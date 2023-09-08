package protocols.app.messages;

import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.nio.charset.StandardCharsets;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class RequestMessage extends ProtoMessage {

    public final static short MSG_ID = 301;

    public final static byte READ = 0;
    public final static byte WRITE = 1;

    long opId;
    byte opType;
    String key;
    @ToString.Exclude
    byte[] data;

    public RequestMessage(long opId, byte opType, String key, byte[] data) {
        super(MSG_ID);
        this.opId = opId;
        this.opType = opType;
        this.key = key;
        this.data = data;
    }

    public static ISerializer<RequestMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(RequestMessage requestMessage, ByteBuf out) {
            out.writeLong(requestMessage.opId);
            out.writeByte(requestMessage.opType);
            byte[] keyBytes = requestMessage.key.getBytes(StandardCharsets.UTF_8);
            out.writeInt(keyBytes.length);
            out.writeBytes(keyBytes);
            out.writeInt(requestMessage.data.length);
            out.writeBytes(requestMessage.data);
        }

        @Override
        public RequestMessage deserialize(ByteBuf in) {
            long opId = in.readLong();
            byte opType = in.readByte();
            byte[] keyBytes = new byte[in.readInt()];
            in.readBytes(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            byte[] data = new byte[in.readInt()];
            in.readBytes(data);
            return new RequestMessage(opId, opType, key, data);
        }
    };
}
