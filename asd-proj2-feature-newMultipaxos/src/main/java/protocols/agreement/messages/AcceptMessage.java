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
public class AcceptMessage extends ProtoMessage {

    public final static short MSG_ID = 404;

    private final int instance;
    private final ProposalID proposalID;
    private final TypedPayload acceptedValue;

    public AcceptMessage(int instance, ProposalID proposalID, TypedPayload acceptedValue) {
        super(MSG_ID);
        this.instance = instance;
        this.proposalID = proposalID;
        this.acceptedValue = acceptedValue;
    }

    public static ISerializer<AcceptMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(AcceptMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.instance);
            ProposalID.serializer.serialize(msg.proposalID, out);
            TypedPayload.serializer.serialize(msg.acceptedValue, out);
        }

        @Override
        public AcceptMessage deserialize(ByteBuf in) throws IOException {
            int instance = in.readInt();
            ProposalID proposalID = ProposalID.serializer.deserialize(in);
            TypedPayload acceptedValue = TypedPayload.serializer.deserialize(in);
            return new AcceptMessage(instance, proposalID, acceptedValue);
        }
    };
}
