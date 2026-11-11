package j2pc.jar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JAD (J2ME application descriptor) parser.
 *
 * <p>Only the fields that matter for recompilation are interpreted, the rest is
 * kept verbatim so that the generated launcher can reproduce the manifest.</p>
 */
public final class JadFile {

    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Path source;

    private JadFile(Path source) {
        this.source = source;
    }

    public static JadFile parse(Path path) throws IOException {
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        JadFile jad = new JadFile(path);
        String lastKey = null;
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if ((line.startsWith(" ") || line.contains(": ")) && lastKey != null && !line.contains(":")) {
                jad.attributes.put(lastKey, jad.attributes.get(lastKey) + line);
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            jad.attributes.put(key, value);
            lastKey = key;
        }
        return jad;
    }

    public Path source() {
        return source;
    }

    public String get(String key) {
        return attributes.get(key);
    }

    public String get(String key, String fallback) {
        String v = attributes.get(key);
        return v == null ? fallback : v;
    }

    public Map<String, String> all() {
        return attributes;
    }

    public String midletName() {
        return get("MIDlet-Name", "Unknown MIDlet");
    }

    public String vendor() {
        return get("MIDlet-Vendor", "Unknown");
    }

    public String version() {
        return get("MIDlet-Version", "1.0.0");
    }

    public String profile() {
        return get("MicroEdition-Profile", "MIDP-2.0");
    }

    public String configuration() {
        return get("MicroEdition-Configuration", "CLDC-1.1");
    }

    public String jarUrl() {
        return get("MIDlet-Jar-URL");
    }

    /** Parsed {@code MIDlet-1: Name, Icon, Class} entries. */
    public java.util.List<MidletEntry> midlets() {
        java.util.List<MidletEntry> out = new java.util.ArrayList<>();
        for (int i = 1; ; i++) {
            String v = attributes.get("MIDlet-" + i);
            if (v == null) {
                break;
            }
            String[] parts = v.split(",");
            String name = parts.length > 0 ? parts[0].trim() : "MIDlet" + i;
            String icon = parts.length > 1 ? parts[1].trim() : null;
            String cls = parts.length > 2 ? parts[2].trim() : null;
            out.add(new MidletEntry(name, icon, cls));
        }
        return out;
    }

    public static final class MidletEntry {
        public final String name;
        public final String icon;
        public final String className;

        public MidletEntry(String name, String icon, String className) {
            this.name = name;
            this.icon = icon;
            this.className = className;
        }
    }
}
