package protocols.agreement.payloads;

import io.netty.buffer.ByteBuf;
import lombok.ToString;
import lombok.Value;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.UUID;

@Value
public class Proposal implements Payload {
    UUID opId;
    @ToString.Exclude
    byte[] operation;

    public static ISerializer<Proposal> serializer = new ISerializer<>() {
        @Override
        public void serialize(Proposal proposal, ByteBuf out) {
            out.writeLong(proposal.opId.getMostSignificantBits());
            out.writeLong(proposal.opId.getLeastSignificantBits());
            out.writeInt(proposal.operation.length);
            out.writeBytes(proposal.operation);
        }

        @Override
        public Proposal deserialize(ByteBuf in) {
            long firstLong = in.readLong();
            long secondLong = in.readLong();
            UUID opId = new UUID(firstLong, secondLong);
            byte[] operation = new byte[in.readInt()];
            in.readBytes(operation);
            return new Proposal(opId, operation);
        }
    };
}
