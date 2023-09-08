package org.fileStreaming.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public class IHaveFile extends ProtoMessage {
    public static final short ID = 203;

    public final long fileLength;
    public final String fileName;

    public final Host host;

    public IHaveFile(long fileLength,String fileName, Host host) {
        super(ID);
        this.fileLength = fileLength;
        this.fileName = fileName;
        this.host = host;
    }

    public static ISerializer<IHaveFile> serializer = new ISerializer<IHaveFile>() {
        @Override
        public void serialize(IHaveFile iHaveFile, ByteBuf out) throws IOException {
            Host.serializer.serialize(iHaveFile.host,out);
            out.writeLong(iHaveFile.fileLength);
            byte [] gg = iHaveFile.fileName.getBytes();
            out.writeInt(gg.length);
            out.writeBytes(gg);
        }

        @Override
        public IHaveFile deserialize(ByteBuf in) throws IOException {
            Host host1 = Host.serializer.deserialize(in);
            long fileLength = in.readLong();
            int strLen = in.readInt();
            byte [] file = new byte[strLen];
            in.readBytes(file,0,strLen);
            String fileName = new String(file);
            return new IHaveFile(fileLength,fileName,host1);
        }
    };
}
