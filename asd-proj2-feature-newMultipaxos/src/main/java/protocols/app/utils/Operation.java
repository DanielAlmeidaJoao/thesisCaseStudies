package protocols.app.utils;

import lombok.ToString;
import lombok.Value;

import java.io.*;
import java.util.Objects;

@Value
public class Operation {
    byte opType;
    String key;
    @ToString.Exclude
    byte[] data;

    public byte[] toByteArray() throws IOException {
        try (var baos = new ByteArrayOutputStream();
             var dos = new DataOutputStream(baos)) {
            dos.writeByte(opType);
            dos.writeUTF(key);
            dos.writeInt(data.length);
            dos.write(data);
            return baos.toByteArray();
        }
    }

    public static Operation fromByteArray(byte[] data) throws IOException {
        try (var bais = new ByteArrayInputStream(data);
             var dis = new DataInputStream(bais)) {
            byte opType = dis.readByte();
            String key = dis.readUTF();
            byte[] opData = new byte[dis.readInt()];
            dis.read(opData, 0, opData.length);
            return new Operation(opType, key, opData);
        }
    }
}
