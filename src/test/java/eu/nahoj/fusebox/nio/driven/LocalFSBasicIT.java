package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.nio.FSTestHelper;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalFSBasicIT {

    private final FSTestHelper helper = new FSTestHelper();

    @BeforeAll @SneakyThrows void mount() { helper.mountOnce(); }
    @AfterAll @SneakyThrows void unmount() { helper.unmountOnce(); }

    private Path mnt(String rel) { return helper.mnt(rel); }

    @Test
    @SneakyThrows
    void statfs_via_java_calls() {
        // Given a mounted filesystem
        FileStore store = Files.getFileStore(helper.mntDir());
        // Then statfs should report a non-zero total space
        assertThat(store.getTotalSpace()).isGreaterThan(0L);
    }

    @Test
    @SneakyThrows
    void truncate_syscall_changes_file_size() {
        // Given a file with some content
        Path f = mnt("trunc-me.txt");
        Files.writeString(f, "abcdef", CREATE, TRUNCATE_EXISTING);
        assertThat(Files.size(f)).isEqualTo(6);
        // And shell 'truncate' is available (to invoke the truncate syscall)
        assumeTrue(helper.isCmdAvailable("truncate"), "truncate(1) not available");
        // When truncating to size 2 via system command
        Process p = new ProcessBuilder("truncate", "-s", "2", f.toString()).redirectErrorStream(true).start();
        int ec = p.waitFor();
        assertThat(ec).isZero();
        // Then file size should be 2 and content prefix preserved
        assertThat(Files.size(f)).isEqualTo(2);
        assertThat(Files.readString(f)).isEqualTo("ab");
    }

    @Test @SneakyThrows
    void access_read_allowed_but_write_denied_on_readonly_file() {
        // Given a file with no write permission for owner
        Path p = mnt("ro.txt");
        Files.writeString(p, "r", CREATE, TRUNCATE_EXISTING);
        Set<PosixFilePermission> ro = PosixFilePermissions.fromString("r-xr-xr-x");
        Files.setPosixFilePermissions(p, ro);
        // When checking read access via provider (should pass)
        p.getFileSystem().provider().checkAccess(p, java.nio.file.AccessMode.READ);
        // And When checking write access via provider -> should be denied (triggers access())
        assertThatThrownBy(() ->
                p.getFileSystem().provider().checkAccess(p, java.nio.file.AccessMode.WRITE))
            // Then AccessDeniedException or FileSystemException should be raised depending on mapping
            .isInstanceOfAny(java.nio.file.AccessDeniedException.class, java.nio.file.FileSystemException.class);
    }

    @Test @SneakyThrows
    void listing_a_regular_file_as_directory_returns_ENOTDIR() {
        // Given a regular file
        Path f = mnt("notadir.txt");
        Files.writeString(f, "x", CREATE, TRUNCATE_EXISTING);
        // When attempting to list it as a directory
        assertThatThrownBy(() -> {
            try (var s = Files.list(f)) {
                // consume
                s.count();
            }
        })
        // Then NotDirectoryException should be raised (maps to ENOTDIR)
        .isInstanceOf(NotDirectoryException.class);
    }

    @Test @SneakyThrows
    void unlink_missing_path_returns_ENOENT() {
        // Given a path that doesn't exist
        Path missing = mnt("missing-" + System.nanoTime());
        // When deleting
        assertThatThrownBy(() -> Files.delete(missing))
                // Then NoSuchFileException should be raised (maps to ENOENT)
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    @SneakyThrows
    void unlink_on_directory_returns_EISDIR_or_fails() {
        // Given an empty directory on the mount
        Path d = mnt("unlink-dir");
        if (!Files.exists(d)) Files.createDirectory(d);
        assertThat(Files.isDirectory(d)).isTrue();
        // When calling the system 'unlink' on a directory path
        Process p = new ProcessBuilder("unlink", d.toString()).redirectErrorStream(true).start();
        int exit = p.waitFor();
        // Then the command should fail (EISDIR on POSIX), and the directory should still exist
        assertThat(exit).isNotZero();
        assertThat(Files.isDirectory(d)).isTrue();
    }

    @Test
    @SneakyThrows
    void mkdir_on_existing_path_returns_EEXIST() {
        // Given an existing directory name (seeded "dir")
        Path existing = mnt("dir");
        assertThat(Files.isDirectory(existing)).isTrue();
        // When attempting to create it again
        assertThatThrownBy(() -> Files.createDirectory(existing))
                // Then Java should surface FileAlreadyExistsException (maps to EEXIST)
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    @SneakyThrows
    void mkdir_on_negative_path_returns_ENOTDIR() {
        // Given a regular file
        Path f = mnt("notadir.txt");
        Files.writeString(f, "x", CREATE, TRUNCATE_EXISTING);
        // When attempting to create a directory inside it
        Path dirInsideFile = f.resolve("dir");
        assertThatThrownBy(() -> Files.createDirectory(dirInsideFile))
                // Then NotDirectoryException or FileSystemException (maps to ENOTDIR)
                .isInstanceOfAny(NotDirectoryException.class, FileSystemException.class);
    }

    @Test
    @SneakyThrows
    void mkdir_on_negative_path_returns_ENOENT() {
        // Given a path that doesn't exist
        Path missing = mnt("missing-" + System.nanoTime());
        // When attempting to create a directory inside it
        Path dirInsideMissing = missing.resolve("dir");
        assertThatThrownBy(() -> Files.createDirectory(dirInsideMissing))
                // Then NoSuchFileException should be raised (maps to ENOENT)
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    @SneakyThrows
    void readdir_and_mkdir_rmdir() {
        // Given initial seeded files
        try (var s = Files.list(helper.mntDir())) {
            List<String> names = s.map(p -> p.getFileName().toString()).toList();
            // Then root should list seeded entries
            assertThat(names).contains("hello.txt", "dir");
        }
        // When creating a new directory
        Path d = mnt("mk");
        Files.createDirectory(d);
        // Then it should exist as a directory
        assertThat(Files.isDirectory(d)).isTrue();

        // And When listing again
        try (var s2 = Files.list(helper.mntDir())) {
            // Then the new directory should appear
            assertThat(s2.map(p -> p.getFileName().toString())).contains(d.getFileName().toString());
        }

        // When deleting it
        Files.delete(d);
        // Then it should be gone
        assertThat(Files.exists(d)).isFalse();
    }

    @Test
    @SneakyThrows
    void chmod_and_utimens() {
        // Given a file
        Path p = mnt("perms.txt");
        Files.writeString(p, "x", CREATE, TRUNCATE_EXISTING);
        // When setting POSIX permissions
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-----");
        Files.setPosixFilePermissions(p, perms);
        // Then permissions should reflect the change
        assertThat(Files.getPosixFilePermissions(p, NOFOLLOW_LINKS)).isEqualTo(perms);

        // When modifying content (affects mtime)
        FileTime before = Files.getLastModifiedTime(p, NOFOLLOW_LINKS);
        Files.writeString(p, "y", TRUNCATE_EXISTING);
        FileTime after = Files.getLastModifiedTime(p, NOFOLLOW_LINKS);
        // Then mtime should be >= before
        assertThat(after.toMillis()).isGreaterThanOrEqualTo(before.toMillis());
    }

    @Test
    @SneakyThrows
    void utimens_sets_times_explicitly() {
        // Given a file
        Path p = mnt("times.txt");
        Files.writeString(p, "t", CREATE, TRUNCATE_EXISTING);
        // When setting explicit atime/mtime via BasicFileAttributeView (triggers utimens)
        val view = Files.getFileAttributeView(p, BasicFileAttributeView.class, NOFOLLOW_LINKS);
        FileTime mtime = FileTime.from(Instant.now().minusSeconds(123));
        FileTime atime = FileTime.from(Instant.now().minusSeconds(456));
        view.setTimes(mtime, atime, null);
        // Then the reported mtime should equal the value we set
        assertThat(Files.getLastModifiedTime(p, NOFOLLOW_LINKS)).isEqualTo(mtime);
    }
}
