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
public class PromiseMessage extends ProtoMessage {

    public final static short MSG_ID = 402;

    private final int instance;
    private final ProposalID proposalID;
    private final ProposalID previousID;
    private final TypedPayload acceptedValue;

    public PromiseMessage(int instance, ProposalID proposalID, ProposalID previousID, TypedPayload acceptedValue) {
        super(MSG_ID);
        this.instance = instance;
        this.proposalID = proposalID;
        this.previousID = previousID;
        this.acceptedValue = acceptedValue;
    }
    public static ISerializer<PromiseMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(PromiseMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.instance);
            ProposalID.serializer.serialize(msg.proposalID, out);
            if (msg.previousID != null) {
                out.writeBoolean(true);
                ProposalID.serializer.serialize(msg.previousID, out);
                TypedPayload.serializer.serialize(msg.acceptedValue, out);
            } else {
                out.writeBoolean(false);
            }
        }

        @Override
        public PromiseMessage deserialize(ByteBuf in) throws IOException {
            int instance = in.readInt();
            ProposalID proposalID = ProposalID.serializer.deserialize(in);
            if (in.readBoolean()) {
                ProposalID previousID = ProposalID.serializer.deserialize(in);
                TypedPayload acceptedValue = TypedPayload.serializer.deserialize(in);
                return new PromiseMessage(instance, proposalID, previousID, acceptedValue);
            } else {
                return new PromiseMessage(instance, proposalID, null, null);
            }
        }
    };
}
