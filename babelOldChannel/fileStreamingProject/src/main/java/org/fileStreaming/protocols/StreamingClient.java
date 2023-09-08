package org.fileStreaming.protocols;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fileStreaming.messages.BabelStreamDeliveryEvent;
import org.fileStreaming.messages.IHaveFile;
import org.fileStreaming.timers.BroadcastTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionUp;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionFailed;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionUp;
import pt.unl.fct.di.novasys.network.data.Host;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

public class StreamingClient extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(StreamingClient.class);

    public static final short PROTO_ID = 400;
    public Properties properties;
    //public final int channelId;
    public final  int connectionProtoChannel;
    //public final Host broadcastAddress;
    public final  Host connectionProtoHost, server;
    IHaveFile iHaveFile;
    FileOutputStream fileOutputStream;
    String NETWORK_PROTO;
    public final long disseminationStart;
    Path filePath;
    public StreamingClient(String protoName, Properties properties) throws Exception{
        super(protoName, PROTO_ID);
        this.properties = properties;
        String address = properties.getProperty("address");
        String port = properties.getProperty("port");
        //String broadcastPort = properties.getProperty("broadcast_port");
        //String broadCastAddress = properties.getProperty("BROADCAST_ADDRESS");

        //broadcastAddress = new Host(InetAddress.getByName(broadCastAddress),Integer.parseInt(broadcastPort));
        connectionProtoHost = new Host(InetAddress.getByName(address),Integer.parseInt(port));
        disseminationStart = Long.parseLong(properties.getProperty("disseminationStart"));

        Properties channelProps = new Properties();

        /**
        channelProps = TCPChannelUtils.udpChannelProperties(broadCastAddress,broadcastPort);
        channelProps.setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
        //channelProps.setProperty(NettyUDPServer.MIN_UDP_RETRANSMISSION_TIMEOUT,"250");
        //channelProps.setProperty(NettyUDPServer.MAX_UDP_RETRANSMISSION_TIMEOUT,"1000");
        channelProps.setProperty(NettyUDPServer.UDP_BROADCAST_PROP,"ON");
        //channelProps.setProperty(TCPChannelUtils.CHANNEL_METRICS,"ON");
        channelId = createChannel(BabelUDPChannel.NAME, channelProps);
        **/
        logger.info("CLIENT UP");
        String [] contact = properties.getProperty("contact").split(":");
        server = new Host(InetAddress.getByName(contact[0]),Integer.parseInt(contact[1]));
        NETWORK_PROTO = properties.getProperty("NETWORK_PROTO");
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, address); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, port); //The port to bind to
        connectionProtoChannel = createChannel(TCPChannel.NAME, channelProps);
        registerMessageSerializer(connectionProtoChannel, IHaveFile.ID,IHaveFile.serializer);
        registerMessageSerializer(connectionProtoChannel, BabelStreamDeliveryEvent.ID,BabelStreamDeliveryEvent.serializer);
        registerMessageHandler(connectionProtoChannel, IHaveFile.ID, this::uponIHaveFileMessage,null,null);
        registerMessageHandler(connectionProtoChannel, BabelStreamDeliveryEvent.ID, this::execute,null,null);

        registerChannelEventHandler(connectionProtoChannel, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(connectionProtoChannel, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(connectionProtoChannel, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(connectionProtoChannel, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);

    }
    long totalReceived = 0;
    long timeStart = 0;

    void execute(BabelStreamDeliveryEvent msg, Host from, short sourceProto, int channelId){
        if(timeStart==0){
            logger.info("RECEIVING FILE BYTES!!");
            timeStart = System.currentTimeMillis();
        }
        int available =msg.dataLength;
        totalReceived += available;
        long timeReceivedAll =-1;
        if(iHaveFile.fileLength==totalReceived){
            timeReceivedAll =System.currentTimeMillis();
            logger.info("TIME_ELAPSED: {}. RECEIVED BYTES {}",timeReceivedAll-timeStart,totalReceived);
        }
        byte [] p = msg.data;
        try {
            fileOutputStream.write(p);
            if(iHaveFile.fileLength==totalReceived){
                IHaveFile iHaveFile1 = new IHaveFile(timeReceivedAll,iHaveFile.fileName,connectionProtoHost);
                sendMessage(iHaveFile1,from);
                //this.sendMessage(connectionProtoChannel,msg,PROTO_ID,from, 1);
                fileOutputStream.close();
            }
        } catch (IOException e) {
            logger.info(e.getLocalizedMessage());
            e.printStackTrace();
            System.exit(0);
        }
    }


    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.info("CONNECTION TO {} IS DOWN.",event.getNode());
    }
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.info("{} MESSAGE CONNECTION TO {} IS UP.",connectionProtoHost,event.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.trace("Host (in) {} is up", event.getNode());
    }

    //JoinMessage msg, Host from, short sourceProto, int channelId
    private void uponIHaveFileMessage(IHaveFile msg, Host from, short sourceProto, int channelId) {
        logger.info("RECEIVED I HAVE A FILE: {} {}. FROM {}",msg.fileLength,msg.fileName,from);
        if(iHaveFile==null){
            iHaveFile = msg;
            try {
                String t = UUID.randomUUID().toString();
                fileOutputStream = new FileOutputStream(t+"_"+NETWORK_PROTO+"_STREAM.mp4");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.out.println("EXIT");
                System.exit(0);
            }
        }
    }
    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        registerTimerHandler(BroadcastTimer.TimerCode, this::uponBroadcastTime);
        setupTimer(new BroadcastTimer(),disseminationStart);
    }
    private void uponBroadcastTime(BroadcastTimer timer, long timerId) {
        logger.info("{} TIMER TRIGGERED. OPENED CONNECTION TO {}",connectionProtoChannel,server);
        openConnection(server);
    }
    private void uponOutConnectionFailed(OutConnectionFailed event, int channelId) {
        logger.info("CONNECTION ERROR");
    }
}
