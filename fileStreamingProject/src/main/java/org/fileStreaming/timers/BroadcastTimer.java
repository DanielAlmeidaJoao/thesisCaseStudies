package org.fileStreaming.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

public class BroadcastTimer extends ProtoTimer {
    public static final short TimerCode = 301;
    public String conId;
    public Host host;

    public BroadcastTimer() {
        super(BroadcastTimer.TimerCode);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }



}
