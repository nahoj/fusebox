package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.nio.api.FuseboxFS;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface ChainingFS extends FuseboxFS {

    default ChainingFS filterPaths(Predicate<String> pathSelector) {
        return new FilteredFS(this, pathSelector);
    }

    default ChainingFS mapFileContents(Predicate<String> pathSelector, ContentGenerator generator) {
        return new ContentViewFS(this, pathSelector, generator);
    }

    default ChainingFS mapFileNames(
            Predicate<String> origPathSelector,
            Predicate<String> mountPathSelector,
            UnaryOperator<String> fileNameToMount,
            UnaryOperator<String> fileNameToOrig
    ) {
        return new RenamedFS(this, origPathSelector, mountPathSelector, fileNameToMount, fileNameToOrig);
    }

    default ChainingFS readOnly() {
        return new ReadOnlyFS(this);
    }

    default ChainingFS withReadOnlyDirs(Predicate<String> pathSelector) {
        return new ReadOnlyDirsFS(this, pathSelector);
    }
}
