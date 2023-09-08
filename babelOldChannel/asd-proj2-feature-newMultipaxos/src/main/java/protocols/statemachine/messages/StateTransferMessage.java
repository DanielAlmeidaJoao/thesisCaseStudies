package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Getter
public class StateTransferMessage extends ProtoMessage {

    public final static short MSG_ID = 291;


    private final int instance;
    private final byte[] state;
    private final Set<Host> membership;

    public StateTransferMessage(int instance, byte[] state, Set<Host> membership) {
        super(MSG_ID);
        this.instance = instance;
        this.state = state;
        this.membership = membership;
    }

    public static ISerializer<StateTransferMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(StateTransferMessage currentStateReply, ByteBuf out) throws IOException {
            out.writeInt(currentStateReply.instance);
            out.writeInt(currentStateReply.state.length);
            out.writeBytes(currentStateReply.state);
            out.writeInt(currentStateReply.membership.size());
            for (Host h : currentStateReply.membership) {
                Host.serializer.serialize(h, out);
            }
        }

        @Override
        public StateTransferMessage deserialize(ByteBuf in) throws IOException {
            int instance = in.readInt();
            int stateLength = in.readInt();
            byte[] state = new byte[stateLength];
            in.readBytes(state);
            int size = in.readInt();
            Set<Host> membership = new HashSet<>();
            for (int i = 0; i < size; i++) {
                membership.add(Host.serializer.deserialize(in));
            }
            return new StateTransferMessage(instance, state, membership);

        }
    };
}
