package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.TestFileInfo;
import eu.nahoj.fusebox.common.api.FileAttributes;
import org.cryptomator.jfuse.api.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalFSGetattrIT {

    @TempDir
    Path tmp;

    @Test
    void getattr_prefers_open_handle_when_FileInfo_present() throws Exception {
        // Given a real file under a VFS-backed root
        Path real = tmp.resolve("real.txt");
        byte[] content = "hello world".getBytes();
        Files.write(real, content);

        LocalFS fs = new LocalFS(tmp);

        // Prepare a Mockito-backed FileInfo. We will open the file via public API to populate fh.
        FileInfo fi = new TestFileInfo(0, 0444, EnumSet.of(StandardOpenOption.READ), 0);

        // Use the filesystem 'open' to create an entry in openFiles and set fh on FileInfo
        fs.open("/real.txt", fi);

        // When getattr is called with a bogus path, expecting it to use the fh instead
        FileAttributes attrs = fs.getattr("/does-not-exist.txt", fi);

        // Then attributes correspond to the real file referenced by the open handle
        assertThat(attrs.size()).isEqualTo(content.length);
    }
}
