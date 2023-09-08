import network.RequestMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface NetworkClientInterface {

    String openMessageConnection(Host peer, short proto, CompletableFuture<String> waiting);
    void send(RequestMessage requestMessage, String conId) throws IOException;
    void metrics();
}
