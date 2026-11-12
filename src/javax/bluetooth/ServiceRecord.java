package javax.bluetooth;

/** Service record published on a device. */
public class ServiceRecord {

    private final String url;

    ServiceRecord(String url) {
        this.url = url;
    }

    public String getConnectionURL(int requiredSecurity, boolean mustBeMaster) {
        return url;
    }

    public void setDeviceServiceClasses(int classes) {
    }

    public void setAttributeValue(int attrID, DataElement value) {
    }

    public DataElement getAttributeValue(int attrID) {
        return null;
    }

    public RemoteDevice getHostDevice() {
        return null;
    }

    public boolean populateRecord(int[] attrIDs) throws java.io.IOException {
        return false;
    }

    public int[] getAttributeIDs() {
        return new int[0];
    }
}
