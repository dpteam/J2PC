package j2pc.gui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import j2pc.Options;
import j2pc.Recompiler;
import j2pc.build.AppBuilder;
import j2pc.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Local HTTP server backing the web UI.
 *
 * <p>Everything is JSON over localhost, so the same front end works in an embedded
 * window, in a browser, or from a script. The server binds to the loopback
 * interface only: it recompiles arbitrary local archives and must never be
 * reachable from the network.</p>
 */
final class WebServer {

    private final Options baseOpts;
    private final HttpServer server;
    private final StringBuilder logBuffer = new StringBuilder();
    private final AtomicBoolean busy = new AtomicBoolean();

    private Path folder = defaultFolder();
    private Path lastOutput;
    private Path lastJar;

    WebServer(Options baseOpts, int port) throws IOException {
        this.baseOpts = baseOpts;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", this::handleIndex);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/scan", this::handleScan);
        server.createContext("/api/recompile", this::handleRecompile);
        server.createContext("/api/files", this::handleFiles);
        server.createContext("/api/file", this::handleFile);
        server.createContext("/api/run", this::handleRun);
    }

    void start() {
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    void stop() {
        server.stop(0);
    }

    // ---------------------------------------------------------------- handlers

    private void handleIndex(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            send(ex, 404, "text/plain", "not found");
            return;
        }
        send(ex, 200, "text/html; charset=utf-8", WebAssets.INDEX_HTML);
    }

    private void handleState(HttpExchange ex) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", baseOpts.version);
        body.put("folder", folder.toString());
        body.put("apps", listApps());
        body.put("busy", busy.get());
        body.put("built", lastJarName());
        sendJson(ex, body);
    }

    private void handleScan(HttpExchange ex) throws IOException {
        String requested = query(ex).getOrDefault("folder", folder.toString());
        Path candidate = Path.of(requested).toAbsolutePath().normalize();
        if (Files.isDirectory(candidate)) {
            folder = candidate;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("folder", folder.toString());
        body.put("apps", listApps());
        sendJson(ex, body);
    }

    private void handleRecompile(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "POST only");
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("log", "[error] a recompilation is already running");
            sendJson(ex, body);
            return;
        }
        synchronized (logBuffer) {
            logBuffer.setLength(0);
        }
        try {
            String json = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> fields = parseFlatJson(json);
            Path jar = Path.of(fields.getOrDefault("jar", "")).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", false);
                body.put("log", "[error] no such file: " + jar);
                sendJson(ex, body);
                return;
            }

            Options opts = copyOptions();
            opts.outputDir = opts.outputDir.resolve(j2pc.util.Names.folderForJar(jar));
            opts.buildJar = "true".equals(fields.get("build"));
            opts.buildNativeImage = "true".equals(fields.get("exe"));
            if (opts.buildNativeImage) {
                opts.buildJar = true;
            }
            opts.generateStubs = !"false".equals(fields.get("stubs"));
            opts.lineComments = !"false".equals(fields.get("lines"));

            boolean ok = runRecompile(jar, opts);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", ok);
            body.put("log", drainLog());
            body.put("summary", summary());
            sendJson(ex, body);
        } catch (RuntimeException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("log", "[error] " + e);
            sendJson(ex, body);
        } finally {
            busy.set(false);
        }
    }

    private void handleFiles(HttpExchange ex) throws IOException {
        List<String> files = new ArrayList<>();
        Path root = lastOutput != null ? lastOutput : baseOpts.outputDir;
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                        .sorted()
                        .forEach(p -> files.add(root.relativize(p).toString().replace('\\', '/')));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("files", files);
        sendJson(ex, body);
    }

    private void handleFile(HttpExchange ex) throws IOException {
        String relative = query(ex).getOrDefault("path", "");
        Path root = lastOutput != null ? lastOutput : baseOpts.outputDir;
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            String decoded = URLDecoder.decode(relative, StandardCharsets.UTF_8);
            Path target = root.resolve(decoded).normalize();
            // Refuse anything that escapes the output directory.
            if (!target.startsWith(root.normalize())) {
                body.put("error", "path outside the output directory");
            } else if (!Files.isRegularFile(target)) {
                body.put("error", "not found: " + decoded);
            } else {
                body.put("content", Files.readString(target, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            body.put("error", String.valueOf(e.getMessage()));
        }
        sendJson(ex, body);
    }

    /**
     * Launches the most recently packaged application in its own process.
     *
     * <p>Separate process on purpose: a recompiled MIDlet opens its own window and
     * runs its own event loop, and a crash in the application must not take the
     * interface down with it.</p>
     */
    private void handleRun(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "POST only");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        Path jar = lastJar;
        if (jar == null || !Files.isRegularFile(jar)) {
            body.put("ok", false);
            body.put("log", "[error] nothing has been packaged yet; recompile first");
            sendJson(ex, body);
            return;
        }
        try {
            String javaBin = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java").toString();
            // The application writes to the console; there is no console behind a
            // window, so its output goes to a log next to the archive. That is also
            // the first place to look when an application refuses to start.
            Path consoleLog = jar.resolveSibling("console.log");
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-jar", jar.toAbsolutePath().toString())
                    .directory(jar.toAbsolutePath().getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(consoleLog.toFile()));
            Process process = pb.start();
            appendLog("[info] started " + jar.getFileName() + " (pid " + process.pid() + ")");
            body.put("ok", true);
            body.put("pid", process.pid());
            body.put("log", "[info] started " + jar.getFileName()
                    + " (pid " + process.pid() + "); output goes to " + consoleLog);
        } catch (IOException e) {
            appendLog("[error] cannot start the application: " + e.getMessage());
            body.put("ok", false);
            body.put("log", "[error] cannot start the application: " + e.getMessage());
        }
        sendJson(ex, body);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ---------------------------------------------------------------- helpers

    private boolean runRecompile(Path jar, Options opts) {
        Log.setSink(this::appendLog);
        try {
            Path jad = siblingJad(jar);
            Recompiler.Result result = new Recompiler(opts).recompile(jar, jad);
            lastOutput = opts.outputDir;
            lastStats = result.classes + " classes, " + result.methods + " methods";
            if (!opts.buildJar) {
                return true;
            }
            AppBuilder.BuildResult build = new AppBuilder(opts).build(opts.outputDir,
                    opts.outputDir.resolve("assets"),
                    new AppBuilder.AppInfo(result.appName, result.appVendor, result.appVersion));
            if (build.ok) {
                lastStats += ", jar " + build.jar.getFileName();
                lastJar = build.jar;
            }
            return build.ok;
        } catch (IOException e) {
            appendLog("[error] " + e.getMessage());
            return false;
        } finally {
            Log.setSink(null);
        }
    }

    private String lastStats = "";

    private String summary() {
        return lastStats;
    }

    /** The packaged JAR of the last successful build, or null. */
    private String lastJarName() {
        return lastJar == null ? "" : lastJar.getFileName().toString();
    }

    private void appendLog(String line) {
        synchronized (logBuffer) {
            logBuffer.append(line).append('\n');
        }
    }

    private String drainLog() {
        synchronized (logBuffer) {
            return logBuffer.toString();
        }
    }

    private Options copyOptions() {
        Options o = new Options();
        o.outputDir = baseOpts.outputDir;
        o.version = baseOpts.version;
        o.verbose = baseOpts.verbose;
        o.includeSynthetic = baseOpts.includeSynthetic;
        o.sourceLevel = baseOpts.sourceLevel;
        o.indentUnit = baseOpts.indentUnit;
        o.packagePrefix = baseOpts.packagePrefix;
        o.onlyPrefix = baseOpts.onlyPrefix;
        o.generateStubs = baseOpts.generateStubs;
        return o;
    }

    private List<Map<String, Object>> listApps() {
        List<Map<String, Object>> apps = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return apps;
        }
        try (Stream<Path> list = Files.list(folder)) {
            list.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .forEach(p -> {
                        Map<String, Object> app = new LinkedHashMap<>();
                        app.put("name", p.getFileName().toString());
                        app.put("path", p.toString());
                        app.put("size", humanSize(p));
                        apps.add(app);
                    });
        } catch (IOException e) {
            // unreadable folder: report nothing
        }
        return apps;
    }

    private static String humanSize(Path p) {
        try {
            long bytes = Files.size(p);
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return (bytes / 1024) + " KiB";
            }
            return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "?";
        }
    }

    private static Path siblingJad(Path jar) {
        String name = jar.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        Path candidate = jar.resolveSibling(name.substring(0, dot) + ".jad");
        return Files.exists(candidate) ? candidate : null;
    }

    /**
     * Starting folder. The working directory is the honest default, but when it
     * holds no archives and a {@code samples} directory exists, that is almost
     * certainly what the user wants to look at.
     */
    private static Path defaultFolder() {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        if (hasJars(cwd)) {
            return cwd;
        }
        Path samples = cwd.resolve("samples");
        if (Files.isDirectory(samples) && hasJars(samples)) {
            return samples;
        }
        return cwd;
    }

    private static boolean hasJars(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.anyMatch(p -> p.toString().toLowerCase().endsWith(".jar"));
        } catch (IOException e) {
            return false;
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    /** Minimal flat JSON reader: the API only sends string and boolean members. */
    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|true|false|\\d+)")
                .matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String raw = m.group(2);
            String value;
            if (raw.startsWith("\"")) {
                value = m.group(3)
                        .replace("\\\\", "\\")
                        .replace("\\\"", "\"")
                        .replace("\\/", "/");
            } else {
                value = raw;
            }
            out.put(key, value);
        }
        return out;
    }

    private static void sendJson(HttpExchange ex, Map<String, Object> body) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", toJson(body));
    }

    private static void send(HttpExchange ex, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------------- json

    private static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value);
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeJson(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> items) {
            sb.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(sb, item);
            }
            sb.append(']');
        } else if (value instanceof CharSequence s) {
            writeJsonString(sb, s.toString());
        } else {
            writeJsonString(sb, String.valueOf(value));
        }
    }

    private static void writeJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
