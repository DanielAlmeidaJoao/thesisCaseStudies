package org.fileStreaming.protocols;

import appExamples2.appExamples.channels.FactoryMethods;
import appExamples2.appExamples.channels.StreamDeliveredHandlerFunction;
import appExamples2.appExamples.channels.babelNewChannels.quicChannels.BabelQUIC_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.tcpChannels.BabelTCP_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.udpBabelChannel.BabelUDPChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fileStreaming.messages.FileBytesMessage;
import org.fileStreaming.messages.IHaveFile;
import org.fileStreaming.timers.BroadcastTimer;
import pt.unl.fct.di.novasys.babel.channels.events.*;
import pt.unl.fct.di.novasys.babel.core.GenericProtocolExtension;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.internal.BabelStreamDeliveryEvent;
import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.client_server.QUICServerEntity;
import quicSupport.utils.QUICLogics;
import tcpSupport.tcpChannelAPI.utils.BabelInputStream;
import tcpSupport.tcpChannelAPI.utils.BabelOutputStream;
import tcpSupport.tcpChannelAPI.utils.TCPChannelUtils;
import udpSupport.client_server.NettyUDPServer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

public class StreamingClient extends GenericProtocolExtension {
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
    public final boolean isMessageSend;

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
        isMessageSend = properties.get("MESSAGE")!=null;


        Properties channelProps;
        /**
        channelProps = TCPChannelUtils.udpChannelProperties(broadCastAddress,broadcastPort);
        channelProps.setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
        //channelProps.setProperty(NettyUDPServer.MIN_UDP_RETRANSMISSION_TIMEOUT,"250");
        //channelProps.setProperty(NettyUDPServer.MAX_UDP_RETRANSMISSION_TIMEOUT,"1000");
        channelProps.setProperty(NettyUDPServer.UDP_BROADCAST_PROP,"ON");
        //channelProps.setProperty(TCPChannelUtils.CHANNEL_METRICS,"ON");
        channelId = createChannel(BabelUDPChannel.NAME, channelProps);
        **/

        String [] contact = properties.getProperty("contact").split(":");
        server = new Host(InetAddress.getByName(contact[0]),Integer.parseInt(contact[1]));
        NETWORK_PROTO = properties.getProperty("NETWORK_PROTO");
        connectionProtoChannel = createConnectionChannel(NETWORK_PROTO, address, port,properties);
        registerMessageSerializer(connectionProtoChannel, IHaveFile.ID,IHaveFile.serializer);
        registerMessageSerializer(connectionProtoChannel, FileBytesMessage.ID,FileBytesMessage.serializer);

        registerMessageHandler(connectionProtoChannel, IHaveFile.ID, this::uponIHaveFileMessage,null,null);
        registerMessageHandler(connectionProtoChannel, FileBytesMessage.ID, this::uponFileByets,null,null);

        registerChannelEventHandler(connectionProtoChannel, OnStreamConnectionUpEvent.EVENT_ID, this::uponStreamConnectionUp);
        registerChannelEventHandler(connectionProtoChannel, OnMessageConnectionUpEvent.EVENT_ID, this::uponMessageConnectionUp);
        registerChannelEventHandler(connectionProtoChannel, OnChannelError.EVENT_ID, this::uponChannelError);
        registerStreamDataHandler(connectionProtoChannel,this::uponStreamBytes,null, this::uponMsgFail2);
        registerChannelEventHandler(connectionProtoChannel, OnConnectionDownEvent.EVENT_ID, this::uponConnectionDown);

        logger.info("{} IS MESSAGE {} . PROTO {}. CLIENT",connectionProtoHost,isMessageSend,NETWORK_PROTO);


    }
    long totalReceived = 0;
    long timeStart = 0;
    private void uponConnectionDown(OnConnectionDownEvent event, int channelId) {
        logger.info("CONNECTION DOWN: {} {} {}",event.connectionId,event.getNode(),event.type);
    }


    private void executeAux(int readAbleBytes, byte [] data, Host from){
        if(timeStart==0){
            logger.info("RECEIVING FILE BYTES!!");
            timeStart = System.currentTimeMillis();
        }
        int available = readAbleBytes;
        totalReceived += available;
        long timeReceivedAll =-1;
        if(iHaveFile.fileLength==totalReceived){
            timeReceivedAll =System.currentTimeMillis();
            logger.info("TIME_ELAPSED: {}. RECEIVED BYTES {}",timeReceivedAll-timeStart,totalReceived);
        }
        byte [] p = data;
        try {
            fileOutputStream.write(p);
            if(iHaveFile.fileLength==totalReceived){
                IHaveFile iHaveFile1 = new IHaveFile(timeReceivedAll,iHaveFile.fileName,connectionProtoHost);
                sendMessage(iHaveFile1,from);
                fileOutputStream.close();
            }
        } catch (IOException e) {
            logger.info(e.getLocalizedMessage());
            e.printStackTrace();
            System.exit(0);
        }
    }
    void execute(BabelStreamDeliveryEvent event){
        executeAux(event.babelOutputStream.readableBytes(),event.babelOutputStream.readBytes(),event.getFrom());
    }

    private void uponStreamBytes(BabelStreamDeliveryEvent event) {
        execute(event);
    }

    private void uponStreamConnectionUp(OnStreamConnectionUpEvent event, int channelId) {
        logger.info("{} STREAM CONNECTION TO {} IS UP.",connectionProtoHost,event.getNode());
    }
    private void uponMessageConnectionUp(OnMessageConnectionUpEvent event, int channelId) {
        logger.info("{} MESSAGE CONNECTION TO {} IS UP.",connectionProtoHost,event.getNode());
    }
    private void uponChannelError(OnChannelError event, int channelId) {
        logger.info("{} ERROR ----- {}",connectionProtoHost,event);
    }

    private void uponFileByets(FileBytesMessage msg, Host from, short sourceProto, int channelId, String streamId) {
        executeAux(msg.dataLength,msg.data,from);
    }
    private void uponIHaveFileMessage(IHaveFile msg, Host from, short sourceProto, int channelId, String streamId) {
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
            if(!isMessageSend){
                openStreamConnectionEvenIfItsConnected(iHaveFile.host,connectionProtoChannel);
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
        openMessageConnection(server);
    }
    public int createConnectionChannel(String proto, String address, String port,Properties properties) throws IOException {
        int channel;
        StreamDeliveredHandlerFunction f=null;
        if(properties.getProperty("NETTY_HANDLER")!=null){
            f = this::execute;
            logger.info("NETTY_HANDLER ON");
        }
        Pair<String,Properties> p = StreamingServer.createConnectionChannel(proto,address,port,properties);
        p.getRight().setProperty(FactoryMethods.SERVER_THREADS,properties.getProperty("SERVER_THREADS"));
        if(proto.equalsIgnoreCase("quic")){
            channel = createChannel(BabelQUIC_P2P_Channel.CHANNEL_NAME,p.getRight(),f);
        }else{
            System.out.println("TCP ON");
            channel = createChannel(BabelTCP_P2P_Channel.CHANNEL_NAME,p.getRight(),f);
        }
        return channel;
    }
    private void uponMsgFail2(OnStreamDataSentEvent msg, Host host, short destProto,
                              Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }
}
