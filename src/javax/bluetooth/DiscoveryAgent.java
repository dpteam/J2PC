package javax.bluetooth;

import java.io.IOException;

/**
 * Device and service discovery.
 *
 * <p>The desktop emulation has no radio, so discovery completes immediately with
 * no results instead of failing. Games that treat "no devices found" as a normal
 * state keep working; games that require a peer degrade gracefully.</p>
 */
public class DiscoveryAgent {

    public static final int NOT_DISCOVERABLE = 0;
    public static final int GIAC = 0x9E8B33;
    public static final int LIAC = 0x9E8B00;
    public static final int CACHED = 0x00;
    public static final int PREKNOWN = 0x01;

    protected DiscoveryAgent() {
    }

    public RemoteDevice[] retrieveDevices(int option) {
        return new RemoteDevice[0];
    }

    public boolean startInquiry(int accessCode, DiscoveryListener listener) throws BluetoothStateException {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        // Nothing to discover: report completion on a separate thread so the
        // caller sees the same ordering it would see on a real device.
        Thread t = new Thread(() -> listener.inquiryCompleted(DiscoveryListener.INQUIRY_COMPLETED));
        t.setDaemon(true);
        t.start();
        return true;
    }

    public boolean cancelInquiry(DiscoveryListener listener) {
        return false;
    }

    public int searchServices(int[] attrSet, UUID[] uuidSet, RemoteDevice btDev,
                              DiscoveryListener discListener) throws BluetoothStateException {
        if (discListener == null) {
            throw new NullPointerException("discListener");
        }
        Thread t = new Thread(() -> {
            discListener.servicesDiscovered(0, new ServiceRecord[0]);
            discListener.serviceSearchCompleted(0, DiscoveryListener.SERVICE_SEARCH_NO_RECORDS);
        });
        t.setDaemon(true);
        t.start();
        return 0;
    }

    public boolean cancelServiceSearch(int transID) {
        return false;
    }

    public String selectService(UUID uuid, int security, boolean master) throws BluetoothStateException {
        return null;
    }
}
