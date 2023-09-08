package org.fileStreaming.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public class BabelStreamDeliveryEvent  extends ProtoMessage  {

    public static final short ID = 206;

    public final int dataLength;
    public final byte [] data;

    public BabelStreamDeliveryEvent(byte [] data, int dataLength) {
        super(ID);
        this.data = data;
        this.dataLength=dataLength;
    }

    public static ISerializer<BabelStreamDeliveryEvent> serializer = new ISerializer<BabelStreamDeliveryEvent>() {
        @Override
        public void serialize(BabelStreamDeliveryEvent iHaveFile, ByteBuf out) throws IOException {
            //Host.serializer.serialize(iHaveFile.host,out);
            out.writeInt(iHaveFile.dataLength);
            out.writeBytes(iHaveFile.data,0, iHaveFile.dataLength);
        }

        @Override
        public BabelStreamDeliveryEvent deserialize(ByteBuf in) throws IOException {
            //Host host1 = Host.serializer.deserialize(in);
            int dataLen = in.readInt();
            byte [] file = new byte[dataLen];
            in.readBytes(file,0,dataLen);
            return new BabelStreamDeliveryEvent(file,dataLen);
        }
    };
}
