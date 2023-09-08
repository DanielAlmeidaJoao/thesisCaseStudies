package network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;

public class HelperMethods {

    public static ByteBuf serialize(RequestMessage msg) throws IOException {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShort(msg.sourceProto);
        byteBuf.writeShort(msg.destProto);
        byteBuf.writeShort(msg.MSG_ID);
        RequestMessage.serializer.serialize(msg,byteBuf);

        return byteBuf;
    }

    public static ResponseMessage deserialize(ByteBuf byteBuf) throws IOException {
        short source = byteBuf.readShort();
        short dest = byteBuf.readShort();
        short id = byteBuf.readShort();
        ResponseMessage responseMessage = ResponseMessage.serializer.deserialize(byteBuf);
        byteBuf.discardReadBytes();
        return responseMessage;
    }
}
