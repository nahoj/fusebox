package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlyDirsFSTest {

    TestDelegateFS delegate;
    ReadOnlyDirsFS fs;

    @BeforeEach
    void setUp() {
        delegate = new TestDelegateFS();
        // selected directory "/ro" and non-selected "/rw"
        delegate.setFile("/ro", FileType.DIRECTORY, 0777);
        delegate.setFile("/rw", FileType.DIRECTORY, 0777);
        delegate.setFile("/ro/file.txt", FileType.REGULAR_FILE, 0666);
        // Selector matches the directory path "/ro"
        fs = new ReadOnlyDirsFS(delegate, "ro"::equals);
    }

    @Test
    void getattr_masks_write_permissions_on_selected_dir_only() throws Exception {
        FileAttributes roAttrs = fs.getattr("/ro", null);
        EnumSet<PosixFilePermission> perms = EnumSet.copyOf(roAttrs.permissions());
        assertThat(perms).doesNotContain(PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_WRITE);

        FileAttributes rwAttrs = fs.getattr("/rw", null);
        EnumSet<PosixFilePermission> rwPerms = EnumSet.copyOf(rwAttrs.permissions());
        assertThat(rwPerms).contains(PosixFilePermission.OWNER_WRITE);
    }

    @Test
    void access_write_denied_on_selected_dir() throws Exception {
        assertThatThrownBy(() -> fs.access("/ro", 02))
                .isInstanceOf(AccessDeniedException.class);

        // Non-selected directory delegates
        fs.access("/rw", 02);
        assertThat(delegate.calls).contains("access:/rw:2");
    }

    @Test
    void write_affecting_ops_denied_for_children_of_selected_dir() {
        // Operations under /ro should be blocked via decorated* methods
        assertThatThrownBy(() -> fs.mkdir("/ro/newdir", 0755))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fs.rmdir("/ro/olddir"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fs.create("/ro/new.txt", 0644, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fs.unlink("/ro/file.txt"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fs.rename("/ro/a", "/ro/b", 0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void setxattr_and_removexattr_denied_on_selected_dir_itself() {
        assertThatThrownBy(() -> fs.setxattr("/ro", "user.k", ByteBuffer.wrap(new byte[]{1})))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fs.decoratedRemovexattr("/ro", "user.k"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
