package protocols.app.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CurrentStateReply extends ProtoReply {

    public static final short REQUEST_ID = 301;

    int instance;
    byte[] state;

    public CurrentStateReply(int instance, byte[] state) {
        super(REQUEST_ID);
        this.instance = instance;
        this.state = state;
    }

    @Override
    public String toString() {
        return "CurrentStateReply{" +
                "instance=" + instance +
                "number of bytes=" + state.length +
                '}';
    }
}
