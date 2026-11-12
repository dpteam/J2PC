package javax.bluetooth;

/** A discovered or known remote device. */
public class RemoteDevice {

    private final String address;
    private String friendlyName;

    protected RemoteDevice(String address) {
        this.address = address == null ? "000000000000" : address;
    }

    public static RemoteDevice getRemoteDevice(String address) throws java.io.IOException {
        return new RemoteDevice(address);
    }

    public boolean isTrustedDevice() {
        return false;
    }

    public String getFriendlyName(boolean alwaysAsk) throws java.io.IOException {
        return friendlyName == null ? address : friendlyName;
    }

    void setFriendlyName(String name) {
        this.friendlyName = name;
    }

    public boolean authenticate() throws java.io.IOException {
        throw new java.io.IOException("bluetooth is not available on this device");
    }

    public boolean authorize(String connection) throws java.io.IOException {
        throw new java.io.IOException("bluetooth is not available on this device");
    }

    public boolean encrypt(boolean on, String connection) throws java.io.IOException {
        throw new java.io.IOException("bluetooth is not available on this device");
    }

    public boolean isAuthenticated() {
        return false;
    }

    public boolean isAuthorized(String connection) {
        return false;
    }

    public boolean isEncrypted() {
        return false;
    }

    public String getBluetoothAddress() {
        return address;
    }

    public static boolean isSameDevice(RemoteDevice a, RemoteDevice b) {
        return a != null && b != null && a.address.equals(b.address);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RemoteDevice && address.equals(((RemoteDevice) o).address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}
