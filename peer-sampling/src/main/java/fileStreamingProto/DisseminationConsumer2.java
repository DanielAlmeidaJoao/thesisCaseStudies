package fileStreamingProto;

import dissemination.requests.BroadcastRequest;
import dissemination.requests.DeliverReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Random;

public class DisseminationConsumer2 extends GenericProtocol implements DisseminationInterface{

    public static class Timer extends ProtoTimer {

        public Timer(short id) {
            super(id);
        }

        @Override
        public ProtoTimer clone() {
            return this;
        }
    }

    private static final Logger logger = LogManager.getLogger(DisseminationConsumer2.class);


    public static final String PROTO_NAME = "DisseminationConsumer";
    public static final short PROTO_ID = 11;

    private static final short timerid = 1;

    private int seqNum = 0;
    private final Host self;
    private final short disseminationProto;

    private final int payloadSize;
    private final Random rnd;
    private final int maxMsgs;

    public DisseminationConsumer2(short disseminationProto, Properties properties) throws HandlerRegistrationException, UnknownHostException {
        super(PROTO_NAME, PROTO_ID);
        String address = properties.getProperty("address");
        String port = properties.getProperty("port");
        this.self = new Host(InetAddress.getByName(address), Short.parseShort(port));;
        this.disseminationProto = disseminationProto;

        this.payloadSize = Integer.parseInt(properties.getProperty("payloadSize", "0"));
        this.rnd = new Random();

        this.maxMsgs = Integer.parseInt(properties.getProperty("maxMsgs", "0"));

        registerReplyHandler(DeliverReply.REPLY_ID, this::uponDeliver);

        registerTimerHandler(timerid, this::uponSendMsg);
    }

    private void uponDeliver(DeliverReply reply, short sourceProto) {
        //byte[] msg;
        /**
        if(payloadSize > 0) {
            msg = new byte[reply.getMsg().length - payloadSize];
            System.arraycopy(reply.getMsg(), payloadSize-1, msg, 0, reply.getMsg().length - payloadSize);
        } else
            msg = reply.getMsg();
         **/
        //System.out.println("RECEIVED REPLY: "+new String(reply.getMsg())+" IHAVE: "+IHAVE_THE_FILE);
        if(IHAVE_THE_FILE || reply.broadCast){
            logger.info("RECEIVED FILE {} TIMERON",timerON);
            if(timerON==-1){
                timerON = setupTimer(new Timer(timerid),disseminationStart);
            }
            logger.info("Received: FROM {} ",sourceProto);
        }else if(fileOwner==null && !reply.broadCast){
            try{
                String [] h = new String(reply.getMsg()).split(":");
                fileOwner = new Host(InetAddress.getByName(h[0]),Integer.parseInt(h[1]));
                sendRequest(new AskFileRequest(0,fileOwner),disseminationProto);
                logger.info("SENT ASKFILE REQUEST");
            }catch (Exception e){
                e.printStackTrace();
                System.exit(1);
            }
            logger.info("Received: {} FROM {} ",new String(reply.getMsg()),sourceProto);

        }
    }

    private void uponSendMsg(Timer timer, long timerId) {
        //System.out.println("TIMER TRIGGERED. SENDING BROADCAST REQUEST");
        if(maxMsgs == 0 || seqNum < maxMsgs) {
            String tosend = String.format("%s:%s",self.getAddress().getHostAddress(),self.getPort());
            /**
            byte[] toSend;
            if (payloadSize > 0) {
                byte[] payload = new byte[payloadSize];
                rnd.nextBytes(payload);
                toSend = new byte[(payloadSize + tosend.getBytes().length)];
                System.arraycopy(payload, 0, toSend, 0, payloadSize);
                System.arraycopy(tosend.getBytes(), 0, toSend, payloadSize - 1, tosend.getBytes().length);
            } else
                toSend = tosend.getBytes();
            **/
            new Exception("ADD MID CALCULATION").printStackTrace();
            sendRequest(new BroadcastRequest(tosend.getBytes(),-1), disseminationProto);
            logger.info("Sent: {}", tosend);
        }
    }
    private boolean IHAVE_THE_FILE;
    private long timerON = -1;
    int disseminationPeriod;
    int disseminationStart;
    Host fileOwner;

    @Override
    public void init(Properties props) {
        disseminationPeriod = Integer.parseInt(props.getProperty("disseminationPeriod", "2000"));
        disseminationStart = Integer.parseInt(props.getProperty("disseminationStart", "2000"));
        if (!props.containsKey("contact")){
            IHAVE_THE_FILE = true;
            timerON = setupTimer(new Timer(timerid),disseminationStart);
        }else{
            IHAVE_THE_FILE = false;
        }
    }
    private void sendFile(String connectionID){

    }
}
