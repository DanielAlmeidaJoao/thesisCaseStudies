package protocols.app.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InstallStateRequest extends ProtoRequest {

    public static final short REQUEST_ID = 302;

    byte[] state;

    public InstallStateRequest(byte[] state) {
        super(REQUEST_ID);
        this.state = state;
    }

    @Override
    public String toString() {
        return "InstallStateRequest{" +
                "number of bytes=" + state.length +
                '}';
    }
}
