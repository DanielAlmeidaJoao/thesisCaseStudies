package protocols.agreement.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

import java.util.UUID;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ElectLeaderRequest extends ProtoRequest {

    public static final short REQUEST_ID = 104;

    public ElectLeaderRequest() {
        super(REQUEST_ID);
    }
}
