package javax.wireless.messaging;

import java.io.IOException;

/** Base interface of the wireless messages. */
public interface Message {

    String getAddress();

    void setAddress(String address);

    java.util.Date getTimestamp();
}
