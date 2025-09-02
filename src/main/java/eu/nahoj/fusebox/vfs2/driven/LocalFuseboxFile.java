package eu.nahoj.fusebox.vfs2.driven;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.vfs2.FileObject;
import org.cryptomator.jfuse.api.FuseOperations.Operation;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.cryptomator.jfuse.api.FuseOperations.Operation.READLINK;

/// Not named `LocalFile` to avoid confusion with vfs2's `LocalFile`.
@Accessors(fluent = true)
public class LocalFuseboxFile extends Vfs2File {

    public static final Set<Operation> IMPLEMENTED_OPERATIONS =
            SetUtils.union(Vfs2File.IMPLEMENTED_OPERATIONS, EnumSet.of(READLINK));

    @Getter
    private final LocalFS fs;

    public LocalFuseboxFile(LocalFS fs, Path path, FileObject fo) {
        super(fs, path, fo);
        this.fs = fs;
    }

//    @Override
//    public String getTargetPath() throws IOException {
//        return Files.readSymbolicLink(fo.getPath()).toString();
//    }
}
