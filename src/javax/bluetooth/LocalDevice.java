package javax.bluetooth;

/**
 * The local Bluetooth device.
 *
 * <p>There is no radio on a desktop, so {@link #getLocalDevice()} reports the
 * absence honestly by throwing {@link BluetoothStateException}. Applications
 * that probe for Bluetooth handle that path; ones that assume it exists will
 * report a clear error instead of failing in a confusing place.</p>
 */
public class LocalDevice {

    public static final String BLUETOOTH_RADIO = "bluetooth.radio";
    public static final String BLUETOOTH_FRIENDLY_NAME = "bluetooth.friendly_name";
    public static final String BLUETOOTH_VERSION = "bluetooth.version";
    public static final String BLUETOOTH_DEVICE_ADDRESS = "bluetooth.device_address";

    private static LocalDevice instance;

    private final String address = "000000000000";

    protected LocalDevice() {
    }

    public static LocalDevice getLocalDevice() throws BluetoothStateException {
        throw new BluetoothStateException("bluetooth is not available on this device");
    }

    public static String getProperty(String property) {
        switch (property) {
            case BLUETOOTH_RADIO:
                return "off";
            case BLUETOOTH_FRIENDLY_NAME:
                return "J2PC desktop";
            case BLUETOOTH_VERSION:
                return "1.1";
            case BLUETOOTH_DEVICE_ADDRESS:
                return "000000000000";
            default:
                return null;
        }
    }

    public DiscoveryAgent getDiscoveryAgent() {
        return new DiscoveryAgent();
    }

    public String getFriendlyName() {
        return "J2PC desktop";
    }

    public DeviceClass getDeviceClass() {
        return new DeviceClass(0, 0, 0);
    }

    public int getAccessCode() {
        return DiscoveryAgent.GIAC;
    }

    public ServiceRecord getRecord(int[] attrIDs) {
        return null;
    }

    public boolean setDiscoverable(int mode) throws BluetoothStateException {
        throw new BluetoothStateException("bluetooth is not available on this device");
    }

    public RemoteDevice getRemoteDevice() {
        return null;
    }

    public String getBluetoothAddress() {
        return address;
    }
}
