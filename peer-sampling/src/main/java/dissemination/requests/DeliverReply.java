package dissemination.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class DeliverReply extends ProtoReply {

    public static final short REPLY_ID = 301;

    private final byte[] msg;
    public boolean broadCast;
    public int hash;

    public DeliverReply(byte[] msg) {
        super(REPLY_ID);
        this.msg = msg;
        broadCast = false;
    }

    public byte[] getMsg() {
        return msg;
    }
}
