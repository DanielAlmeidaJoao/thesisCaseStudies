import appExamples2.appExamples.channels.FactoryMethods;
import io.netty.buffer.ByteBuf;
import network.HelperMethods;
import network.RequestMessage;
import network.ResponseMessage;
import pt.unl.fct.di.novasys.babel.core.BabelMessageSerializer;
import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.utils.enums.TransmissionType;
import udpSupport.channels.SingleThreadedUDPChannel;
import udpSupport.channels.UDPChannel;
import udpSupport.channels.UDPChannelHandlerMethods;
import udpSupport.channels.UDPChannelInterface;
import udpSupport.metrics.NetworkStats;
import udpSupport.metrics.UDPNetworkStatsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPClientChannel  implements UDPChannelHandlerMethods, NetworkClientInterface {

    private UDPChannelInterface udpChannelInterface;
    private final Map<String, Map<Long, CompletableFuture<ResponseMessage>>> opCallbacks;


    public UDPClientChannel (Properties properties, Map<String, Map<Long, CompletableFuture<ResponseMessage>>> opCallbacks)throws Exception {
        this.opCallbacks = opCallbacks;
        BabelMessageSerializer serializer = new BabelMessageSerializer(new ConcurrentHashMap<>());
        serializer.registerProtoSerializer(ResponseMessage.MSG_ID,ResponseMessage.serializer);
        serializer.registerProtoSerializer(RequestMessage.MSG_ID,RequestMessage.serializer);

        if(properties.getProperty("SINLGE_TRHEADED")!=null){
            udpChannelInterface = new SingleThreadedUDPChannel(properties,this,serializer);
        }else {
            udpChannelInterface = new UDPChannel(properties,false,this,serializer);
        }
    }
    @Override
    public void onPeerDown(InetSocketAddress inetSocketAddress) {

    }

    @Override
    public void onDeliverMessage(BabelMessage babelMessage, InetSocketAddress inetSocketAddress) {
        ResponseMessage responseMessage = (ResponseMessage) babelMessage.getMessage();
        String id = map.get(inetSocketAddress);
        opCallbacks.get(id).get(responseMessage.getOpId()).complete(responseMessage);

    }

    @Override
    public void onMessageSentHandler(boolean b, Throwable throwable, BabelMessage t, InetSocketAddress inetSocketAddress) {

    }

    AtomicInteger atomicInteger = new AtomicInteger(0);
    public Map<InetSocketAddress,String> map = new HashMap<>();
    public Map<String,InetSocketAddress> idToAddress = new HashMap<>();
    public String openMessageConnection(Host peer, short proto, CompletableFuture<String> waiting) {
        String id = "updchan"+atomicInteger.incrementAndGet();
        InetSocketAddress inetSocketAddress = peer.address;
        map.put(inetSocketAddress,id);
        idToAddress.put(id,inetSocketAddress);
        waiting.complete(id);
        return id;
    }
    /**
    @Override
    public void onDeliverMessage(ByteBuf byteBuf, InetSocketAddress inetSocketAddress) {
        try {
            ResponseMessage responseMessage = HelperMethods.deserialize(byteBuf);
            String id = map.get(inetSocketAddress);
            opCallbacks.get(id).get(responseMessage.getOpId()).complete(responseMessage);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    } **/


    public void metrics(){
        System.out.println("READING METRICS "+udpChannelInterface.metricsEnabled());
        List<UDPNetworkStatsWrapper> list = udpChannelInterface.getMetrics();
        for (UDPNetworkStatsWrapper udpNetworkStatsWrapper : list) {
            NetworkStats stats = udpNetworkStatsWrapper.sentAckedMessageStats;
            System.out.println("SENT - "+stats.getMessagesSent()+" RECEIVED: "+stats.getMessagesReceived()
            +" ACKED MESSAGES:"+stats.getMessagesAcked());
        }
    }

    public void send(RequestMessage requestMessage, String conId) throws IOException {
        InetSocketAddress address = idToAddress.get(conId);
        udpChannelInterface.sendMessage(new BabelMessage(requestMessage, RequestMessage.sourceProto,RequestMessage.destProto),address);
    }
}
