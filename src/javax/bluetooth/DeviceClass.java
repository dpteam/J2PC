package javax.bluetooth;

/** Device classification returned by discovery. */
public class DeviceClass {

    private final int major;
    private final int minor;
    private final int serviceClasses;

    public DeviceClass(int majorDeviceClass, int minorDeviceClass, int serviceClasses) {
        this.major = majorDeviceClass;
        this.minor = minorDeviceClass;
        this.serviceClasses = serviceClasses;
    }

    public int getMajorDeviceClass() {
        return major;
    }

    public int getMinorDeviceClass() {
        return minor;
    }

    public int getServiceClasses() {
        return serviceClasses;
    }
}
