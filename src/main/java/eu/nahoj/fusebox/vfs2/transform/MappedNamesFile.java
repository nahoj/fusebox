package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class MappedNamesFile implements DecoratedFile {

    @Getter
    private final MappedNamesFS fs;
    @Getter
    private final Path path;
    @Getter
    private final FuseboxFile delegate;
    private final Predicate<String> origPathSelector;
    private final UnaryOperator<String> origNameToMount;

    @Override
    public List<DirEntry> getEntries() throws IOException {
        // For entries of this directory, the original directory path is
        // origParentPath resolved with the original (unmapped) directory name.
        List<DirEntry> dirEntries = delegate.getEntries().stream()
                .map(e -> origPathSelector.test(delegate.path().resolve(e.name()).toString())
                        ? e.withName(origNameToMount.apply(e.name()))
                        : e)
                .toList();
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
}
