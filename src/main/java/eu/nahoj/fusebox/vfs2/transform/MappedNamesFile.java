package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@RequiredArgsConstructor
class MappedNamesFile implements DecoratedFile {

    private final FuseboxFile delegate;
    private final Path origParentPath;
    private final Predicate<String> origPathSelector;
    private final UnaryOperator<String> origNameToMount;

    @Override
    public FuseboxFile delegate() {
        return delegate;
    }

    @Override
    public String name() {
        return origChildNameToMount(origParentPath, delegate.name());
    }

    @Override
    public List<DirEntry> getEntries() throws IOException {
        // For entries of this directory, the original directory path is
        // origParentPath resolved with the original (unmapped) directory name.
        Path thisOrigDirPath = origParentPath.resolve(delegate.name());
        List<DirEntry> dirEntries = delegate.getEntries().stream()
                .map(e -> e.withName(origChildNameToMount(thisOrigDirPath, e.name())))
                .collect(toList());
        // Detect duplicates after mapping
        var duplicateNames = dirEntries.stream()
                .collect(groupingBy(DirEntry::name, counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(toSet());
        if (!duplicateNames.isEmpty()) {
            throw new IllegalStateException("Duplicate entries: " + dirEntries.stream().sorted().toList());
        }
        return dirEntries;
    }

    private String origChildNameToMount(Path origParentPath, String origName) {
        Path origPath = origParentPath.resolve(origName);
        return origPathSelector.test(origPath.toString())
                ? origNameToMount.apply(origName)
                : origName;
    }
}
