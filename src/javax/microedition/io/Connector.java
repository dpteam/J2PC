package javax.microedition.io;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/** Factory for J2ME connections. */
public final class Connector {

    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    private Connector() {
    }

    public static Connection open(String name) throws IOException {
        return open(name, READ_WRITE, false);
    }

    public static Connection open(String name, int mode) throws IOException {
        return open(name, mode, false);
    }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name.startsWith("http://") || name.startsWith("https://")) {
            return new HttpConnectionImpl(name, mode);
        }
        if (name.startsWith("file://")) {
            return new javax.microedition.io.file.FileConnectionImpl(name);
        }
        if (name.startsWith("sms://") || name.startsWith("mms://") || name.startsWith("cbs://")) {
            return new javax.wireless.messaging.MessageFactory.Connection(name);
        }
        throw new ConnectionNotFoundException("unsupported protocol: " + name);
    }

    public static InputStream openInputStream(String name) throws IOException {
        return ((InputConnection) open(name, READ)).openInputStream();
    }

    public static DataInputStream openDataInputStream(String name) throws IOException {
        return ((InputConnection) open(name, READ)).openDataInputStream();
    }

    public static OutputStream openOutputStream(String name) throws IOException {
        return ((OutputConnection) open(name, WRITE)).openOutputStream();
    }

    public static DataOutputStream openDataOutputStream(String name) throws IOException {
        return ((OutputConnection) open(name, WRITE)).openDataOutputStream();
    }

    // ------------------------------------------------------------ http impl

    private static final class HttpConnectionImpl implements HttpConnection {

        private final String url;
        private final int mode;
        private HttpURLConnection connection;
        private String method = GET;
        private final Map<String, String> requestProperties = new HashMap<>();

        HttpConnectionImpl(String url, int mode) {
            this.url = url;
            this.mode = mode;
        }

        private HttpURLConnection connection() throws IOException {
            if (connection == null) {
                URLConnection raw = new URL(url).openConnection();
                if (!(raw instanceof HttpURLConnection)) {
                    throw new IOException("not an HTTP URL: " + url);
                }
                connection = (HttpURLConnection) raw;
                connection.setRequestMethod(method);
                connection.setInstanceFollowRedirects(true);
                for (Map.Entry<String, String> e : requestProperties.entrySet()) {
                    connection.setRequestProperty(e.getKey(), e.getValue());
                }
                if (mode != READ) {
                    connection.setDoOutput(true);
                }
            }
            return connection;
        }

        @Override
        public String getURL() {
            return url;
        }

        @Override
        public String getProtocol() {
            return url.startsWith("https") ? "https" : "http";
        }

        @Override
        public String getHost() {
            try {
                return new URL(url).getHost();
            } catch (IOException e) {
                return "";
            }
        }

        @Override
        public String getFile() {
            try {
                return new URL(url).getFile();
            } catch (IOException e) {
                return "";
            }
        }

        @Override
        public String getRef() {
            try {
                return new URL(url).getRef();
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String getQuery() {
            String file = getFile();
            int q = file.indexOf('?');
            return q < 0 ? null : file.substring(q + 1);
        }

        @Override
        public int getPort() {
            try {
                return new URL(url).getPort();
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public void setRequestMethod(String method) throws IOException {
            this.method = method;
            if (connection != null) {
                connection.setRequestMethod(method);
            }
        }

        @Override
        public String getRequestProperty(String key) {
            return requestProperties.get(key);
        }

        @Override
        public void setRequestProperty(String key, String value) throws IOException {
            requestProperties.put(key, value);
            if (connection != null) {
                connection.setRequestProperty(key, value);
            }
        }

        @Override
        public int getResponseCode() throws IOException {
            return connection().getResponseCode();
        }

        @Override
        public String getResponseMessage() throws IOException {
            return connection().getResponseMessage();
        }

        @Override
        public long getExpiration() throws IOException {
            return connection().getExpiration();
        }

        @Override
        public long getDate() throws IOException {
            return connection().getDate();
        }

        @Override
        public long getLastModified() throws IOException {
            return connection().getLastModified();
        }

        @Override
        public String getHeaderField(String name) throws IOException {
            return connection().getHeaderField(name);
        }

        @Override
        public int getHeaderFieldInt(String name, int def) throws IOException {
            return connection().getHeaderFieldInt(name, def);
        }

        @Override
        public long getHeaderFieldDate(String name, long def) throws IOException {
            return connection().getHeaderFieldDate(name, def);
        }

        @Override
        public String getHeaderField(int index) throws IOException {
            return connection().getHeaderField(index);
        }

        @Override
        public String getHeaderFieldKey(int index) throws IOException {
            return connection().getHeaderFieldKey(index);
        }

        @Override
        public String getType() {
            try {
                return connection().getContentType();
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String getEncoding() {
            try {
                return connection().getContentEncoding();
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public long getLength() {
            try {
                return connection().getContentLengthLong();
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return connection().getInputStream();
        }

        @Override
        public DataInputStream openDataInputStream() throws IOException {
            return new DataInputStream(openInputStream());
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return connection().getOutputStream();
        }

        @Override
        public DataOutputStream openDataOutputStream() throws IOException {
            return new DataOutputStream(openOutputStream());
        }

        @Override
        public void close() throws IOException {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Utility used by tests: reads a whole stream into a byte array. */
    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
