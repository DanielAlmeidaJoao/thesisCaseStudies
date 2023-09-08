package fileStreamingProto;

import java.util.Properties;

public interface HyparViewInterface {
    void init(Properties props);

    int getChannel();

    int connectedPeers();
}
