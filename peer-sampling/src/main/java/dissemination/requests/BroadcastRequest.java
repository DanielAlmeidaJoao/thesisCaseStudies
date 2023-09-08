package dissemination.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class BroadcastRequest extends ProtoRequest {

    public static final short REQUEST_ID = 301;

    private final byte[] msg;
    public final int mid;

    public BroadcastRequest(byte[] msg, int mid) {
        super(REQUEST_ID);
        this.msg = msg;
        this.mid = mid;
    }

    public byte[] getMsg() {
        return msg;
    }
}
