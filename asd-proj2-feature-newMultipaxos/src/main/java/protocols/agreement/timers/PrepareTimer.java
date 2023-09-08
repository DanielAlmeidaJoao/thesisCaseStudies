package protocols.agreement.timers;

import lombok.Getter;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class PrepareTimer extends ProtoTimer {
    public static final short TIMER_ID = 480;

    @Getter
    private final int instance;

    public PrepareTimer(int instance) {
        super(TIMER_ID);
        this.instance = instance;
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
