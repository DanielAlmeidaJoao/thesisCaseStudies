package protocols.app.messages;

import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class ResponseMessage extends ProtoMessage {

    public final static short MSG_ID = 302;

    long opId;
    byte[] data;

    public ResponseMessage(long opId, byte[] data) {
        super(MSG_ID);
        this.opId = opId;
        this.data = data;
    }

    public static ISerializer<ResponseMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ResponseMessage responseMsg, ByteBuf out) {
            out.writeLong(responseMsg.opId);
            out.writeInt(responseMsg.data.length);
            out.writeBytes(responseMsg.data);
        }

        @Override
        public ResponseMessage deserialize(ByteBuf in) {
            long opId = in.readLong();
            byte[] data = new byte[in.readInt()];
            in.readBytes(data);
            return new ResponseMessage(opId, data);
        }
    };
}
