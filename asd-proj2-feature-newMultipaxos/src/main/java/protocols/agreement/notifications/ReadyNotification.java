package protocols.agreement.notifications;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ReadyNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 102;

    int readyInstance;
    Set<Host> membership;

    public ReadyNotification(int readyInstance, Set<Host> membership) {
        super(NOTIFICATION_ID);
        this.readyInstance = readyInstance;
        this.membership = membership;
    }
}
