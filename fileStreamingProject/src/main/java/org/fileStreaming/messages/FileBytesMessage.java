package org.fileStreaming.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class FileBytesMessage extends ProtoMessage {

    public static final short ID = 206;

    public final byte [] data;
    public final int dataLength;

    public FileBytesMessage(byte [] data, int dataLength) {
        super(ID);
        this.data = data;
        this.dataLength=dataLength;
    }

    public static ISerializer<FileBytesMessage> serializer = new ISerializer<FileBytesMessage>() {
        @Override
        public void serialize(FileBytesMessage iHaveFile, ByteBuf out) throws IOException {
            //Host.serializer.serialize(iHaveFile.host,out);
            out.writeInt(iHaveFile.dataLength);
            out.writeBytes(iHaveFile.data,0, iHaveFile.dataLength);
        }

        @Override
        public FileBytesMessage deserialize(ByteBuf in) throws IOException {
            //Host host1 = Host.serializer.deserialize(in);
            int dataLen = in.readInt();
            byte [] file = new byte[dataLen];
            in.readBytes(file,0,dataLen);
            return new FileBytesMessage(file,dataLen);
        }
    };
}
