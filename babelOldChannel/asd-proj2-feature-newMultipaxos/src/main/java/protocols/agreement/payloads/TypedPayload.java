package protocols.agreement.payloads;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public record TypedPayload(Type type, Payload payload) {
    public enum Type {
        ADD_REPLICA((byte) 0),
        REMOVE_REPLICA((byte) 1),
        PROPOSE((byte) 2);

        final byte id;
        Type(byte id) {
            this.id = id;
        }
        static Type fromByte(byte id) {
            return switch (id) {
                case 0 -> ADD_REPLICA;
                case 1 -> REMOVE_REPLICA;
                case 2 -> PROPOSE;
                default -> throw new IllegalArgumentException("Unexpected value: " + id);
            };
        }
    }

    public static ISerializer<TypedPayload> serializer = new ISerializer<>() {
        @Override
        public void serialize(TypedPayload typedPayload, ByteBuf out) throws IOException {
            out.writeByte(typedPayload.type.id);
            if (typedPayload.payload instanceof Membership membership) {
                Membership.serializer.serialize(membership, out);
            } else if (typedPayload.payload instanceof Proposal proposal) {
                Proposal.serializer.serialize(proposal, out);
            }
        }

        @Override
        public TypedPayload deserialize(ByteBuf in) throws IOException {
            Type type = Type.fromByte(in.readByte());
            Payload payload = switch (type) {
                case ADD_REPLICA, REMOVE_REPLICA -> Membership.serializer.deserialize(in);
                case PROPOSE -> Proposal.serializer.deserialize(in);
            };
            return new TypedPayload(type, payload);
        }
    };
}
