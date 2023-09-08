package dissemination.plumtree.utils;

import dissemination.plumtree.messages.IHaveMessage;
import pt.unl.fct.di.novasys.network.data.Host;

public class AddressedIHaveMessage {
    public IHaveMessage msg;
    public Host to;

    public AddressedIHaveMessage(IHaveMessage msg, Host to) {
        this.msg = msg;
        this.to = to;
    }
}
