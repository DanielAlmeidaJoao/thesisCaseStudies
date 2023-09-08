package fileStreamingProto;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

public class AskFileRequest  extends ProtoRequest {

    public static final short REQUEST_ID = 351;

    private final int fileID;
    public final Host fileOwner;

    public AskFileRequest(int fileID, Host host) {
        super(REQUEST_ID);
        this.fileID = fileID;
        this.fileOwner = host;
    }

    public int getFileID() {
        return fileID;
    }
}
