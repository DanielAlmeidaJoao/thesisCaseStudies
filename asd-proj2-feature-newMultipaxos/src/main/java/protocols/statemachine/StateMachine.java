package protocols.statemachine;

import appExamples2.appExamples.channels.babelNewChannels.quicChannels.BabelQUIC_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.tcpChannels.BabelTCP_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.udpBabelChannel.BabelUDPChannel;
import lombok.extern.log4j.Log4j2;
import protocols.agreement.MultiPaxos;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.ReadyNotification;
import protocols.agreement.payloads.Membership;
import protocols.agreement.payloads.Proposal;
import protocols.agreement.payloads.TypedPayload;
import protocols.agreement.requests.ElectLeaderRequest;
import protocols.agreement.requests.ProposeRequest;
import protocols.app.HashApp;
import protocols.app.requests.CurrentStateReply;
import protocols.app.requests.CurrentStateRequest;
import protocols.app.requests.InstallStateRequest;
import protocols.statemachine.messages.ForwardToLeaderMessage;
import protocols.statemachine.messages.JoinRequestMessage;
import protocols.statemachine.messages.StateTransferMessage;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.statemachine.notifications.ExecuteNotification;
import protocols.statemachine.notifications.NewLeaderNotification;
import protocols.statemachine.requests.OrderRequest;
import protocols.statemachine.timers.ElectionTimer;
import protocols.statemachine.timers.ReconnectionTimer;
import pt.unl.fct.di.novasys.babel.channels.events.OnConnectionDownEvent;
import pt.unl.fct.di.novasys.babel.channels.events.OnMessageConnectionUpEvent;
import pt.unl.fct.di.novasys.babel.channels.events.OnOpenConnectionFailed;
import pt.unl.fct.di.novasys.babel.core.GenericProtocolExtension;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.client_server.QUICServerEntity;
import tcpSupport.tcpChannelAPI.utils.TCPChannelUtils;
import udpSupport.client_server.NettyUDPServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * This is NOT fully functional StateMachine implementation.
 * This is simply an example of things you can do, and can be used as a starting point.
 * <p>
 * You are free to change/delete anything in this class, including its fields.
 * The only thing that you cannot change are the notifications/requests between the StateMachine and the APPLICATION
 * You can change the requests/notification between the StateMachine and AGREEMENT protocol, however make sure it is
 * coherent with the specification shown in the project description.
 * <p>
 * Do not assume that any logic implemented here is correct, think for yourself!
 */
@Log4j2
public class StateMachine extends GenericProtocolExtension {
    private enum State {JOINING, ACTIVE}

    private final int RECONNECTION_DELAY_MILLIS;
    private final int CONNECTION_TIMEOUT_MILLIS;

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "StateMachine";
    public static final short PROTOCOL_ID = 200;

    private final Host self;     //My own address/port
    private final int channelId; //Id of the created channel
    private final short consensusId;

    private State state;
    private final Set<Host> membership;
    private int nextInstance;

    private final Queue<TypedPayload> pendingConsensus;
    private TypedPayload executingConsensus;
    private final SortedMap<Integer, TypedPayload> pendingExecution;
    private Host leader;
    private final Map<Integer, Host> pendingJoins;
    private final Map<Host, Long> connectionTimeouts;

    public StateMachine(Properties props, short consensusId) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.consensusId = consensusId;
        nextInstance = 0;
        membership = new TreeSet<>();
        pendingConsensus = new LinkedList<>();
        executingConsensus = null;
        pendingExecution = new TreeMap<>();
        pendingJoins = new HashMap<>();
        connectionTimeouts = new HashMap<>();
        RECONNECTION_DELAY_MILLIS = 10; //TODO props
        CONNECTION_TIMEOUT_MILLIS = 50; //TODO props

        String address = props.getProperty("address");
        String port = props.getProperty("p2p_port");

        log.info("Listening on {}:{}", address, port);
        this.self = new Host(InetAddress.getByName(address), Integer.parseInt(port));

        String NETWORK_PROTO  = props.getProperty("NETWORK_PROTOCOL");
        String channelName;
        Properties channelProps;
        if("TCP".equalsIgnoreCase(NETWORK_PROTO)){
            channelProps = TCPChannelUtils.tcpChannelProperties(address,port);
            //channelProps.setProperty("SINGLE_THREADED","ON");
            channelName = BabelTCP_P2P_Channel.CHANNEL_NAME;
        }else if("QUIC".equalsIgnoreCase(NETWORK_PROTO)){
            channelProps = TCPChannelUtils.quicChannelProperty(address,port);
            //channelProps.setProperty("SINGLE_THREADED","ON");
            channelName = BabelQUIC_P2P_Channel.CHANNEL_NAME;
        }else{
            channelProps = TCPChannelUtils.udpChannelProperties(address,port);
            channelProps.setProperty("UPD_MAX_SEND_RETRIES", "100");
            channelProps.setProperty(NettyUDPServer.MAX_UDP_RETRANSMISSION_TIMEOUT,"200");
            //channelProps.setProperty("SINGLE_THREADED","ON");
            channelName = BabelUDPChannel.NAME;
        }
        channelId = createChannel(channelName, channelProps);

        /*--------------------- Register Message Handlers ----------------------------- */
        if (isMultiPaxos()) {
            registerTimerHandler(ElectionTimer.TIMER_ID, this::onElectionTimer);
            subscribeNotification(NewLeaderNotification.NOTIFICATION_ID, this::uponLeaderNotification);
            registerMessageSerializer(channelId, ForwardToLeaderMessage.MSG_ID, ForwardToLeaderMessage.serializer);

            try {
                registerMessageHandler(channelId, ForwardToLeaderMessage.MSG_ID, this::uponForwardToLeaderMessage, this::uponMsgFail);
            } catch (HandlerRegistrationException e) {
                throw new AssertionError("Error registering message handler.", e);
            }
        }

        registerMessageHandler(channelId, StateTransferMessage.MSG_ID, this::uponStateTransferMessage, this::uponMsgFail);
        registerMessageHandler(channelId, JoinRequestMessage.MSG_ID, this::uponJoinMessage, this::uponMsgFail);


        /*-------------------- Register Channel Events ------------------------------- */
        registerChannelEventHandler(channelId, OnOpenConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OnMessageConnectionUpEvent.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, OnConnectionDownEvent.EVENT_ID, this::uponInConnectionDown);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(ReconnectionTimer.TIMER_ID, this::onReconnectionTimer);

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(OrderRequest.REQUEST_ID, this::uponOrderRequest);

        /*--------------------- Register Reply Handlers ----------------------------- */
        registerReplyHandler(CurrentStateReply.REQUEST_ID, this::uponCurrentStateReply);

        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(DecidedNotification.NOTIFICATION_ID, this::uponDecidedNotification);
    }

    private boolean isMultiPaxos() {
        return this.consensusId == MultiPaxos.PROTOCOL_ID;
    }

    @Override
    public void init(Properties props) {
        //Inform the state machine protocol about the channel we created in the constructor
        triggerNotification(new ChannelReadyNotification(channelId, self));

        String host = props.getProperty("initial_membership");
        String[] hosts = host.split(",");
        List<Host> initialMembership = new LinkedList<>();
        for (String s : hosts) {
            String[] hostElements = s.split(":");
            Host h;
            try {
                h = new Host(InetAddress.getByName(hostElements[0]), Integer.parseInt(hostElements[1]));
            } catch (UnknownHostException e) {
                throw new AssertionError("Error parsing initial_membership", e);
            }
            initialMembership.add(h);
        }

        if (initialMembership.contains(self)) {
            state = State.ACTIVE;
            log.info("Starting in ACTIVE as I am part of initial membership");
            //I'm part of the initial members, so I'm assuming the system is bootstrapping
            membership.addAll(initialMembership);
            membership.forEach(this::openMessageConnection);
            triggerNotification(new ReadyNotification(nextInstance, new HashSet<>(membership)));
            if (isMultiPaxos()) {
                setupTimer(new ElectionTimer(), 5000);
            }
        } else {
            state = State.JOINING;
            log.info("Starting in JOINING as I am not part of initial membership");
            //You have to do something to join the system and know which instance you joined
            // (and copy the state of that instance)
            openMessageConnection(initialMembership.get(0));
            sendMessage(new JoinRequestMessage(), initialMembership.get(0));
        }

    }

    /*--------------------------------- Requests ---------------------------------------- */
    private void uponOrderRequest(OrderRequest request, short sourceProto) {
        log.debug("Received request: " + request);
        var payload = new TypedPayload(TypedPayload.Type.PROPOSE, new Proposal(request.getOpId(), request.getOperation()));
        orderRequest(payload);
    }

    private void uponCurrentStateReply(CurrentStateReply msg, short sourceProto) {
        Host replica = pendingJoins.remove(msg.getInstance());
        sendMessage(new StateTransferMessage(msg.getInstance(), msg.getState(), membership), replica);
    }

    private void orderRequest(TypedPayload payload) {
        if (isMultiPaxos()) {
            switch (state) {
                case JOINING -> pendingConsensus.add(payload);
                case ACTIVE -> {
                    //TODO
                    //                pendingConsensus.add(payload);
                    //                if (executingConsensus == null) {
                    //                    requestNextConsensus();
                    //                }
                    sendMessage(channelId, new ForwardToLeaderMessage(payload), leader);
                }
            }
        } else {
            queuePayload(payload);
        }
    }

    private void queuePayload(TypedPayload payload) {
        switch (state) {
            case JOINING -> pendingConsensus.add(payload);
            case ACTIVE -> {
                pendingConsensus.add(payload);
                if (executingConsensus == null) {
                    requestNextConsensus();
                }
            }
        }
    }

    private void uponJoinMessage(JoinRequestMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        var payload = new TypedPayload(TypedPayload.Type.ADD_REPLICA, new Membership(from));
        orderRequest(payload);
    }

    private void uponStateTransferMessage(StateTransferMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        sendRequest(new InstallStateRequest(msg.getState()), HashApp.PROTO_ID);
        nextInstance = msg.getInstance();
        membership.clear();
        membership.addAll(msg.getMembership());
        triggerNotification(new ReadyNotification(nextInstance, new HashSet<>(membership)));
    }

    private void uponForwardToLeaderMessage(ForwardToLeaderMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        log.debug("Received: {}", msg);
        var payload = msg.getForwardedValue();
        queuePayload(payload);
    }

    /*--------------------------------- Notifications ---------------------------------------- */
    private void uponDecidedNotification(DecidedNotification notification, short sourceProto) {
        log.debug("Received notification: " + notification);
        if (notification.getInstance() == nextInstance) {
            processPayload(notification.getPayload());
            for (var pending : pendingExecution.entrySet()) {
                if (pending.getKey() == nextInstance) {
                    processPayload(pending.getValue());
                }
            }
        } else {
            pendingExecution.put(notification.getInstance(), notification.getPayload());
        }
        if (executingConsensus != null) {
            if (!executingConsensus.equals(notification.getPayload()) && (!isMultiPaxos() || self.equals(leader))) {
                sendProposeRequest(executingConsensus);
                return;
            } else {
                executingConsensus = null;
            }
        }
        if (!pendingConsensus.isEmpty() && (!isMultiPaxos() || self.equals(leader))) {
            requestNextConsensus();
        }
    }

    private void processPayload(TypedPayload payload) {
        switch (payload.type()) {
            case ADD_REPLICA -> {
                var newMember = ((Membership) payload.payload()).host();
                membership.add(newMember);
                openMessageConnection(newMember);
                // TODO
                pendingJoins.put(nextInstance, newMember);
                sendRequest(new CurrentStateRequest(nextInstance), HashApp.PROTO_ID);
            }
            case REMOVE_REPLICA -> {
                var oldMember = ((Membership) payload.payload()).host();
                membership.remove(oldMember);
                closeConnection(oldMember);
            }
            case PROPOSE -> {
                var proposal = (Proposal) payload.payload();
                triggerNotification(new ExecuteNotification(proposal.getOpId(), proposal.getOperation()));
            }
        }
        nextInstance++;
        triggerNotification(new ReadyNotification(nextInstance, new HashSet<>(membership)));
    }

    private void requestNextConsensus() {
        executingConsensus = pendingConsensus.poll();
        sendProposeRequest(executingConsensus);
    }

    private void sendProposeRequest(TypedPayload payload) {
        if (!isMultiPaxos() || self.equals(leader)) {
            sendRequest(new ProposeRequest(nextInstance, payload), consensusId);
        } else {
            sendMessage(channelId, new ForwardToLeaderMessage(executingConsensus), leader);
        }
    }

    private void uponLeaderNotification(NewLeaderNotification notification, short sourceProto) {
        if (!notification.getLeader().equals(leader)) {
            leader = notification.getLeader();
            log.info("New leader: {}", leader);
            if (executingConsensus != null) {
                sendProposeRequest(executingConsensus);
            } else if (!pendingConsensus.isEmpty()) {
                requestNextConsensus();
            }
        }
    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        log.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /* --------------------------------- TCPChannel Events ---------------------------- */
    private void onReconnectionTimer(ReconnectionTimer timer, long timerId) {
        openMessageConnection(timer.getHost());
    }

    private void onElectionTimer(ElectionTimer timer, long timerId) {
        sendRequest(new ElectLeaderRequest(), consensusId);
    }

    private void uponOutConnectionUp(OnMessageConnectionUpEvent event, int channelId) {
        if(event.inConnection){
            uponInConnectionUp(event,channelId);
        }else{
            log.info("Connection to {} is up", event.getNode());
            connectionTimeouts.remove(event.getNode());
        }

    }

    private void uponOutConnectionDown(OnConnectionDownEvent event, int channelId) {
        log.warn("Connection to {} is down, cause {}", event.getNode(), event.getCause());
        setupTimer(new ReconnectionTimer(event.getNode()), RECONNECTION_DELAY_MILLIS);
        connectionTimeouts.put(event.getNode(), System.currentTimeMillis() + CONNECTION_TIMEOUT_MILLIS);
    }

    private void uponOutConnectionFailed(OnOpenConnectionFailed<ProtoMessage> event, int channelId) {
        log.warn("{} Connection to {} failed, cause: {}",self,event.getNode(), event.getCause());
        Long timeout = connectionTimeouts.get(event.getNode());
        if (membership.contains(event.getNode()) && (timeout == null || timeout < System.currentTimeMillis())) {
            setupTimer(new ReconnectionTimer(event.getNode()), RECONNECTION_DELAY_MILLIS);
        } else if (timeout != null) {
            connectionTimeouts.remove(event.getNode());
            if (consensusId == MultiPaxos.PROTOCOL_ID && event.getNode().equals(leader)) {
                sendRequest(new ElectLeaderRequest(), consensusId);
            }
            orderRequest(new TypedPayload(TypedPayload.Type.REMOVE_REPLICA, new Membership(event.getNode())));
        }
    }

    private void uponInConnectionUp(OnMessageConnectionUpEvent event, int channelId) {
        log.trace("Connection from {} is up", event.getNode());
    }

    private void uponInConnectionDown(OnConnectionDownEvent event, int channelId) {
        if(event.inConnection){
            log.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
        }else{
            uponOutConnectionDown(event,channelId);
        }
    }

}
