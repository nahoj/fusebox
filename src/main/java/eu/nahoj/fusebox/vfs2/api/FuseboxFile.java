package eu.nahoj.fusebox.vfs2.api;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.vfs2.transform.MappedContentFile;

import java.io.IOException;
import java.util.List;
import java.util.function.UnaryOperator;

public interface FuseboxFile {

    /** Base name of this file (no parent path). */
    String name();

    /** File attributes. Implementations may return minimal attributes if provider doesn't expose POSIX metadata. */
    FileAttributes getAttributes() throws IOException;

    /** Backs the `opendir` operation. */
    boolean existsAndIsDirectory() throws IOException;

    List<DirEntry> getEntries() throws IOException;

    /** Open a readable handle if this is a regular file. */
    FuseboxContent openReadable() throws IOException;

    /** Returns a view of this file with contents mapped by the provided mapper. */
    default FuseboxFile mapContent(UnaryOperator<FuseboxContent> mapper) {
        return new MappedContentFile(this, mapper);
    }
}
