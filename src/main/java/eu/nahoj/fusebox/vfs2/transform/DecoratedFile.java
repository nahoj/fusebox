package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;

import java.io.IOException;
import java.util.List;

/**
 * A decorator interface for {@link FuseboxFile} that forwards operations to a delegate by default.
 */
public interface DecoratedFile extends FuseboxFile {

    /** The underlying file to which calls should be delegated by default. */
    FuseboxFile delegate();

    @Override
    default String name() {
        return delegate().name();
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
