package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import eu.nahoj.fusebox.vfs2.driven.LocalFS;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MappedNamesReadlinkIT {

    private @Nullable FileObject rootFo;

    @AfterEach
    void tearDown() throws Exception {
        if (rootFo != null) rootFo.close();
    }

    @Test
    void relative_symlink_target_is_mapped() throws Exception {
        // Setup a temporary source FS with a -> b symlink
        Path tmp = Files.createTempDirectory("vfs2-mappednames-it-");
        Files.writeString(tmp.resolve("b"), "x");
        Files.createSymbolicLink(tmp.resolve("a"), Path.of("b"));

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        // Base FS and name-mapping FS that appends/strips a trailing '1' to every path element
        LocalFS base = new LocalFS(rootFo);
        FuseboxFS fs = base.mapNames(
                _ -> true, // origPathSelector
                _ -> true, // mountPathSelector
                name -> name + "1", // origNameToMount
                name -> name.endsWith("1") ? name.substring(0, name.length() - 1) : name // mountNameToOrig
        );

        // Resolve mapped link and check readlink mapping: a1 -> b1
        FuseboxFile link = fs.resolveFile("a1");
        String target = link.getTargetPath();
        assertThat(target).isEqualTo("b1");
    }
}
