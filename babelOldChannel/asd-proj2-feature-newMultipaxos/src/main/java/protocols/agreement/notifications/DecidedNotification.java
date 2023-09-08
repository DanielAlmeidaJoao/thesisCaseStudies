package protocols.agreement.notifications;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import protocols.agreement.payloads.TypedPayload;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

import java.util.UUID;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class DecidedNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 101;

    int instance;
    TypedPayload payload;

    public DecidedNotification(int instance, TypedPayload payload) {
        super(NOTIFICATION_ID);
        this.instance = instance;
        this.payload = payload;
    }
}
