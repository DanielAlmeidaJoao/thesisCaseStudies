package protocols.statemachine.notifications;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.net.InetAddress;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class NewLeaderNotification extends ProtoNotification {

    public final static short NOTIFICATION_ID = 203;

    Host leader;

    public NewLeaderNotification(Host leader) {
        super(NOTIFICATION_ID);
        this.leader = leader;
    }

}
