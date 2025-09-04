package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.nio.FSTestHelper;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalFSXattrIT {

    private final FSTestHelper helper = new FSTestHelper();

    @BeforeAll @SneakyThrows void mount() { helper.mountOnce(); }
    @AfterAll @SneakyThrows void unmount() { helper.unmountOnce(); }

    private Path mnt(String rel) { return helper.mnt(rel); }

    @Test
    @SneakyThrows
    void listxattr_populates_buffer_via_syscall_if_available() {
        // Given xattr support and getfattr available
        Path p = mnt("xattr-list.txt");
        Files.writeString(p, "x", CREATE, TRUNCATE_EXISTING);
        val view = Files.getFileAttributeView(p, UserDefinedFileAttributeView.class, NOFOLLOW_LINKS);
        if (view == null) return; // skip if unsupported
        boolean hasGetfattr = helper.isCmdAvailable("getfattr");
        assumeTrue(hasGetfattr, "getfattr not available; skipping syscall-based listxattr test");
        // When writing two attributes
        view.write("user.alpha", StandardCharsets.UTF_8.encode("A"));
        view.write("user.beta", StandardCharsets.UTF_8.encode("B"));
        // And When listing via system getfattr (which uses listxattr syscall and exercises buffer population path)
        Process proc = new ProcessBuilder("getfattr", "-d", p.toString()).redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        proc.waitFor();
        // Then output should mention both attribute keys
        assertThat(out).contains("user.alpha").contains("user.beta");
    }

    @Test
    @SneakyThrows
    void xattr_roundtrip_userNamespace_ifAvailable() {
        // Given a file and a user.* xattr view (if supported)
        Path p = mnt("xattr.txt");
        Files.writeString(p, "x", CREATE, TRUNCATE_EXISTING);
        UserDefinedFileAttributeView view =
                Files.getFileAttributeView(p, UserDefinedFileAttributeView.class, NOFOLLOW_LINKS);
        if (view == null) return; // skip if unsupported
        String key = "user.test";
        // When writing an xattr value
        ByteBuffer buf = StandardCharsets.UTF_8.encode("val");
        view.write(key, buf);
        // And When reading the xattr back
        ByteBuffer read = ByteBuffer.allocate(view.size(key));
        view.read(key, read);
        read.flip();
        // Then the decoded value should match
        assertThat(StandardCharsets.UTF_8.decode(read).toString()).isEqualTo("val");
        // When deleting the attribute
        view.delete(key);
    }
}
