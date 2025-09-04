package eu.nahoj.fusebox.nio.transform;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Factory utilities for {@link ContentGenerator} implementations.
 */
public final class ContentGenerators {
    private ContentGenerators() {}

    /**
     * Creates a generator that runs an external process, writes the source bytes to stdin and returns stdout bytes.
     * <p>
     * Notes:
     * - Non-zero exit status results in IOException including stderr (abbreviated).
     * - The process is forcibly destroyed on timeout.
     */
    public static ContentGenerator fromProcess(List<String> command) {
        return fromProcess(command, Map.of(), Duration.ofSeconds(30));
    }

    public static ContentGenerator fromProcess(List<String> command, Map<String, String> env, Duration timeout) {
        return (path, source) -> runProcess(command, env, timeout, source);
    }

    private static byte[] runProcess(List<String> command, Map<String, String> env, Duration timeout, byte[] stdin) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (!env.isEmpty()) {
            pb.environment().putAll(env);
        }
        Process p = pb.start();
        // Write stdin then close immediately to signal EOF
        try (OutputStream os = p.getOutputStream()) {
            os.write(stdin);
            os.flush();
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread tOut = new Thread(() -> drain(p.getInputStream(), stdout), "cg-stdout");
        Thread tErr = new Thread(() -> drain(p.getErrorStream(), stderr), "cg-stderr");
        tOut.start();
        tErr.start();
        boolean finished;
        try {
            finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Process interrupted for command: " + String.join(" ", command), e);
        }
        if (!finished) {
            p.destroyForcibly();
            try { tOut.join(200); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
            try { tErr.join(200); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
            throw new IOException("Process timed out after " + timeout + " running: " + String.join(" ", command));
        }
        try {
            tOut.join();
            tErr.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted awaiting process IO joins for command: " + String.join(" ", command), e);
        }
        int code = p.exitValue();
        if (code != 0) {
            String err = stderr.toString(StandardCharsets.UTF_8);
            throw new IOException("Process exited with code " + code + " for command: "
                    + String.join(" ", command) + " stderr: " + StringUtils.abbreviate(err, 4000));
        }
        return stdout.toByteArray();
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[64 * 1024];
        try {
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        } catch (IOException ignore) {
            // Best-effort drain; errors will ultimately surface via exit code
        }
    }
}
