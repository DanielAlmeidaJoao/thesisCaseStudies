package org.fileStreaming.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class BroadcastTimer extends ProtoTimer {
    public static final short TimerCode = 301;

    public BroadcastTimer() {
        super(BroadcastTimer.TimerCode);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }



}
