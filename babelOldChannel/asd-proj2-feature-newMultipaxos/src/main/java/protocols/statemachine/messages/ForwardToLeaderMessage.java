package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import protocols.agreement.payloads.TypedPayload;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
public class ForwardToLeaderMessage extends ProtoMessage {

    public final static short MSG_ID = 220;

    private final TypedPayload forwardedValue;

    public ForwardToLeaderMessage(TypedPayload forwardedValue) {
        super(MSG_ID);
        this.forwardedValue = forwardedValue;
    }

    public static ISerializer<ForwardToLeaderMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ForwardToLeaderMessage msg, ByteBuf out) throws IOException {
            TypedPayload.serializer.serialize(msg.forwardedValue, out);
        }

        @Override
        public ForwardToLeaderMessage deserialize(ByteBuf in) throws IOException {
            TypedPayload forwardedValue = TypedPayload.serializer.deserialize(in);
            return new ForwardToLeaderMessage(forwardedValue);
        }
    };
}
