package javax.bluetooth;

/**
 * Bluetooth UUID.
 *
 * <p>The desktop emulation has no radio, but the class itself is fully
 * functional: games commonly build service UUIDs and compare them even when
 * Bluetooth is unavailable.</p>
 */
public class UUID {

    private final String value;

    public UUID(String uuidValue, boolean shortUUID) {
        if (uuidValue == null) {
            throw new NullPointerException("uuidValue");
        }
        String hex = uuidValue.replace("-", "").toUpperCase();
        if (shortUUID) {
            while (hex.length() < 8) {
                hex = "0" + hex;
            }
        }
        this.value = hex;
    }

    public UUID(long uuidValue) {
        this(Long.toHexString(uuidValue & 0xFFFFFFFFL), true);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UUID)) {
            return false;
        }
        return value.equals(((UUID) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
