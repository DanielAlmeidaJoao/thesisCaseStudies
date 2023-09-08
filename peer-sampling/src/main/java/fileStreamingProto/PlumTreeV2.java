package fileStreamingProto;

import dissemination.messages.GossipMessage;
import dissemination.plumtree.messages.GraftMessage;
import dissemination.plumtree.messages.IHaveMessage;
import dissemination.plumtree.messages.PruneMessage;
import dissemination.plumtree.timers.IHaveTimeout;
import dissemination.plumtree.utils.AddressedIHaveMessage;
import dissemination.plumtree.utils.HashProducer;
import dissemination.plumtree.utils.LazyQueuePolicy;
import dissemination.plumtree.utils.MessageSource;
import dissemination.requests.BroadcastRequest;
import dissemination.requests.DeliverReply;
import hyparview.notifications.NeighDown;
import hyparview.notifications.NeighUp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.channels.events.OnStreamConnectionUpEvent;
import pt.unl.fct.di.novasys.babel.channels.events.OnStreamDataSentEvent;
import pt.unl.fct.di.novasys.babel.core.GenericProtocolExtension;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.internal.BabelStreamDeliveryEvent;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PlumTreeV2 extends GenericProtocolExtension implements PlumTreeInterface{

    private static final Logger logger = LogManager.getLogger(PlumTreeV2.class);

    public final static short PROTOCOL_ID = 327;
    public final static String PROTOCOL_NAME = "PlumTreeV2";

    private final int space;

    private final long timeout1;
    private final long timeout2;

    private final Host myself;

    private final Set<Host> eager;
    private final Set<Host> lazy;

    private final Map<Integer, Queue<MessageSource>> missing;
    private final Map<Integer, GossipMessage> received;

    private final Queue<Integer> stored;

    private final Map<Integer, Long> onGoingTimers;
    private final Queue<AddressedIHaveMessage> lazyQueue;

    private final LazyQueuePolicy policy;

    private final HashProducer hashProducer;
    private final int channelID;

    private boolean IHAVE_THE_FILE;
    String filePath;

    public PlumTreeV2(String channelName, int channelId, Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;
        this.hashProducer = new HashProducer(myself);
        this.channelID = channelId;

        this.space = Integer.parseInt(properties.getProperty("space", "6000"));
        this.stored = new LinkedList<>();

        this.eager = new HashSet<>();
        this.lazy = new HashSet<>();

        this.missing = new HashMap<>();
        this.received = new HashMap<>();
        this.onGoingTimers = new HashMap<>();
        this.lazyQueue = new LinkedList<>();

        this.policy = HashSet::new;
        filePath = properties.getProperty("FILE_PATH");

        this.timeout1 = Long.parseLong(properties.getProperty("timeout1", "1000"));
        this.timeout2 = Long.parseLong(properties.getProperty("timeout2", "500"));

        /**
        String address = properties.getProperty("address");
        String port = myself.getPort()+"";
        String NETWORK_PROTO  = properties.getProperty("NETWORK_PROTO");

        Properties channelProps;
        if("TCP".equalsIgnoreCase(NETWORK_PROTO)){
            channelProps = TCPStreamUtils.tcpChannelProperties(address,port);
            channelName = BabelQUIC_TCP_Channel.NAME_TCP;
        }else if("QUIC".equalsIgnoreCase(NETWORK_PROTO)){
            channelProps = TCPStreamUtils.quicChannelProperty(address,port);
            channelName = BabelQUIC_TCP_Channel.NAME_QUIC;
        }else{
            channelProps = TCPStreamUtils.udpChannelProperties(address,port);
            channelName = BabelUDPChannel.NAME;
        }

        int channelId = createChannel(channelName, channelProps);**/

        registerSharedChannel(channelId);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId,GossipMessage.MSG_ID, GossipMessage.serializer);
        registerMessageSerializer(channelId, PruneMessage.MSG_ID, PruneMessage.serializer);
        registerMessageSerializer(channelId,GraftMessage.MSG_ID, GraftMessage.serializer);
        registerMessageSerializer(channelId,IHaveMessage.MSG_ID, IHaveMessage.serializer);


        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, GossipMessage.MSG_ID, this::uponReceiveGossip);
        registerMessageHandler(channelId, PruneMessage.MSG_ID, this::uponReceivePrune);
        registerMessageHandler(channelId, GraftMessage.MSG_ID, this::uponReceiveGraft);
        registerMessageHandler(channelId, IHaveMessage.MSG_ID, this::uponReceiveIHave);
        registerStreamDataHandler(channelId,this::uponStreamBytes,null, this::uponMsgFail2);



        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(IHaveTimeout.TIMER_ID, this::uponIHaveTimeout);


        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(BroadcastRequest.REQUEST_ID, this::uponBroadcast);
        registerRequestHandler(AskFileRequest.REQUEST_ID, this::uponAskFile);



        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(NeighUp.NOTIFICATION_ID, this::uponNeighbourUp);
        subscribeNotification(NeighDown.NOTIFICATION_ID, this::uponNeighbourDown);

        registerChannelEventHandler(channelId, OnStreamConnectionUpEvent.EVENT_ID, this::uponStreamConnectionUp);

    }

    FileOutputStream fileOutputStream;
    int filesSent = 0;
    private void uponStreamConnectionUp(OnStreamConnectionUpEvent event, int channelId) {
        //System.out.println("STREAM CONNECTION OPENED "+event.inConnection);
        if(event.inConnection){
            if(filesSent>2){
                filesSent++;
                return;
            }
            try {
                Path filePath = Paths.get(this.filePath);
                event.babelInputStream.writeFile(filePath.toFile());
                logger.info("FILE SENT TO {}",event.getNode());
                filesSent++;
            }catch (Exception e){e.printStackTrace();
                System.exit(0);
            }
            //sendFile
        }else{
            try{
                fileOutputStream = new FileOutputStream("movies/"+myself.toString()+"_"+getNetworkProtocol(channelId)+"_copy.mp4");
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    long totalT = 0;
    private void uponStreamBytes(BabelStreamDeliveryEvent event) {
        if(totalT==0){
            logger.info("{} RECEIVING FILE FROM {}",myself,event.getFrom());
        }
        totalT += event.babelOutputStream.readableBytes();
        try{
            fileOutputStream.write(event.babelOutputStream.readBytes());
            if(totalT==1035368729){
                fileOutputStream.close();
                //System.out.println("RECEIVED ALL BYTES!!");
                logger.info("FILE DELIVERED");
                DeliverReply d = new DeliverReply(null);
                d.broadCast = true;
                sendReply(d,DisseminationConsumer2.PROTO_ID);
                logger.info("REPLY SENT TO {} ",DisseminationConsumer2.PROTO_ID);
            }else if(totalT>1035368729){
                System.out.println("GREATER GREATER ");
                logger.info("FILE DELIVERED greater");

            }
        }catch (Exception e){e.printStackTrace();
        logger.info("TOTTTTTTTTTTTTTAL {}",totalT);
        System.exit(0);}
    }
    private void uponMsgFail2(OnStreamDataSentEvent msg, Host host, short destProto,
                              Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponReceiveGossip(GossipMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.trace("Received {} from {}", msg, from);
        if(!received.containsKey(msg.getMid())) {
            sendReply(new DeliverReply(msg.getContent()), msg.getToDeliver());
            received.put(msg.getMid(), msg);
            stored.add(msg.getMid());
            if (stored.size() > space) {
                int torm = stored.poll();
                received.put(torm, null);
            }

            Long tid;
            if((tid = onGoingTimers.remove(msg.getMid())) != null) {
                cancelTimer(tid);
            }
            eagerPush(msg, msg.getRound() +1, from);
            lazyPush(msg, msg.getRound() +1, from);

            if(eager.add(from)) {
                logger.trace("Added {} to eager {}", from, eager);
                logger.debug("Added {} to eager", from);
            }
            if(lazy.remove(from)) {
                logger.trace("Removed {} from lazy {}", from, lazy);
                logger.debug("Removed {} from lazy", from);
            }

            optimize(msg, msg.getRound(), from);
        } else {
            if(eager.remove(from)) {
                logger.trace("Removed {} from eager {}", from, eager);
                logger.debug("Removed {} from eager", from);
            } if(lazy.add(from)) {
                logger.trace("Added {} to lazy {}", from, lazy);
                logger.debug("Added {} to lazy", from);
            }
            sendMessage(new PruneMessage(), from);
        }
    }

    private void eagerPush(GossipMessage msg, int round, Host from) {
        for(Host peer : eager) {
            if(!peer.equals(from)) {
                sendMessage(msg.setRound(round), peer);
                logger.trace("Sent {} to {}", msg, peer);
            }
        }
    }

    private void lazyPush(GossipMessage msg, int round, Host from) {
        for(Host peer : lazy) {
            if(!peer.equals(from)) {
                lazyQueue.add(new AddressedIHaveMessage(new IHaveMessage(msg.getMid(), round), peer));
            }
        }
        dispatch();
    }

    private void dispatch() {
        Set<AddressedIHaveMessage> announcements = policy.apply(lazyQueue);
        for(AddressedIHaveMessage msg : announcements) {
            sendMessage(msg.msg, msg.to);
        }
        lazyQueue.removeAll(announcements);
    }


    private void optimize(GossipMessage msg, int round, Host from) {

    }

    private void uponReceivePrune(PruneMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.trace("Received {} from {}", msg, from);
        if(eager.remove(from)) {
            logger.trace("Removed {} from eager {}", from, eager);
            logger.debug("Removed {} from eager", from);
        }
        if(lazy.add(from)) {
            logger.trace("Added {} to lazy {}", from, lazy);
            logger.debug("Added {} to lazy", from);
        }
    }

    private void uponReceiveGraft(GraftMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.trace("Received {} from {}", msg, from);
        if(eager.add(from)) {
            logger.trace("Added {} to eager {}", from, eager);
            logger.debug("Added {} to eager", from);
        } if(lazy.remove(from)) {
            logger.trace("Removed {} from lazy {}", from, lazy);
            logger.debug("Removed {} from lazy", from);
        }

        if(received.getOrDefault(msg.getMid(), null) != null) {
            sendMessage(received.get(msg.getMid()), from);
        }
    }

    private void uponReceiveIHave(IHaveMessage msg, Host from, short sourceProto, int channelId,String connectionId) {
        logger.trace("Received {} from {}", msg, from);
        if(!received.containsKey(msg.getMid())) {
            if(!onGoingTimers.containsKey(msg.getMid())) {
                long tid = setupTimer(new IHaveTimeout(msg.getMid()), timeout1);
                onGoingTimers.put(msg.getMid(), tid);
            }
            missing.computeIfAbsent(msg.getMid(), v-> new LinkedList<>()).add(new MessageSource(from, msg.getRound()));
        }
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponIHaveTimeout(IHaveTimeout timeout, long timerId) {
        if(!received.containsKey(timeout.getMid())) {
            MessageSource msgSrc = missing.get(timeout.getMid()).poll();
            if (msgSrc != null) {
                long tid = setupTimer(timeout, timeout2);
                onGoingTimers.put(timeout.getMid(), tid);

                if (eager.add(msgSrc.peer)) {
                    logger.trace("Added {} to eager {}", msgSrc.peer, eager);
                    logger.debug("Added {} to eager", msgSrc.peer);
                }
                if (lazy.remove(msgSrc.peer)) {
                    logger.trace("Removed {} from lazy {}", msgSrc.peer, lazy);
                    logger.debug("Removed {} from lazy", msgSrc.peer);
                }

                sendMessage(new GraftMessage(timeout.getMid(), msgSrc.round), msgSrc.peer);
            }
        }
    }


    /*--------------------------------- Requests ---------------------------------------- */
    private void uponBroadcast(BroadcastRequest request, short sourceProto) {
        //System.out.println(myself.toString()+"-+ GOING TO BROAD CAST "+new String(request.getMsg())+" NR: "+super.numConnectedPeers(channelID));
        int mid = hashProducer.hash(request.getMsg());
        GossipMessage msg = new GossipMessage(mid, 0, sourceProto, request.getMsg());
        eagerPush(msg, 0, myself);
        lazyPush(msg, 0, myself);
        sendReply(new DeliverReply(request.getMsg()), sourceProto);
        received.put(mid, msg);
        stored.add(mid);
        if (stored.size() > space) {
            int torm = stored.poll();
            received.put(torm, null);
        }
    }

    private void uponAskFile(AskFileRequest request, short sourceProto) {
        //System.out.println("RECEIVED ASK FILE REQUEST. OPENING STREAM CONNECTION");
        openStreamConnection(request.fileOwner,channelID,PROTOCOL_ID,PROTOCOL_ID,true);
    }
    /*--------------------------------- Notifications ---------------------------------------- */
    private void uponNeighbourUp(NeighUp notification, short sourceProto) {
        if(eager.add(notification.getPeer())) {
            logger.trace("Added {} to eager {}", notification.getPeer(), eager);
            logger.debug("Added {} to eager", notification.getPeer());
        } else
            logger.trace("Received neigh up but {} is already in eager {}", notification.getPeer(), eager);
    }

    private void uponNeighbourDown(NeighDown notification, short sourceProto) {
        if(eager.remove(notification.getPeer())) {
            logger.trace("Removed {} from eager {}", notification.getPeer(), eager);
            logger.debug("Removed {} from eager", notification.getPeer());
        }
        if(lazy.remove(notification.getPeer())) {
            logger.trace("Removed {} from lazy {}", notification.getPeer(), lazy);
            logger.debug("Removed {} from lazy", notification.getPeer());
        }

        MessageSource msgSrc  = new MessageSource(notification.getPeer(), 0);
        for(Queue<MessageSource> iHaves : missing.values()) {
            iHaves.remove(msgSrc);
        }
        closeConnection(notification.getPeer());
    }

    @Override
    public void init(Properties props){
        if (props.containsKey("contact")){
            IHAVE_THE_FILE = false;
        }else{
            IHAVE_THE_FILE = true;
        }
    }
}
