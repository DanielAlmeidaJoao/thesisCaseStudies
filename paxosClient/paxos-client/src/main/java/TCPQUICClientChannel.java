import appExamples2.appExamples.channels.FactoryMethods;
import io.netty.buffer.ByteBuf;
import network.HelperMethods;
import network.RequestMessage;
import network.ResponseMessage;
import pt.unl.fct.di.novasys.babel.core.BabelMessageSerializer;
import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.channels.ChannelHandlerMethods;
import quicSupport.channels.NettyChannelInterface;
import quicSupport.channels.NettyQUICChannel;
import quicSupport.channels.SingleThreadedQuicChannel;
import quicSupport.utils.enums.NetworkProtocol;
import quicSupport.utils.enums.NetworkRole;
import quicSupport.utils.enums.TransmissionType;
import tcpSupport.tcpChannelAPI.channel.NettyTCPChannel;
import tcpSupport.tcpChannelAPI.channel.SingleThreadedNettyTCPChannel;
import tcpSupport.tcpChannelAPI.utils.BabelInputStream;
import tcpSupport.tcpChannelAPI.utils.BabelOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class TCPQUICClientChannel implements ChannelHandlerMethods, NetworkClientInterface{
    private final Map<String, Map<Long, CompletableFuture<ResponseMessage>>> opCallbacks;

    public NettyChannelInterface nettyChannelInterface;
    public TCPQUICClientChannel(Properties properties, Map<String, Map<Long, CompletableFuture<ResponseMessage>>> opCallbacks, NetworkProtocol protocol) throws Exception {
        this.opCallbacks = opCallbacks;
        BabelMessageSerializer serializer = new BabelMessageSerializer(new ConcurrentHashMap<>());
        serializer.registerProtoSerializer(ResponseMessage.MSG_ID,ResponseMessage.serializer);
        serializer.registerProtoSerializer(RequestMessage.MSG_ID,RequestMessage.serializer);
        if(NetworkProtocol.TCP==protocol){
            if(properties.getProperty("SINLGE_TRHEADED")!=null){
                nettyChannelInterface = new SingleThreadedNettyTCPChannel(properties,this, NetworkRole.CLIENT,serializer);
            }else {
                nettyChannelInterface = new NettyTCPChannel(properties,false,this,NetworkRole.CLIENT,serializer);
            }
        }else{
            if(properties.getProperty("SINLGE_TRHEADED")!=null){
                nettyChannelInterface = new SingleThreadedQuicChannel(properties,NetworkRole.CLIENT,this,serializer);
            }else {
                nettyChannelInterface = new NettyQUICChannel(properties,false,NetworkRole.CLIENT,this,serializer);
            }
        }

    }

    @Override
    public void onStreamErrorHandler(InetSocketAddress inetSocketAddress, Throwable throwable, String s) {

    }

    @Override
    public void onOpenConnectionFailed(InetSocketAddress inetSocketAddress, Throwable throwable, TransmissionType transmissionType, String s) {

    }

    @Override
    public void failedToCloseStream(String s, Throwable throwable) {

    }

    @Override
    public void onMessageSent(BabelMessage o, Throwable throwable, InetSocketAddress inetSocketAddress, TransmissionType transmissionType, String s) {

    }

    @Override
    public void onStreamDataSent(InputStream inputStream, byte[] bytes, int i, Throwable throwable, InetSocketAddress inetSocketAddress, TransmissionType transmissionType, String s) {

    }


    @Override
    public void failedToCreateStream(InetSocketAddress inetSocketAddress, Throwable throwable) {

    }

    @Override
    public void failedToGetMetrics(Throwable throwable) {

    }

    @Override
    public void onStreamClosedHandler(InetSocketAddress inetSocketAddress, String s, boolean b, TransmissionType transmissionType) {

    }

    @Override
    public void onChannelReadDelimitedMessage(String conID, BabelMessage babelMessage, InetSocketAddress inetSocketAddress) {
        ResponseMessage responseMessage = (ResponseMessage) babelMessage.getMessage();
        opCallbacks.get(conID).get(responseMessage.getOpId()).complete(responseMessage);
    }

    @Override
    public void onChannelReadFlowStream(String s, BabelOutputStream babelOutputStream, InetSocketAddress inetSocketAddress, BabelInputStream babelInputStream, short i) {

    }

    @Override
    public void onConnectionUp(boolean b, InetSocketAddress inetSocketAddress, TransmissionType transmissionType, String s, BabelInputStream babelInputStream) {
        System.out.println(System.currentTimeMillis()+" PORRRAS :COMPLETED WITH ID: "+s+" "+inetSocketAddress);
        System.out.println("CON PEERS "+nettyChannelInterface.connectedPeers());
        connecting.complete(s);
    }
    CompletableFuture<String> connecting;
    public String openMessageConnection(Host peer, short proto, CompletableFuture<String> waiting) {
        connecting = waiting;
        short d = -1;
        System.out.println("OPENING CONNECTION TOO "+peer.address);
        return nettyChannelInterface.open(peer.address,TransmissionType.STRUCTURED_MESSAGE,d,d,false);
    }
    public void send(RequestMessage requestMessage,String conId) throws IOException {
        nettyChannelInterface.send(conId,new BabelMessage(requestMessage, RequestMessage.sourceProto,RequestMessage.destProto));
    }

    @Override
    public void metrics() {

    }
    /*
    @Override
    public void onChannelReadDelimitedMessage(String conId, ByteBuf byteBuf, InetSocketAddress inetSocketAddress) {
        try {
            ResponseMessage responseMessage = HelperMethods.deserialize(byteBuf);
            opCallbacks.get(conId).get(responseMessage.getOpId()).complete(responseMessage);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    } **/


}
