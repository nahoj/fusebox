package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlyFSTest {

    TestDelegateFS delegate;
    ReadOnlyFS ro;

    @BeforeEach
    void setUp() {
        delegate = new TestDelegateFS();
        delegate.setFile("/file.txt", FileType.REGULAR_FILE, 0777);
        ro = new ReadOnlyFS(delegate);
    }

    @Test
    void getattr_masks_write_permissions() throws Exception {
        FileAttributes attrs = ro.getattr("/file.txt", null);
        EnumSet<PosixFilePermission> perms = EnumSet.copyOf(attrs.permissions());
        assertThat(perms).doesNotContain(PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_WRITE);
    }

    @Test
    void access_write_denied_read_allowed() throws Exception {
        // read-only mask 4 should pass and be delegated
        ro.access("/file.txt", 04);
        assertThat(delegate.calls).contains("access:/file.txt:4");

        // write mask 2 should be denied
        assertThatThrownBy(() -> ro.access("/file.txt", 02))
                .isInstanceOf(ReadOnlyFileSystemException.class);
    }

    @Test
    void write_affecting_operations_throw() {
        assertThatThrownBy(() -> ro.setxattr("/file.txt", "user.k", ByteBuffer.wrap(new byte[]{1})))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.removexattr("/file.txt", "user.k"))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.chmod("/file.txt", 0644, null))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.chown("/file.txt", 0, 0, null))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.utimens("/file.txt", null, null, null))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.symlink("/target", "/link"))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.mkdir("/dir", 0755))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.rmdir("/dir"))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.create("/n", 0644, null))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.write("/file.txt", ByteBuffer.allocate(1), 1, 0, null))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.truncate("/file.txt", 0, null))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.unlink("/file.txt"))
                .isInstanceOf(ReadOnlyFileSystemException.class);
        assertThatThrownBy(() -> ro.rename("/a", "/b", 0))
                .isInstanceOf(ReadOnlyFileSystemException.class);
    }
}
