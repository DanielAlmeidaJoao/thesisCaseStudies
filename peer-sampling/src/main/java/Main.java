import appExamples2.appExamples.channels.babelNewChannels.quicChannels.BabelQUIC_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.tcpChannels.BabelTCP_P2P_Channel;
import appExamples2.appExamples.channels.babelNewChannels.udpBabelChannel.BabelUDPChannel;
import appExamples2.appExamples.channels.babelNewChannels.udpBabelChannel.BabelUDPInitializer;
import appExamples2.appExamples.channels.initializers.BabelQUICChannelInitializer;
import appExamples2.appExamples.channels.initializers.BabelTCPChannelInitializer;
import dissemination.DisseminationConsumer;
import dissemination.plumtree.PlumTree;
import dissemination.plumtree.utils.HashProducer;
import dissemination.requests.BroadcastRequest;
import dissemination.requests.DeliverReply;
import fileStreamingProto.*;
import hyparview.HyparView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.utils.enums.NetworkRole;

import java.io.File;
import java.net.*;
import java.security.InvalidParameterException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;


public class Main {

    static {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final String DEFAULT_CONF = "config.properties";
    //ol
    public static void main(String[] args) throws Exception {
        Babel babel = Babel.getInstance();
        babel.registerChannelInitializer(BabelQUIC_P2P_Channel.CHANNEL_NAME,new BabelQUICChannelInitializer(NetworkRole.P2P_CHANNEL));
        babel.registerChannelInitializer(BabelTCP_P2P_Channel.CHANNEL_NAME,new BabelTCPChannelInitializer(NetworkRole.P2P_CHANNEL));
        babel.registerChannelInitializer(BabelUDPChannel.NAME,new BabelUDPInitializer());

        Properties props = Babel.loadConfig(args, DEFAULT_CONF);
        addInterfaceIp(props);
        String address = props.getProperty("address");
        String port = props.getProperty("port");
        String logFile = props.getProperty("logFile");
        String APP_TYPR = props.getProperty("APP_TYPE");
        if(logFile == null ){
            System.out.println("LOG FILE NAME IS NULL");
            //System.exit(0);
        }
        Host self = new Host(InetAddress.getByName(address), Short.parseShort(port));
        //PeerSampling sampling = new PeerSampling(props);
        String NETWORK_PROTO  = props.getProperty("NETWORK_PROTO");

        logger.info("Hello, I am {}. PROTO: {}", self,NETWORK_PROTO);
        HyparViewInterface sampling;
        PlumTreeInterface flood;
        if(APP_TYPR.equals("NORMAL")){
            sampling = new HyparView(null, props, self);
            babel.registerProtocol((HyparView)sampling);

            flood = new PlumTree("CHANNEL NAME",sampling.getChannel(),props,self);
            babel.registerProtocol((PlumTree)flood);

            DisseminationConsumer dissemination = new DisseminationConsumer(flood.getProtoId(), props);
            babel.registerProtocol(dissemination);
            babel.start();

            sampling.init(props);
            flood.init(props);
            dissemination.init(props);
        }else{
            sampling = new HyparViewV2(null, props, self);
            babel.registerProtocol((HyparViewV2)sampling);
            sampling.init(props);

            flood = new PlumTreeV2("CHANNEL NAME",sampling.getChannel(),props,self);
            babel.registerProtocol((PlumTreeV2)flood);
            flood.init(props);

            DisseminationConsumer2 dissemination = new DisseminationConsumer2(flood.getProtoId(), props);
            babel.registerProtocol(dissemination);
            dissemination.init(props);
            babel.start();
        }


        //FloodGossip flood = new FloodGossip(props, sampling.getChannel(), sampling.getProtoId());



        Runtime.getRuntime().addShutdownHook(new Thread(() ->{

            System.out.println("CONNECTED PEERS "+sampling.connectedPeers());
            if(sampling.getChannel()>0){
                return;
            }
            /**
            NetworkProtocol networkProtocol = sampling.getChannelProtocol();
            if(NetworkProtocol.UDP==networkProtocol){
                sampling.metrics();
                return;
            }  **/
            try{
                String fName = String.format("logs/%s.info",logFile);
                File f = new File(fName);
                if(f.exists()){
                    System.out.println(f.delete());
                }else{
                    System.out.println("NOT EXISTS!");
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            logger.info("Goodbye");
        }));
    }

    public static String getIpOfInterface(String interfaceName) throws SocketException {
        NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
        System.out.println(networkInterface);
        Enumeration<InetAddress> inetAddress = networkInterface.getInetAddresses();
        InetAddress currentAddress;
        while (inetAddress.hasMoreElements()) {
            currentAddress = inetAddress.nextElement();
            if (currentAddress instanceof Inet4Address && !currentAddress.isLoopbackAddress()) {
                return currentAddress.getHostAddress();
            }
        }
        return null;
    }

    public static void addInterfaceIp(Properties props) throws SocketException, InvalidParameterException {
        String interfaceName;
        if ((interfaceName = props.getProperty("interface")) != null) {
            String ip = getIpOfInterface(interfaceName);
            if (ip != null)
                props.setProperty("address", ip);
            else {
                throw new InvalidParameterException("Property interface is set to " + interfaceName + ", but has no ip");
            }
        }
    }



}
