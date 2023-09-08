package protocols.statemachine.notifications;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ChannelReadyNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 201;

    int channelId;
    Host myself;

    public ChannelReadyNotification(int channelId, Host myself) {
        super(NOTIFICATION_ID);
        this.channelId = channelId;
        this.myself = myself;
    }
}
