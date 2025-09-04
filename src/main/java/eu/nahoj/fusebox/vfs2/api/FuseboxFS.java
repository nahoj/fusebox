package eu.nahoj.fusebox.vfs2.api;

import eu.nahoj.fusebox.vfs2.transform.MappedFilesFS;
import eu.nahoj.fusebox.vfs2.transform.MappedNamesFS;
import org.apache.commons.lang3.NotImplementedException;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;

import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface FuseboxFS {

    default Set<Operation> supportedOperations() {
        return Set.of();
    }

    // /////////////////////
    // Operations
    // /////////////////////

    default Statvfs getStats(String path) throws IOException {
        throw new NotImplementedException();
    }

    /// `path` is relative to the FS root
    /// - it does not start or end with a '/'
    /// - it does not contain parts '.' or '..', nor repeated '/'
    /// - the empty string "" designates the FS root
    default FuseboxFile resolveFile(String path) throws IOException {
        throw new NotImplementedException();
    }

    // //////////////////////
    // Transformations
    // //////////////////////

    default FuseboxFS mapFiles(Predicate<String> pathSelector, UnaryOperator<FuseboxFile> mapper) {
        return new MappedFilesFS(this, pathSelector, mapper);
    }

    default FuseboxFS mapNames(Predicate<String> origPathSelector, Predicate<String> mountPathSelector,
                               UnaryOperator<String> origNameToMount, UnaryOperator<String> mountNameToOrig) {
        return new MappedNamesFS(this, origPathSelector, mountPathSelector,
                origNameToMount, mountNameToOrig);
    }
}
