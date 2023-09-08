package protocols.statemachine.notifications;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

import java.util.UUID;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ExecuteNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 202;

    UUID opId;
    @ToString.Exclude
    byte[] operation;

    public ExecuteNotification(UUID opId, byte[] operation) {
        super(NOTIFICATION_ID);
        this.opId = opId;
        this.operation = operation;
    }
}
