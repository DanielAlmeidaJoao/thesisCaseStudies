package protocols.agreement;

import lombok.*;
import lombok.experimental.NonFinal;
import lombok.extern.log4j.Log4j2;
import protocols.agreement.messages.AcceptMessage;
import protocols.agreement.messages.PrepareMessage;
import protocols.agreement.messages.PromiseMessage;
import protocols.agreement.messages.ProposeMessage;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.ReadyNotification;
import protocols.agreement.payloads.TypedPayload;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.timers.PrepareRetryTimer;
import protocols.agreement.timers.PrepareTimer;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.statemachine.notifications.NewLeaderNotification;
import protocols.agreement.requests.ElectLeaderRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.*;

@Log4j2
public class MultiPaxos extends GenericProtocol {

    //Protocol information, to register in babel
    public final static short PROTOCOL_ID = 700;
    public final static String PROTOCOL_NAME = "MultiPaxos";
    private final int PREPARE_RETRY_INTERVAL_MILLIS;

    private Host self;
    private final SortedMap<Integer, Instance> instances;
    private final SortedMap<Integer, Queue<PendingMessage>> messagesAhead;
    private int currentInstance;
    private final Random random;
    private boolean leader;
    private ProposalID leaderID;

    public record PendingMessage(ProtoMessage msg, Host from) {}

    public MultiPaxos() throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        instances = new TreeMap<>();
        messagesAhead = new TreeMap<>();
        currentInstance = -1;
        random = new Random();
        leader = false;
        leaderID = new ProposalID(-1, null); //TODO justify -1 implies this never blows up on compare
        PREPARE_RETRY_INTERVAL_MILLIS = 200;
        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(PrepareTimer.TIMER_ID, this::uponPrepareTimer);
        registerTimerHandler(PrepareRetryTimer.TIMER_ID, this::uponPrepareRetryTimer);

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);
        registerRequestHandler(ElectLeaderRequest.REQUEST_ID, this::uponElectLeaderRequest);

        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(ReadyNotification.NOTIFICATION_ID, this::uponReadyNotification);
    }

    @Override
    public void init(Properties props) {
        //Nothing to do here, we just wait for events from the application or agreement
    }

    //Upon receiving the channelId from the membership, register our own callbacks and serializers
    private void uponChannelCreated(ChannelReadyNotification notification, short sourceProto) {
        int cId = notification.getChannelId();
        self = notification.getMyself();
        log.info("Channel {} created, I am {}", cId, self);
        // Allows this protocol to receive events from this channel.
        registerSharedChannel(cId);
        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(cId, PrepareMessage.MSG_ID, PrepareMessage.serializer);
        registerMessageSerializer(cId, PromiseMessage.MSG_ID, PromiseMessage.serializer);
        registerMessageSerializer(cId, ProposeMessage.MSG_ID, ProposeMessage.serializer);
        registerMessageSerializer(cId, AcceptMessage.MSG_ID, AcceptMessage.serializer);
        /*---------------------- Register Message Handlers -------------------------- */
        try {
            registerMessageHandler(cId, PrepareMessage.MSG_ID, this::uponPrepareMessage, this::uponMsgFail);
            registerMessageHandler(cId, PromiseMessage.MSG_ID, this::uponPromiseMessage, this::uponMsgFail);
            registerMessageHandler(cId, ProposeMessage.MSG_ID, this::uponProposeMessage, this::uponMsgFail);
            registerMessageHandler(cId, AcceptMessage.MSG_ID, this::uponAcceptMessage, this::uponMsgFail);
        } catch (HandlerRegistrationException e) {
            throw new AssertionError("Error registering message handler.", e);
        }
    }

    private void uponPrepareTimer(PrepareTimer timer, long timerId) {
        Instance instance = instances.get(timer.getInstance());
        instance.getProposer().prepare();
        instance.setPrepareRetryTimer(setupTimer(new PrepareRetryTimer(timer.getInstance()), PREPARE_RETRY_INTERVAL_MILLIS));
    }

    private void uponPrepareRetryTimer(PrepareRetryTimer timer, long timerId) {
        Instance instance = instances.get(timer.getInstance());
        if (instance == null || instance.getLearner().isComplete()) {
            //TODO babel is not cancelling timers
            log.trace("This timer should have been cancelled {}", timer.getInstance());
            return;
        }
        instance.getProposer().retryPrepare();
        instance.setPrepareRetryTimer(setupTimer(new PrepareRetryTimer(timer.getInstance()), PREPARE_RETRY_INTERVAL_MILLIS));
    }

    private void uponProposeRequest(ProposeRequest request, short sourceProto) {
        log.debug("Received {}", request);
        if (leader) {
            var instance = instances.get(currentInstance);
            instance.getMessenger().sendPropose(leaderID, request.getPayload());
        } else if (self.equals(leaderID.getHost())) {
            //TODO async <3
            log.error("well, gg {}", leaderID);
        } else {
            //TODO ideally state machine would never send this but async :)))
            log.error("I'm not leader {}", request);
        }
    }

    private void uponElectLeaderRequest(ElectLeaderRequest request, short sourceProto) {
        var instance = instances.get(currentInstance);
        long millis = random.nextLong(10); //TODO
        instance.setPrepareTimer(setupTimer(new PrepareTimer(instance.getInstance()), millis));
    }

    private void uponReadyNotification(ReadyNotification notification, short sourceProto) {
        currentInstance = notification.getReadyInstance();
        Set<Host> membership = notification.getMembership();
        instances.put(currentInstance, new Instance(currentInstance, membership));
        log.debug("MultiPaxos starting at instance {},  membership: {}", currentInstance, membership);
        var pendingMessages = messagesAhead.remove(currentInstance);
        if (pendingMessages != null) {
            for (var pendingMessage : pendingMessages) {
                ProtoMessage protoMessage = pendingMessage.msg();
                Host from = pendingMessage.from();
                processPendingMessage(protoMessage,from);
            }
        }
    }

    private void processPendingMessage(ProtoMessage msg, Host from) {
        if (msg instanceof AcceptMessage acceptMessage) {
            uponAcceptMessage(acceptMessage, from, (short) -1, -1,null);
        } else if (msg instanceof PrepareMessage prepareMessage) {
            uponPrepareMessage(prepareMessage, from, (short) -1, -1,null);
        } else if (msg instanceof ProposeMessage proposeMessage) {
            uponProposeMessage(proposeMessage, from, (short) -1, -1,null);
        } else if (msg instanceof PromiseMessage promiseMessage) {
            uponPromiseMessage(promiseMessage, from, (short) -1, -1,null);
        } else {
            throw new AssertionError("Unexpected Message" + msg);
        }
    }

    private void uponPrepareMessage(PrepareMessage msg, Host from, short sourceProto, int channelId,String conID) {
        log.debug("Received {} from {}", msg, from);
        int instance = msg.getInstance();
        if (instance > currentInstance) {
            // TODO we won't elect someone who's delayed
            return;
        }
        MultiPaxos.Instance instance1 = instances.get(instance);
        if(instance1!=null){
            instance1.getAcceptor()
                    .receivePrepare(from, msg.getProposalID());
        }
    }

    private void uponPromiseMessage(PromiseMessage msg, Host from, short sourceProto, int channelId,String conID) {
        log.debug("Received {} from {}", msg, from);
        int instance = msg.getInstance();
        if (instance < currentInstance) {
            log.debug("they're delayed proposer {} {}", instance, currentInstance);
            return; //TODO maybe send NACK
        }
        if (instance > currentInstance) {
            messagesAhead.computeIfAbsent(instance, i -> new LinkedList<>())
                    .add(new PendingMessage(msg, from));
            return;
        }
        MultiPaxos.Instance instance1 = instances.get(instance);
        if(instance1!=null){
            instance1 .getProposer()
                    .receivePromise(from, msg.getProposalID(), msg.getPreviousID(), msg.getAcceptedValue());
        }

    }

    private void uponProposeMessage(ProposeMessage msg, Host from, short sourceProto, int channelId, String conID) {
        log.debug("Received {} from {}", msg, from);
        int instance = msg.getInstance();
        if (instance < currentInstance) {
            log.debug("they're delayed acceptor {} {}", instance, currentInstance);
            //TODO maybe send NACK
        }
        if (instance > currentInstance) {
            messagesAhead.computeIfAbsent(instance, i -> new LinkedList<>())
                    .add(new PendingMessage(msg, from));
            return;
        }
        MultiPaxos.Instance instance1 = instances.get(instance);
        if(instance1!=null){
            instance1.getAcceptor()
                    .receivePropose(from, msg.getProposalID(), msg.getProposalValue());
        }

    }

    private void uponAcceptMessage(AcceptMessage msg, Host from, short sourceProto, int channelId, String conID) {
        log.debug("Received {} from {}", msg, from);
        int instance = msg.getInstance();
        if (instance < currentInstance) {
            log.debug("they're delayed learner {} {}", instance, currentInstance);
            //TODO maybe send NACK
        }
        if (instance > currentInstance) {
            messagesAhead.computeIfAbsent(instance, i -> new LinkedList<>())
                    .add(new PendingMessage(msg, from));
            return;
        }
        MultiPaxos.Instance instance1 = instances.get(instance);
        if(instance1!=null){
            instance1.getLearner().receiveAccept(from, msg.getProposalID(), msg.getAcceptedValue());
        }
    }

    @Value
    class Instance {
        int instance;
        Proposer proposer;
        Acceptor acceptor;
        Learner learner;
        Messenger messenger;
        @Setter
        @NonFinal
        long prepareTimer;
        @Setter
        @NonFinal
        long prepareRetryTimer;

        Instance(int instance, Set<Host> membership) {
            this.instance = instance;
            this.messenger = new Messenger(membership);
            this.proposer = new Proposer();
            this.acceptor = new Acceptor();
            this.learner = new Learner();
        }

        @Getter
        class Proposer {
            protected ProposalID proposalID;
            protected TypedPayload proposedValue = null;
            protected ProposalID lastAcceptedID = null;
            @Getter(AccessLevel.NONE)
            protected Set<Host> promisesReceived = new HashSet<>();

            public Proposer() {
                this.proposalID = new ProposalID(0, self);
            }

            public void prepare() {
                leader = false;
                promisesReceived.clear();
                proposalID.increment();
                messenger.sendPrepare(proposalID);
            }

            public void receivePromise(Host from, ProposalID proposalID,
                                       ProposalID prevAcceptedID, TypedPayload prevAcceptedValue) {

                cancelTimer(prepareTimer);
                if (leaderID.isGreaterThan(proposalID))
                    return;

                promisesReceived.add(from);

                if (prevAcceptedID != null && (lastAcceptedID == null || prevAcceptedID.isGreaterThan(lastAcceptedID))) {
                    lastAcceptedID = prevAcceptedID;

                    if (prevAcceptedValue != null)
                        proposedValue = prevAcceptedValue;
                }

                if (promisesReceived.size() == messenger.getQuorumSize()) {
                    leader = true;
                    leaderID = proposalID;
                    this.proposalID = proposalID;
                    triggerNotification(new NewLeaderNotification(leaderID.getHost()));
//                    messenger.sendPropose(leaderID, proposedValue);
                    cancelTimer(prepareRetryTimer);
                }
            }

            public void retryPrepare() {
                if (proposalID.isGreaterThan(leaderID)) {
                    leader = false;
                    promisesReceived.clear();
                    messenger.sendPrepare(proposalID);
                }
            }
        }

        @Getter
        @NoArgsConstructor
        class Acceptor {
            protected ProposalID acceptedID;
            protected TypedPayload acceptedValue;

            public void receivePrepare(Host from, ProposalID proposalID) {
                if (proposalID.isGreaterThan(leaderID)) {
                    leaderID = proposalID;
                    leader = false;
                    triggerNotification(new NewLeaderNotification(leaderID.getHost()));
                    messenger.sendPromise(from, proposalID, acceptedID, acceptedValue);
                }
            }

            public void receivePropose(Host from, ProposalID proposalID, TypedPayload value) {
                if (proposalID.isLessThan(leaderID)) {
                    return;
                }

                if (proposalID.isGreaterThan(leaderID)) {
                    leaderID = proposalID;
                    leader = false;
                    triggerNotification(new NewLeaderNotification(leaderID.getHost()));
                }

                acceptedID = proposalID;
                acceptedValue = value;
                //TODO should i have this?
                messenger.sendAccept(acceptedID, acceptedValue);

            }
        }

        class Learner {

            @AllArgsConstructor
            class Proposal {
                int acceptCount;
                TypedPayload value;
            }

            private final Map<ProposalID, Proposal> proposals;
            private final Map<Host, ProposalID> acceptors;
            private TypedPayload finalValue;

            public Learner() {
                proposals = new HashMap<>();
                acceptors = new HashMap<>();
                finalValue = null;
            }

            public boolean isComplete() {
                return finalValue != null;
            }

            public void receiveAccept(Host from, ProposalID proposalID, TypedPayload acceptedValue) {

                if (isComplete() && proposalID.isLessThan(leaderID))
                    return;

                ProposalID oldPID = acceptors.get(from);

                if (oldPID != null && !proposalID.isGreaterThan(oldPID))
                    return;

                acceptors.put(from, proposalID);

                Proposal thisProposal = proposals.computeIfAbsent(proposalID, p -> new Proposal(0, acceptedValue));

                thisProposal.acceptCount++;

                if (thisProposal.acceptCount == messenger.getQuorumSize()) {
                    finalValue = acceptedValue;
                    proposals.clear();
                    acceptors.clear();

                    messenger.onConsensus(proposalID, acceptedValue);
                }
            }

        }

        @AllArgsConstructor
        private class Messenger {
            private final Set<Host> membership;

            public int getQuorumSize() {
                return membership.size() / 2 + 1;
            }

            public void sendPrepare(ProposalID proposalID) {
                var msg = new PrepareMessage(instance, proposalID);
                for (Host acceptor : membership) {
                    sendMessage(msg, acceptor);
                }
                log.debug("Sent {} to acceptors {}", msg, membership);
            }

            public void sendPromise(Host proposer, ProposalID proposalID, ProposalID previousID, TypedPayload acceptedValue) {
                var msg = new PromiseMessage(instance, proposalID, previousID, acceptedValue);
                sendMessage(msg, proposer);
                log.debug("Sent {} to proposer {}", msg, proposer);
            }

            public void sendPropose(ProposalID proposalID, TypedPayload proposalValue) {
                var msg = new ProposeMessage(instance, proposalID, proposalValue);
                for (Host acceptor : membership) {
                    sendMessage(msg, acceptor);
                }
                log.debug("Sent {} to acceptors {}", msg, membership);
            }

            public void sendAccept(ProposalID proposalID, TypedPayload acceptedValue) {
                var msg = new AcceptMessage(instance, proposalID, acceptedValue);
                for (Host learner : membership) {
                    sendMessage(msg, learner);
                }
                log.debug("Sent {} to learners {}", msg, membership);
            }

            public void onConsensus(ProposalID proposalID, TypedPayload value) {
                if (instances.size() > 200) {
                    instances.remove(instance - 200);//TODO TERRIBLE
                }
                triggerNotification(new DecidedNotification(instance, value));
            }
        }

    }

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        log.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }
}
