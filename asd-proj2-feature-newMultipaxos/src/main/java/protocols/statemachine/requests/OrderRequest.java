package protocols.statemachine.requests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

import java.util.UUID;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class OrderRequest extends ProtoRequest {

    public static final short REQUEST_ID = 201;

    UUID opId;
    @ToString.Exclude
    byte[] operation;

    public OrderRequest(UUID opId, byte[] operation) {
        super(REQUEST_ID);
        this.opId = opId;
        this.operation = operation;
    }
}
