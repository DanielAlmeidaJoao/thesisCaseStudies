package protocols.agreement.payloads;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public record Membership(Host host) implements Payload {

    public static ISerializer<Membership> serializer = new ISerializer<>() {
        @Override
        public void serialize(Membership membership, ByteBuf out) throws IOException {
            Host.serializer.serialize(membership.host, out);
        }

        @Override
        public Membership deserialize(ByteBuf in) throws IOException {
            var host = Host.serializer.deserialize(in);
            return new Membership(host);
        }
    };
}
