package eu.nahoj.fusebox.vfs2.api;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.vfs2.transform.MappedContentFile;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public interface FuseboxFile {

    FuseboxFS fs();

    Path path();

    default String name() {
        // Only "/" has null getFileName()
        return requireNonNull(path().getFileName()).toString();
    }

    /** File attributes. Implementations may return minimal attributes if provider doesn't expose POSIX metadata. */
    default FileAttributes getAttributes() throws IOException {
        throw new NotImplementedException();
    }

    default void setPermissions(Set<PosixFilePermission> permissions) throws IOException {
        throw new NotImplementedException();
    }

    // Links

    default String getTargetPath() throws IOException {
        throw new NotImplementedException();
    }

//    default FuseboxFile getTargetFile() throws IOException {
//        throw new NotImplementedException();
//    }

    // Directories

    default void createDirectory(Set<PosixFilePermission> permissions) throws IOException {
        throw new NotImplementedException();
    }

    /** Backs the `opendir` operation. */
    default boolean existsAndIsDirectory() throws IOException {
        throw new NotImplementedException();
    }

    default List<DirEntry> getEntries() throws IOException {
        throw new NotImplementedException();
    }

    // Files

    /** Open a readable handle if this is a regular file. */
    default FuseboxContent openReadable() throws IOException {
        throw new NotImplementedException();
    }

    /** Returns a view of this file with contents mapped by the provided mapper. */
    default FuseboxFile mapContent(UnaryOperator<FuseboxContent> mapper) {
        return new MappedContentFile(this, mapper);
    }

    default void delete() throws IOException {
        throw new NotImplementedException();
    }
}
