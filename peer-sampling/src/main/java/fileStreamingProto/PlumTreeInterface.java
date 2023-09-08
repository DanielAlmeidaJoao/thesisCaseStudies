package fileStreamingProto;

import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

import java.io.IOException;
import java.util.Properties;

public interface PlumTreeInterface {

    void init(Properties props);

    short getProtoId();
}
