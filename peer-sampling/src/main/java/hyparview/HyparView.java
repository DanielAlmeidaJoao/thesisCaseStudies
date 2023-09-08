package hyparview;

import appExamples2.appExamples.channels.FactoryMethods;
import appExamples2.appExamples.channels.babelNewChannels.quicChannels.BabelQUIC_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.tcpChannels.BabelTCP_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.udpBabelChannel.BabelUDPChannel;
import fileStreamingProto.HyparViewInterface;
import hyparview.messages.*;
import hyparview.notifications.NeighDown;
import hyparview.notifications.NeighUp;
import hyparview.timers.HelloTimeout;
import hyparview.timers.ShuffleTimer;
import hyparview.utils.IView;
import hyparview.utils.View;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.unl.fct.di.novasys.babel.channels.events.OnConnectionDownEvent;
import pt.unl.fct.di.novasys.babel.channels.events.OnMessageConnectionUpEvent;
import pt.unl.fct.di.novasys.babel.channels.events.OnOpenConnectionFailed;
import pt.unl.fct.di.novasys.babel.core.GenericProtocolExtension;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.utils.enums.NetworkProtocol;
import tcpSupport.tcpChannelAPI.utils.TCPChannelUtils;
import udpSupport.client_server.NettyUDPServer;
import udpSupport.metrics.UDPNetworkStatsWrapper;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;


public class HyparView extends GenericProtocolExtension implements HyparViewInterface {

    private static final Logger logger = LogManager.getLogger(HyparView.class);

    public final static short PROTOCOL_ID = 400;
    public final static String PROTOCOL_NAME = "HyParView";
    private static final int MAX_BACKOFF = 60000;

    private final short ARWL; //param: active random walk length
    private final short PRWL; //param: passive random walk length

    private final short shuffleTime; //param: timeout for shuffle
    private final short originalTimeout; //param: timeout for hello msgs
    private short timeout;

    private final short kActive; //param: number of active nodes to exchange on shuffle
    private final short kPassive; //param: number of passive nodes to exchange on shuffle


    protected int channelId;
    protected final Host myself;

    protected IView active;
    protected IView passive;

    protected Set<Host> pending;
    private final Map<Short, Host[]> activeShuffles;

    private short seqNum = 0;

    protected final Random rnd;
    public String NETWORK_PROTO;

    public HyparView(String channelName, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;

        int maxActive = Integer.parseInt(properties.getProperty("ActiveView", "4")); //param: maximum active nodes (degree of random overlay)
        int maxPassive = Integer.parseInt(properties.getProperty("PassiveView", "7")); //param: maximum passive nodes
        this.ARWL = Short.parseShort(properties.getProperty("ARWL", "4")); //param: active random walk length
        this.PRWL = Short.parseShort(properties.getProperty("PRWL", "2")); //param: passive random walk length

        this.shuffleTime = Short.parseShort(properties.getProperty("shuffleTime", "2000")); //param: timeout for shuffle
        this.timeout = this.originalTimeout = Short.parseShort(properties.getProperty("helloBackoff", "1000")); //param: timeout for hello msgs

        this.kActive = Short.parseShort(properties.getProperty("kActive", "2")); //param: number of active nodes to exchange on shuffle
        this.kPassive = Short.parseShort(properties.getProperty("kPassive", "3")); //param: number of passive nodes to exchange on shuffle

        this.rnd = new Random();
        this.active = new View(maxActive, myself, rnd);
        this.passive = new View(maxPassive, myself, rnd);

        this.pending = new HashSet<>();
        this.activeShuffles = new TreeMap<>();

        this.active.setOther(passive, pending);
        this.passive.setOther(active, pending);

        String address = properties.getProperty("address");
        String port = properties.getProperty("port");
        NETWORK_PROTO  = properties.getProperty("NETWORK_PROTO");

        Properties channelProps;
        if("TCP".equalsIgnoreCase(NETWORK_PROTO)){
            channelProps = TCPChannelUtils.tcpChannelProperties(address,port);
            //channelProps.setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
            channelName = BabelTCP_P2P_Channel.CHANNEL_NAME;
            //channelProps.setProperty(FactoryMethods.CLIENT_THREADS,"1");
            //channelProps.setProperty(FactoryMethods.SINGLE_THREADED_PROP,"TRUE");
        }else if("QUIC".equalsIgnoreCase(NETWORK_PROTO)){
            channelProps = TCPChannelUtils.quicChannelProperty(address,port);
            //channelProps.setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
            //channelProps.setProperty(FactoryMethods.CLIENT_THREADS,"1");
            channelName = BabelQUIC_P2P_Channel.CHANNEL_NAME;
        }else{
            channelProps = TCPChannelUtils.udpChannelProperties(address,port);
            //channelProps.setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
            channelProps.setProperty(NettyUDPServer.MIN_UDP_RETRANSMISSION_TIMEOUT,"250");
            channelProps.setProperty(NettyUDPServer.MAX_UDP_RETRANSMISSION_TIMEOUT,"1000");
            channelProps.setProperty(udpSupport.client_server.NettyUDPServer.MAX_SEND_RETRIES_KEY,"100");
            //channelProps.setProperty(TCPChannelUtils.CHANNEL_METRICS,"ON");
            channelName = BabelUDPChannel.NAME;
        }

        //channelProps.remove("AUTO_CONNECT");

        channelId = createChannel(channelName, channelProps);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, JoinMessage.MSG_CODE, JoinMessage.serializer);
        registerMessageSerializer(channelId, JoinReplyMessage.MSG_CODE, JoinReplyMessage.serializer);
        registerMessageSerializer(channelId, ForwardJoinMessage.MSG_CODE, ForwardJoinMessage.serializer);
        registerMessageSerializer(channelId, HelloMessage.MSG_CODE, HelloMessage.serializer);
        registerMessageSerializer(channelId, HelloReplyMessage.MSG_CODE, HelloReplyMessage.serializer);
        registerMessageSerializer(channelId, DisconnectMessage.MSG_CODE, DisconnectMessage.serializer);
        registerMessageSerializer(channelId, ShuffleMessage.MSG_CODE, ShuffleMessage.serializer);
        registerMessageSerializer(channelId, ShuffleReplyMessage.MSG_CODE, ShuffleReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, JoinMessage.MSG_CODE, this::uponReceiveJoin);
        registerMessageHandler(channelId, JoinReplyMessage.MSG_CODE, this::uponReceiveJoinReply);
        registerMessageHandler(channelId, ForwardJoinMessage.MSG_CODE, this::uponReceiveForwardJoin);
        registerMessageHandler(channelId, HelloMessage.MSG_CODE, this::uponReceiveHello);
        registerMessageHandler(channelId, HelloReplyMessage.MSG_CODE, this::uponReceiveHelloReply);
        registerMessageHandler(channelId, DisconnectMessage.MSG_CODE, this::uponReceiveDisconnect, this::uponDisconnectSent);
        registerMessageHandler(channelId, ShuffleMessage.MSG_CODE, this::uponReceiveShuffle);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_CODE, this::uponReceiveShuffleReply, this::uponShuffleReplySent);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(ShuffleTimer.TimerCode, this::uponShuffleTime);
        registerTimerHandler(HelloTimeout.TimerCode, this::uponHelloTimeout);

        /*-------------------- Register Channel Event ------------------------------- */
        registerChannelEventHandler(channelId, OnConnectionDownEvent.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OnOpenConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OnMessageConnectionUpEvent.EVENT_ID, this::uponOnMessageConnectionUpEvent);
        //registerChannelEventHandler(channelId, OnConnectionDownEvent.EVENT_ID, this::uponOnConnectionDownEvent);
        //registerChannelEventHandler(channelId, OnMessageConnectionUpEvent.EVENT_ID, this::uponInConnectionDown);

    } /*--------------------------------- Messages ---------------------------------------- */

    public int getChannel() {
        return channelId;
    }

    protected void handleDropFromActive(Host dropped) {
        if(dropped != null) {
            triggerNotification(new NeighDown(dropped));
            sendMessage(new DisconnectMessage(), dropped);
            logger.debug("Sent DisconnectMessage to {}", dropped);
            passive.addPeer(dropped);
            logger.trace("Added to {} passive{}", dropped, passive);
        }
    }

    private void uponReceiveJoin(JoinMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        Host h = active.addPeer(from);
        logger.trace("Added to {} active{}", from, active);
        openMessageConnection(from);
        triggerNotification(new NeighUp(from));
        sendMessage( new JoinReplyMessage(), from);
        logger.debug("Sent JoinReplyMessage to {}", from);
        handleDropFromActive(h);

        for(Host peer : active.getPeers()) {
            if(!peer.equals(from)) {
                sendMessage(new ForwardJoinMessage(ARWL, from), peer);
                logger.debug("Sent ForwardJoinMessage to {}", peer);
            }

        }
    }

    private void uponReceiveJoinReply(JoinReplyMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        replyNotReceived=false;
        if(!active.containsPeer(from)) {
            passive.removePeer(from);
            pending.remove(from);


            Host h = active.addPeer(from);
            openMessageConnection(from);
            logger.trace("Added to {} active{}", from, active);
            triggerNotification(new NeighUp(from));
            handleDropFromActive(h);
        }
    }

    private void uponReceiveForwardJoin(ForwardJoinMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        if(msg.decrementTtl() == 0 || active.getPeers().size() == 1) {
            if(!msg.getNewHost().equals(myself) && !active.containsPeer(msg.getNewHost())) {
                passive.removePeer(msg.getNewHost());
                pending.remove(msg.getNewHost());

                Host h = active.addPeer(msg.getNewHost());
                logger.trace("Added to {} active{}", msg.getNewHost(), active);
                openMessageConnection(msg.getNewHost());
                triggerNotification(new NeighUp(msg.getNewHost()));
                sendMessage(new JoinReplyMessage(), msg.getNewHost());
                logger.debug("Sent JoinReplyMessage to {}", msg.getNewHost());
                handleDropFromActive(h);
            }
        } else {
            if(msg.getTtl() == PRWL)  {
                passive.addPeer(msg.getNewHost());
                logger.trace("Added to {} passive{}", from, passive);
            }
            Host next = active.getRandomDiff(from);
            if(next != null) {
                sendMessage(msg, next);
                logger.debug("Sent ForwardJoinMessage to {}", next);
            }
        }
    }

    private void uponReceiveHello(HelloMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        if(msg.isPriority()) {
            if(!active.containsPeer(from)) {
                pending.remove(from);
                logger.trace("Removed from {} pending{}", from, pending);
                passive.removePeer(from);
                logger.trace("Removed from {} passive{}", from, passive);
                Host h = active.addPeer(from);
                logger.trace("Added to {} active{}", from, active);
                openMessageConnection(from);
                triggerNotification(new NeighUp(from));
                handleDropFromActive(h);
            }
            sendMessage(new HelloReplyMessage(true), from);
            logger.debug("Sent HelloReplyMessage to {}", from);

        } else {
            pending.remove(from);
            logger.trace("Removed from {} pending{}", from, pending);
            if(!active.fullWithPending(pending) || active.containsPeer(from)) {
                if(!active.containsPeer(from)) {
                    passive.removePeer(from);
                    logger.trace("Removed from {} passive{}", from, passive);
                    active.addPeer(from);
                    logger.trace("Added to {} active{}", from, active);
                    openMessageConnection(from);
                    triggerNotification(new NeighUp(from));
                }
                sendMessage(new HelloReplyMessage(true), from);
                logger.debug("Sent HelloReplyMessage to {}", from);
            } else {
                sendMessage(new HelloReplyMessage(false), from);
                logger.debug("Sent HelloReplyMessage to {}", from);
            }
        }
    }

    private void uponReceiveHelloReply(HelloReplyMessage msg, Host from, short sourceProto, int channelId, String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        pending.remove(from);
        logger.trace("Removed from {} pending{}", from, pending);
        if(msg.isTrue()) {
            if(!active.containsPeer(from)) {
                timeout = originalTimeout;
                Host h = active.addPeer(from);
                logger.trace("Added to {} active{}", from, active);
                openMessageConnection(from);
                triggerNotification(new NeighUp(from));
                handleDropFromActive(h);
            }
        } else if(!active.containsPeer(from)){
            passive.addPeer(from);
            closeConnection(from);
            logger.trace("Added to {} passive{}", from, passive);
            if(!active.fullWithPending(pending)) {
                setupTimer(new HelloTimeout(), timeout);
            }
        }
    }

    protected void uponReceiveDisconnect(DisconnectMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        if(active.containsPeer(from)) {
            active.removePeer(from);
            logger.trace("Removed from {} active{}", from, active);
            handleDropFromActive(from);

            if(active.getPeers().isEmpty()) {
                timeout = originalTimeout;
            }

            if(!active.fullWithPending(pending)){
                setupTimer(new HelloTimeout(), timeout);
            }
        }
    }

    private void uponDisconnectSent(DisconnectMessage msg, Host host, short destProto, int channelId) {
        logger.trace("Sent {} to {}", msg, host);
        closeConnection(host);
    }

    private void uponReceiveShuffle(ShuffleMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        if(msg.decrementTtl() > 0 && active.getPeers().size() > 1) {
            Host next = active.getRandomDiff(from);
            sendMessage(msg, next);
            logger.debug("Sent ShuffleMessage to {}", next);
        } else if(!msg.getOrigin().equals(myself)) {
            logger.trace("Processing {}, passive{}", msg, passive);
            Set<Host> peers = new HashSet<>();
            peers.addAll(active.getRandomSample(msg.getFullSample().size()));
            Host[] hosts = peers.toArray(new Host[0]);
            int i = 0;
            for (Host host : msg.getFullSample()) {
                if (!host.equals(myself) && !active.containsPeer(host) && passive.isFull() && i < peers.size()) {
                    passive.removePeer(hosts[i]);
                    i++;
                }
                passive.addPeer(host);
            }
            logger.trace("After Passive{}", passive);
            if(!active.containsPeer(msg.getOrigin()) && !pending.contains(msg.getOrigin()))
                openMessageConnection(msg.getOrigin());
            sendMessage(new ShuffleReplyMessage(peers, msg.getSeqnum()), msg.getOrigin());
            logger.debug("Sent ShuffleReplyMessage to {}", msg.getOrigin());
        } else
            activeShuffles.remove(msg.getSeqnum());
    }

    private void uponShuffleReplySent(ShuffleReplyMessage msg, Host host, short destProto, int channelId) {
        if(!active.containsPeer(host) && !pending.contains(host)) {
            logger.trace("Disconnecting from {} after shuffleReply", host);
            closeConnection(host);
        }
    }

    private void uponReceiveShuffleReply(ShuffleReplyMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.debug("Received {} from {}", msg, from);
        Host[] sent = activeShuffles.remove(msg.getSeqnum());
        List<Host> sample = msg.getSample();
        sample.add(from);
        int i = 0;
        logger.trace("Processing {}, passive{}", msg, passive);
        for (Host h : sample) {
            try {
                if(!h.equals(myself) && !active.containsPeer(h) && passive.isFull() && i < sent.length) {
                    passive.removePeer(sent[i]);
                    i ++;
                }
            } catch (Exception e) {
                // TODO: handle exception
            }
            passive.addPeer(h);
        }
        logger.trace("After Passive{}", passive);
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponShuffleTime(ShuffleTimer timer, long timerId) {
        if(!active.fullWithPending(pending)){
            setupTimer(new HelloTimeout(), timeout);
        }

        Host h = active.getRandom();
        if(h != null) {
            Set<Host> peers = new HashSet<>();
            peers.addAll(active.getRandomSample(kActive));
            peers.addAll(passive.getRandomSample(kPassive));
            activeShuffles.put(seqNum, peers.toArray(new Host[0]));
            sendMessage(new ShuffleMessage(myself, peers, PRWL, seqNum), h);
            logger.debug("Sent ShuffleMessage to {}", h);
            seqNum = (short) ((short) (seqNum % Short.MAX_VALUE) + 1);
        }
    }

    private void uponHelloTimeout(HelloTimeout timer, long timerId) {
        if(!active.fullWithPending(pending)){
            Host h = passive.dropRandom();
            if(h != null && pending.add(h)) {
                logger.trace("Sending HelloMessage to {}, pending {}, active {}, passive {}", h, pending, active, passive);
                openMessageConnection(h);
                sendMessage(new HelloMessage(getPriority()), h);
                logger.debug("Sent HelloMessage to {}", h);
                timeout = (short) (Math.min(timeout * 2, MAX_BACKOFF));
            } else if(h != null)
                passive.addPeer(h);
        }
    }

    private boolean getPriority() {
        return active.getPeers().size() + pending.size() == 1;
    }

    /* --------------------------------- Channel Events ---------------------------- */

    private void uponOutConnectionDown(OnConnectionDownEvent event, int channelId) {
        logger.trace("Host {} is down, active{}, cause: {}", event.getNode(), active, event.getCause());
        if(active.removePeer(event.getNode())) {
            triggerNotification(new NeighDown(event.getNode()));
            if(!active.fullWithPending(pending)){
                setupTimer(new HelloTimeout(), timeout);
            }
        } else
            pending.remove(event.getNode());
    }

    private void uponOutConnectionFailed(OnOpenConnectionFailed event, int channelId) {
        logger.trace("Connection to host {} failed, cause: {}", event.getNode(), event.getCause());
        if(active.removePeer(event.getNode())) {
            triggerNotification(new NeighDown(event.getNode()));
            if(!active.fullWithPending(pending)){
                setupTimer(new HelloTimeout(), timeout);
            }
        } else
            pending.remove(event.getNode());
    }

    private void uponOnMessageConnectionUpEvent(OnMessageConnectionUpEvent event, int channelId) {
        logger.trace("Host (out) {} is up", event.getNode());
    }

    private void uponOnConnectionDownEvent(OnConnectionDownEvent event, int channelId) {
        logger.trace("Host (in) {} is up", event.getNode());
    }

    private void uponInConnectionDown(OnConnectionDownEvent event, int channelId) {
        logger.trace("Connection from host {} is down, active{}, cause: {}", event.getNode(), active, event.getCause());
    }
    boolean replyNotReceived = true;
    @Override
    public void init(Properties props){
        if (props.containsKey("contact")) {
            try {
                String contact = props.getProperty("contact");

                String[] hostElems = contact.split(":");
                Host c = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));

                openMessageConnection(c);
                JoinMessage m = new JoinMessage();
                sendMessage(m,  c);
                logger.debug("Sent JoinMessage to {}",c);
                logger.trace("Sent " + m + " to " + c);
                /**
                new Thread(() -> {
                    int cc = 0;
                    while(replyNotReceived){
                        sendMessage(m,  c);
                        cc++;
                        try {
                            Thread.sleep(10000);
                        } catch (Exception e) {
                            // TODO: handle exception
                        }
                    }
                }).start();
                **/
            } catch (Exception e) {
                System.err.println("Invalid contact on configuration: '" + props.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }

        setupPeriodicTimer(new ShuffleTimer(), this.shuffleTime, this.shuffleTime);
    }

    public int connectedPeers(){
        return super.numConnectedPeers(channelId);
    }
    public NetworkProtocol getChannelProtocol(){
        return super.getNetworkProtocol(channelId);
    }
    public void metrics(){
        List<UDPNetworkStatsWrapper> list = super.getUDPMetrics(channelId);
        for (UDPNetworkStatsWrapper udpNetworkStatsWrapper : list) {
            /**logger.info("EFFECTIVE SENT AND ACKED: {} {}",udpNetworkStatsWrapper.sentAckedMessageStats.getMessagesSent(),
                    udpNetworkStatsWrapper.sentAckedMessageStats.getMessagesAcked());**/
            System.out.println(udpNetworkStatsWrapper.sentAckedMessageStats.getMessagesSent()+" - "+
            udpNetworkStatsWrapper.sentAckedMessageStats.getMessagesAcked());
        }
    }
}