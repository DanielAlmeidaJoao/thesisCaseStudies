package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import protocols.agreement.ProposalID;
import protocols.agreement.payloads.TypedPayload;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
public class ProposeMessage extends ProtoMessage {

    public final static short MSG_ID = 403;

    private final int instance;
    private final ProposalID proposalID;
    private final TypedPayload proposalValue;

    public ProposeMessage(int instance, ProposalID proposalID, TypedPayload proposalValue) {
        super(MSG_ID);
        this.instance = instance;
        this.proposalID = proposalID;
        this.proposalValue = proposalValue;
    }
    public static ISerializer<ProposeMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ProposeMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.instance);
            ProposalID.serializer.serialize(msg.proposalID, out);
            TypedPayload.serializer.serialize(msg.proposalValue, out);
        }

        @Override
        public ProposeMessage deserialize(ByteBuf in) throws IOException {
            int instance = in.readInt();
            ProposalID proposalID = ProposalID.serializer.deserialize(in);
            TypedPayload proposalValue = TypedPayload.serializer.deserialize(in);
            return new ProposeMessage(instance, proposalID, proposalValue);
        }
    };
}
