package protocols.agreement.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class RemoveReplicaRequest extends ProtoRequest {

    public static final short REQUEST_ID = 102;

    int instance;
    Host replica;

    public RemoveReplicaRequest(int instance, Host replica) {
        super(REQUEST_ID);
        this.instance = instance;
        this.replica = replica;
    }
}
