import io.netty.channel.*;
import network.*;
import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import quicSupport.client_server.QUICServerEntity;
import quicSupport.utils.enums.NetworkProtocol;
import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.Status;
import tcpSupport.tcpChannelAPI.utils.TCPChannelUtils;
import udpSupport.channels.UDPChannel;
import udpSupport.client_server.NettyUDPServer;

import java.net.*;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class PaxosClient extends DB {

    private static final AtomicInteger initCounter = new AtomicInteger();
    //private static final ThreadLocal<String> threadServer = new ThreadLocal<>();
    private static final Map<String, Map<Long, CompletableFuture<ResponseMessage>>> opCallbacks = new HashMap<>();
    private static AtomicInteger idCounter;
    public static Random random = new Random();
    private static int timeoutMillis;
    private static byte readType;
    private static List<String> servers;
    private static NetworkClientInterface networkClientInterface;

    public static String ip;

    public PaxosClient(){

    }

    public static String getIpOfInterface(String interfaceName) throws SocketException {
        if (interfaceName.equalsIgnoreCase("lo")) {
            return "127.0.0.1"; //This is an special exception to deal with the loopback.
        }
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
            if (ip != null) {
                props.setProperty("address", ip);
            } else {
                throw new InvalidParameterException("Property interface is set to " + interfaceName + ", but has no ip");
            }
        }
    }

    @Override
    public void init() {
        try {
            synchronized (opCallbacks) {
                if (servers == null) {
                    System.out.println("INIT2 CALLED OOPPRRU "+Thread.currentThread().getId());
                    //ONCE
                    timeoutMillis = Integer.parseInt(getProperties().getProperty("timeout_millis"));
                    int serverPort = Integer.parseInt(getProperties().getProperty("app_server_port"));
                    NetworkProtocol protocol = NetworkProtocol.valueOf(getProperties().getProperty("NETWORK_PROTOCOL"));
                    Properties properties = getProperties();
                    addInterfaceIp(properties);
                    ip = properties.getProperty("address");
                    String portServer = "35005";
                    if(NetworkProtocol.UDP==protocol){
                        Properties channelProperties = TCPChannelUtils.udpChannelProperties(ip,portServer);
                        //channelProperties.setProperty("UPD_MAX_SEND_RETRIES", "100");
                        channelProperties.setProperty("SINGLE_THREADED","ON");
                        channelProperties.setProperty(NettyUDPServer.MAX_UDP_RETRANSMISSION_TIMEOUT,"200");
                        //channelProperties.setProperty(QUICServerEntity.BUFF_ALOC_SIZE,"16384");
                        //channelProperties.setProperty(TCPChannelUtils.CHANNEL_METRICS,"ON");
                        networkClientInterface = new UDPClientChannel(channelProperties,opCallbacks);
                        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                        // Schedule a task to run after a delay of 5 seconds
                        //Runnable task = () -> networkClientInterface.metrics();
                        //scheduler.scheduleAtFixedRate(task, 5,5, TimeUnit.SECONDS);

                    }else if(NetworkProtocol.TCP==protocol){
                        Properties channelProperties = TCPChannelUtils.tcpChannelProperties(ip,portServer);
                        channelProperties.setProperty("SINGLE_THREADED","ON");
                        networkClientInterface = new TCPQUICClientChannel(channelProperties,opCallbacks,protocol);
                    }else if(NetworkProtocol.QUIC==protocol){
                        Properties channelProperties = TCPChannelUtils.quicChannelProperty(ip,portServer);
                        channelProperties.setProperty("SINGLE_THREADED","ON");
                        networkClientInterface = new TCPQUICClientChannel(channelProperties,opCallbacks,protocol);
                    }else{
                        throw new RuntimeException("UNKNOWN NETWORK_PROTOCOL: "+protocol);
                    }
                    System.out.println("RUNNING PROTOCOL: "+protocol);
                    idCounter = new AtomicInteger(0);
                    servers = new LinkedList<>();

                    String[] hosts = getProperties().getProperty("hosts").split(",");
                    List<ChannelFuture> connectFutures = new LinkedList<>();

                    for (String s : hosts) {
                        String add [] = s.split(":");
                        InetAddress addr = InetAddress.getByName(add[0]);
                        int port = Integer.parseInt(add[1]);
                        Host host = new Host(addr,port);
                        CompletableFuture<String> waiting = new CompletableFuture<>();
                        String id = networkClientInterface.openMessageConnection(host, (short) 2,waiting);
                        waiting.get();
                        servers.add(id);
                        opCallbacks.put(id, new ConcurrentHashMap<>());
                    }
                    System.out.println("Connected to all servers! "+protocol);
                    //END ONCE
                }
                //int threadId = initCounter.incrementAndGet();
                //int randIdx = threadId % servers.size();
                //threadServer.set(servers.get(randIdx));
            }
        } catch (UnknownHostException | InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
        try {
            int id = idCounter.incrementAndGet();
            RequestMessage requestMessage = new RequestMessage(id, RequestMessage.READ, key, new byte[0]);
            return executeOperation(requestMessage);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return Status.ERROR;
        }
    }

    @Override
    public Status insert(String table, String key, Map<String, ByteIterator> values) {
        int id = idCounter.incrementAndGet();
        try {
            byte[] value = values.values().iterator().next().toArray();
            RequestMessage requestMessage = new RequestMessage(id, RequestMessage.WRITE, key, value);
            return executeOperation(requestMessage);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return Status.ERROR;
        }
    }

    private Status executeOperation(RequestMessage requestMessage) throws InterruptedException, ExecutionException {
        int randIdx = ((int) requestMessage.getOpId()) % servers.size();
        //threadServer.set(servers.get(randIdx));
        String channel = servers.get(randIdx);
        //System.out.println("EXECUTING "+channel);
        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        opCallbacks.get(channel).put(requestMessage.getOpId(), future);
        try {
            networkClientInterface.send(requestMessage,channel);
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return Status.OK;
        } catch (TimeoutException ex) {
            System.out.println("Op Timed out..." + channel + " " + requestMessage.getOpId());
            System.exit(1);
            return Status.SERVICE_UNAVAILABLE;
        } catch (Exception e){
            e.printStackTrace();
            System.exit(1);
            return Status.ERROR;
        }
    }

    @Override
    public Status scan(String t, String sK, int rC, Set<String> f, Vector<HashMap<String, ByteIterator>> res) {
        throw new IllegalStateException();
    }

    @Override
    public Status update(String table, String key, Map<String, ByteIterator> values) {
        throw new IllegalStateException();
    }

    @Override
    public Status delete(String table, String key) {
        throw new IllegalStateException();
    }

}
