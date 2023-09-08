package protocols.agreement.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import protocols.agreement.payloads.TypedPayload;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

import java.util.UUID;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ProposeRequest extends ProtoRequest {

    public static final short REQUEST_ID = 101;

    int instance;
    TypedPayload payload;

    public ProposeRequest(int instance, TypedPayload payload) {
        super(REQUEST_ID);
        this.instance = instance;
        this.payload = payload;
    }
}
