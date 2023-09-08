package org.fileStreaming.protocols;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fileStreaming.messages.BabelStreamDeliveryEvent;
import org.fileStreaming.messages.IHaveFile;
import org.fileStreaming.timers.BroadcastTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class StreamingServer extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(StreamingServer.class);
    public static final short PROTO_ID = 400;
    public Properties properties;
    //public final int channelId;
    //public final Host broadcastAddress;
    public final int connectionProtoChannel;
    public final Host connectionProtoHost;
    public final long disseminationStart, disseminationPeriod;
    Map<Host,Pair<Long,Long>> timeElapsed;
    Path filePath;

    public StreamingServer(String protoName, Properties properties) throws Exception{
        super(protoName, PROTO_ID);
        this.properties = properties;
        String address = properties.getProperty("address");
        String port = properties.getProperty("port");
        connectionProtoHost = new Host(InetAddress.getByName(address),Integer.parseInt(port));
        filePath = Paths.get(properties.getProperty("FILE_PATH"));

        disseminationPeriod = Long.parseLong(properties.getProperty("disseminationPeriod"));
        disseminationStart = Long.parseLong(properties.getProperty("disseminationStart"));

        String channelName;
        timeElapsed = new HashMap<>();
        logger.info("SERVER UP");

        /**
        //String broadCastAddress = properties.getProperty("BROADCAST_ADDRESS");
        //String broadcastPort = properties.getProperty("broadcast_port");
        //broadcastAddress = new Host(InetAddress.getByName(broadCastAddress),Integer.parseInt(broadcastPort));


        channelProps = TCPChannelUtils.udpChannelProperties(broadCastAddress,broadcastPort);
        channelProps.setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
        channelProps.setProperty(NettyUDPServer.UDP_BROADCAST_PROP,"ON");
        channelId = createChannel(BabelUDPChannel.NAME, channelProps);
         **/
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, address); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, port); //The port to bind to
        connectionProtoChannel = createChannel(TCPChannel.NAME, channelProps);

        registerMessageSerializer(connectionProtoChannel, BabelStreamDeliveryEvent.ID,BabelStreamDeliveryEvent.serializer);
        registerMessageSerializer(connectionProtoChannel, IHaveFile.ID,IHaveFile.serializer);
        registerMessageHandler(connectionProtoChannel, IHaveFile.ID, this::uponIHaveFileMessage,null,null);
        //registerMessageHandler(connectionProtoChannel, BabelStreamDeliveryEvent.ID, this::uponIHaveFileMessage,null,null);

        registerChannelEventHandler(connectionProtoChannel, InConnectionUp.EVENT_ID, this::uponMessageConnectionUp);
        registerChannelEventHandler(connectionProtoChannel, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
        //registerTimerHandler(BroadcastTimer.TimerCode, this::uponBroadcastTime);
    }
    private void uponIHaveFileMessage(IHaveFile msg, Host from, short sourceProto, int channelId) {
        Pair<Long,Long> p = timeElapsed.get(from);
        Pair<Long,Long> p2 = Pair.of(p.getLeft(),msg.fileLength-p.getLeft());
        timeElapsed.put(from,p2);
        logger.info("{} {} HAS THE FILE!!! ELAPSED: {}",connectionProtoHost,from,msg.fileLength);
        System.out.println("RECEIVED FROM "+from+" --- "+msg.fileLength+" +++ SENT "+p.getLeft());
        clients--;
        if(clients==0){
            List<Long> elapsed = new LinkedList<>();
            for (Pair<Long, Long> value : timeElapsed.values()) {
                elapsed.add(value.getRight());
            }
            String h = Arrays.toString(elapsed.toArray());
            logger.info(h);
            System.out.println(h);
        }
    }
    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Host (in) {} is down", event.getNode());
    }
    int clients = 0;
    private void startStreaming(Host host) {
        logger.info("{} Stream CONNECTION UP. SENDING THE FILE TO {}",connectionProtoHost,host);
        long start = System.currentTimeMillis();
        timeElapsed.put(host,Pair.of(start,0L));
        clients++;
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                int size = 1024*64;
                FileInputStream ff = new FileInputStream(filePath.toFile());
                byte [] read = new byte[size];
                int ef = 0;
                while ( (ef = ff.read(read,0,size))>0){
                    //sendMessage(new BabelStreamDeliveryEvent(read,ef),host);
                    this.sendMessage(connectionProtoChannel,new BabelStreamDeliveryEvent(read,ef),PROTO_ID,host, 1);
                    read = new byte[size];
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }).start();
    }
    int countBroadcast;
    private void uponMessageConnectionUp(InConnectionUp event, int channelId) {
        countBroadcast++;
        String name="name_"+countBroadcast;
        System.out.println("TIMER TRIGGERED "+name);
        IHaveFile iHaveFile = new IHaveFile(filePath.toFile().length(),name,connectionProtoHost);
        this.sendMessage(connectionProtoChannel,iHaveFile,PROTO_ID,event.getNode(), 1);
        System.out.println("BROADCAST SENT");
        logger.info("{} MESSAGE SENT TO {}",connectionProtoHost,event.getNode());
        BroadcastTimer b = new BroadcastTimer();
        b.host = event.getNode();
        setupTimer(b, disseminationStart);
    }
    private void uponBroadcastTime(BroadcastTimer timer, long timerId) {
        logger.info("{} TIMER TRIGGERED. OPENED CONNECTION TO {}",connectionProtoChannel);
        startStreaming(timer.host);
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        registerTimerHandler(BroadcastTimer.TimerCode, this::uponBroadcastTime);

        //long id = setupPeriodicTimer(new BroadcastTimer(), disseminationStart, disseminationPeriod);
        //System.out.println("SET TIMER: "+disseminationStart + " -- "+disseminationPeriod+" ID: "+id);
    }
}
