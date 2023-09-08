package protocols.statemachine.messages;


import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class JoinRequestMessage extends ProtoMessage {
    public static final short MSG_ID = 290; //TODO

    public JoinRequestMessage() {
        super(MSG_ID);
    }
}
