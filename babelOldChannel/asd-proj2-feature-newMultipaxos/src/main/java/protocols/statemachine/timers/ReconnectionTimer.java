package protocols.statemachine.timers;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ReconnectionTimer extends ProtoTimer {
    public static final short TIMER_ID = 230;

    Host host;

    public ReconnectionTimer(Host host) {
        super(TIMER_ID);
        this.host = host;
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

}
