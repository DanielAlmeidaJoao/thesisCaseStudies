package dissemination;

import dissemination.plumtree.utils.HashProducer;
import dissemination.requests.BroadcastRequest;
import dissemination.requests.DeliverReply;
import fileStreamingProto.DisseminationInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Random;

public class DisseminationConsumer extends GenericProtocol implements DisseminationInterface {

    public static class Timer extends ProtoTimer {

        public Timer(short id) {
            super(id);
        }

        @Override
        public ProtoTimer clone() {
            return this;
        }
    }

    private static final Logger logger = LogManager.getLogger(DisseminationConsumer.class);


    public static final String PROTO_NAME = "DisseminationConsumer";
    public static final short PROTO_ID = 11;

    private static final short timerid = 1;

    private int seqNum = 0;
    private final Host self;
    private final short disseminationProto;

    private final int payloadSize;
    private final Random rnd;
    private final int maxMsgs;

    private final HashProducer hashProducer;


    public DisseminationConsumer(short disseminationProto, Properties properties) throws HandlerRegistrationException, UnknownHostException {
        super(PROTO_NAME, PROTO_ID);
        String address = properties.getProperty("address");
        String port = properties.getProperty("port");
        this.self = new Host(InetAddress.getByName(address), Short.parseShort(port));;
        this.disseminationProto = disseminationProto;

        this.payloadSize = Integer.parseInt(properties.getProperty("payloadSize", "0"));
        this.rnd = new Random();

        this.hashProducer = new HashProducer(self);

        this.maxMsgs = Integer.parseInt(properties.getProperty("maxMsgs", "0"));

        registerReplyHandler(DeliverReply.REPLY_ID, this::uponDeliver);

        registerTimerHandler(timerid, this::uponSendMsg);
    }

    private void uponDeliver(DeliverReply reply, short sourceProto) {
        long receicedTime = System.currentTimeMillis();
        logger.info("Received: {}:{}",reply.hash,receicedTime);
        byte[] msg;
        if(payloadSize > 0) {
            msg = new byte[reply.getMsg().length - payloadSize];
            System.arraycopy(reply.getMsg(), payloadSize-1, msg, 0, reply.getMsg().length - payloadSize);
        } else
            msg = reply.getMsg();


    }

    private void uponSendMsg(Timer timer, long timerId) {
        if(maxMsgs == 0 || seqNum < maxMsgs) {
            String tosend = String.format("Hello from %s seq num: %d", self, seqNum++);
            byte[] toSend;
            if (payloadSize > 0) {
                byte[] payload = new byte[payloadSize];
                rnd.nextBytes(payload);
                toSend = new byte[(payloadSize + tosend.getBytes().length)];
                System.arraycopy(payload, 0, toSend, 0, payloadSize);
                System.arraycopy(tosend.getBytes(), 0, toSend, payloadSize - 1, tosend.getBytes().length);
            } else
                toSend = tosend.getBytes();

            int mid = hashProducer.hash(toSend);
            long sentTime = System.currentTimeMillis();
            long disseminationTimeInSecond = seqNum;
            sendRequest(new BroadcastRequest(toSend,mid),disseminationProto);
            logger.info("Sent: {}:{}:{}",mid,sentTime,disseminationTimeInSecond);
        }
    }

    @Override
    public void init(Properties props) {
        int disseminationPeriod = Integer.parseInt(props.getProperty("disseminationPeriod", "2000"));
        int disseminationStart = Integer.parseInt(props.getProperty("disseminationStart", "2000"));
        setupPeriodicTimer(new Timer(timerid), disseminationStart, disseminationPeriod);
    }
}
