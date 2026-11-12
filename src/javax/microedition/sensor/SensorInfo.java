package javax.microedition.sensor;

/** Metadata of a sensor. */
public interface SensorInfo {

    int CONNECT_AND_READ = 1;
    int CONTEXT_TYPE_AMBIENT = 1;
    int CONTEXT_TYPE_DEVICE = 2;
    int CONTEXT_TYPE_USER = 3;
    int CONTEXT_TYPE_NEAR = 4;

    ChannelInfo[] getChannelInfos();

    int getContextType();

    String getDescription();

    int getMaxBufferSize();

    String getModel();

    int getMode();

    String getQuantity();

    String getUrl();

    String getVendor();

    int getProperty(int index);

    boolean isAvailabilityPushSupported();

    boolean isAvailable();

    boolean isConditionPushSupported();
}
