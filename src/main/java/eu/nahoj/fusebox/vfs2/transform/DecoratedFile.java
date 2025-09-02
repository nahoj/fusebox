package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A decorator interface for {@link FuseboxFile} that forwards operations to a delegate by default.
 */
public interface DecoratedFile extends FuseboxFile {

    FuseboxFS fs();

    /** The underlying file to which calls should be delegated by default. */
    FuseboxFile delegate();

    @Override
    default Path path() {
        return delegate().path();
    }

    @Override
    default FileAttributes getAttributes() throws IOException {
        return delegate().getAttributes();
    }

    @Override
    default boolean existsAndIsDirectory() throws IOException {
        return delegate().existsAndIsDirectory();
    }

    @Override
    default List<DirEntry> getEntries() throws IOException {
        return delegate().getEntries();
    }

    @Override
    default FuseboxContent openReadable() throws IOException {
        return delegate().openReadable();
    }
}
