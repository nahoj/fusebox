package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFuseboxFileIT {

    private @Nullable FileObject rootFo;

    @AfterEach
    void tearDown() throws Exception {
        if (rootFo != null) rootFo.close();
    }

    @Test
    void read_file_and_list_children() throws Exception {
        // Prepare temp fs
        Path tmp = Files.createTempDirectory("vfs2test");
        Path file = tmp.resolve("hello.txt");
        Files.writeString(file, "hello world", StandardCharsets.UTF_8);

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        LocalFS fs = new LocalFS(rootFo);

        // Resolve file
        FuseboxFile f = fs.resolveFile("hello.txt");
        FileAttributes attrs = f.getAttributes();
        assertThat(attrs.size()).isEqualTo(11L);

        // Read first 5 bytes
        try (FuseboxContent r = f.openReadable()) {
            ByteBuffer buf = ByteBuffer.allocate(5);
            int n = r.withByteChannelUncheckedIO(ch -> ch.readAt(buf, 0));
            assertThat(n).isEqualTo(5);
            buf.flip();
            byte[] arr = new byte[buf.remaining()];
            buf.get(arr);
            assertThat(new String(arr, StandardCharsets.UTF_8)).isEqualTo("hello");
        }

        // List children
        FuseboxFile dir = fs.resolveFile("");
        assertThat(dir.getEntries())
                .extracting(DirEntry::name)
                .contains("hello.txt");
    }
}
