package protocols.app.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class CurrentStateRequest extends ProtoRequest {

    public static final short REQUEST_ID = 301;

    int instance;

    public CurrentStateRequest(int instance) {
        super(REQUEST_ID);
        this.instance = instance;
    }
}
