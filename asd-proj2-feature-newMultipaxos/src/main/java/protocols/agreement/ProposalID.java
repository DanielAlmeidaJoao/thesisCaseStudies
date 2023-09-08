package protocols.agreement;

import io.netty.buffer.ByteBuf;
import lombok.*;
import protocols.agreement.messages.AcceptMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.UUID;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ProposalID implements Comparable<ProposalID> {

    @Setter
    private int number;
    private final Host host;

    public void increment() {
        this.number += 1;
    }

    public boolean isGreaterThan(ProposalID rhs) {
        return this.compareTo(rhs) > 0;
    }

    public boolean isLessThan(ProposalID rhs) {
        return this.compareTo(rhs) < 0;
    }

    @Override
    public int compareTo(ProposalID rhs) {
        if (this.equals(rhs))
            return 0;
        if (number < rhs.number || (number == rhs.number && host.compareTo(rhs.host) < 0))
            return -1;
        return 1;
    }

    public static ISerializer<ProposalID> serializer = new ISerializer<>() {
        @Override
        public void serialize(ProposalID msg, ByteBuf out) throws IOException {
            out.writeInt(msg.number);
            Host.serializer.serialize(msg.host, out);
        }

        @Override
        public ProposalID deserialize(ByteBuf in) throws IOException {
            int number = in.readInt();
            var host = Host.serializer.deserialize(in);
            return new ProposalID(number, host);
        }
    };
}
