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
public class PrepareMessage extends ProtoMessage {

    public final static short MSG_ID = 401;

    private final int instance;
    private final ProposalID proposalID;

    public PrepareMessage(int instance, ProposalID proposalID) {
        super(MSG_ID);
        this.instance = instance;
        this.proposalID = proposalID;
    }
    public static ISerializer<PrepareMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(PrepareMessage msg, ByteBuf out) throws IOException {
            out.writeInt(msg.instance);
            ProposalID.serializer.serialize(msg.proposalID, out);
        }

        @Override
        public PrepareMessage deserialize(ByteBuf in) throws IOException {
            int instance = in.readInt();
            ProposalID proposalID = ProposalID.serializer.deserialize(in);
            return new PrepareMessage(instance, proposalID);
        }
    };
}
