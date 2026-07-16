package io.graphingest;

import com.google.gson.Gson;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Push your code to the GraphIngest platform.
 *
 * <pre>{@code
 * // Dashboard-only:
 * DeployResult result = Deploy.run();
 *
 * // With a local .env file:
 * DeployResult result = Deploy.run(DeployOpts.builder().envPath(".env").build());
 *
 * // Absolute path:
 * DeployResult result = Deploy.run(DeployOpts.builder().envPath("/home/me/prod.env").build());
 * }</pre>
 */
public class Deploy {

    private static final Logger LOG = Logger.getLogger(Deploy.class.getName());
    private static final Gson GSON = new Gson();
    private static final Pattern NODE_GRAPH_PATTERN = Pattern.compile("Node\\.create|Graph\\.create|@GraphIngestNode|@GraphIngestGraph");

    private static final Set<String> SKIP_DIRS = Set.of(
            "target", "build", ".gradle", ".git", ".idea", "node_modules", "__pycache__", ".venv"
    );

    /**
     * Deploy with default options (dashboard-only env vars).
     */
    public static DeployResult run() throws IOException, InterruptedException {
        return run(DeployOpts.builder().build());
    }

    /**
     * Deploy with custom options.
     */
    public static DeployResult run(DeployOpts opts) throws IOException, InterruptedException {
        Path projectDir = opts.getProjectDir() != null
                ? Path.of(opts.getProjectDir())
                : Path.of(System.getProperty("user.dir"));

        // 1. Find source files with Node.create/Graph.create
        LOG.info("Scanning for Node/Graph definitions...");
        List<Path> sourceFiles = findJavaSourceFiles(projectDir);
        if (sourceFiles.isEmpty()) {
            throw new IllegalStateException("No Java files with Node.create() or Graph.create() found in " + projectDir);
        }
        LOG.info("  Found " + sourceFiles.size() + " file(s) with Node/Graph definitions");

        // 2. Read env file (only if envPath provided)
        Map<String, String> envVars = new LinkedHashMap<>();
        if (opts.getEnvPath() != null && !opts.getEnvPath().isEmpty()) {
            Path envFile = Path.of(opts.getEnvPath()).isAbsolute()
                    ? Path.of(opts.getEnvPath())
                    : projectDir.resolve(opts.getEnvPath());
            envVars = parseEnvFile(envFile);
            if (!envVars.isEmpty()) {
                LOG.info("Environment variables (from " + opts.getEnvPath() + "):");
                envVars.keySet().forEach(k -> LOG.info("  ✓ " + k));
            } else {
                LOG.warning("  " + opts.getEnvPath() + " not found or empty — using dashboard variables only");
            }
        } else {
            LOG.info("  No envPath provided — using dashboard variables only");
        }

        // 3. Read pom.xml or build.gradle
        String pomContent = "";
        Path pomPath = projectDir.resolve("pom.xml");
        if (Files.exists(pomPath)) {
            pomContent = Files.readString(pomPath);
            LOG.info("  Found pom.xml");
        }

        // 4. Prepare payload
        Map<String, String> files = new LinkedHashMap<>();
        for (Path fp : sourceFiles) {
            String rel = projectDir.relativize(fp).toString();
            files.put(rel, Files.readString(fp));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("files", files);
        payload.put("dependencies", pomContent);
        payload.put("env_vars", envVars);
        payload.put("language", "java");

        // 5. Upload to platform
        LOG.info("Uploading to GraphIngest platform...");
        GraphIngestClient client = GraphIngestClient.getDefault();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getBaseUrl() + "/api/deploy"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + client.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("Deploy failed (" + response.statusCode() + "): " + response.body());
        }

        DeployResult result = GSON.fromJson(response.body(), DeployResult.class);

        LOG.info("Deployed. " + result.getFunctions().size() + " function(s) registered:");
        result.getFunctions().forEach(fn -> LOG.info("  • " + fn));

        return result;
    }

    private static List<Path> findJavaSourceFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(p -> {
                        for (Path segment : root.relativize(p)) {
                            if (SKIP_DIRS.contains(segment.toString())) return false;
                        }
                        return true;
                    })
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return NODE_GRAPH_PATTERN.matcher(content).find();
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    private static Map<String, String> parseEnvFile(Path path) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (!Files.exists(path)) return vars;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx == -1) continue;
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                // Strip surrounding quotes
                if (val.length() >= 2) {
                    if ((val.startsWith("\"") && val.endsWith("\"")) ||
                        (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                }
                if (!key.isEmpty()) vars.put(key, val);
            }
        } catch (IOException e) {
            LOG.warning("Could not read " + path + ": " + e.getMessage());
        }
        return vars;
    }
}
