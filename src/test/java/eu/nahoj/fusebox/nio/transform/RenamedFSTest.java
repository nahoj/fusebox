package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenamedFSTest {

    TestDelegateFS delegate;

    @BeforeEach
    void setUp() {
        delegate = new TestDelegateFS();
        delegate.setFile("/", FileType.DIRECTORY, 0755);
        delegate.setFile("/orig", FileType.DIRECTORY, 0755);
        delegate.setFile("/orig/a.txt", FileType.REGULAR_FILE, 0644);
        delegate.setFile("/orig/b.txt", FileType.REGULAR_FILE, 0644);
        delegate.setDirEntries("/", "orig");
        delegate.setDirEntries("/orig", "a.txt", "b.txt");
    }

    private RenamedFS renamedFS(UnaryOperator<String> toMount, UnaryOperator<String> toOrig,
                                Predicate<String> selector) {
        return new RenamedFS(delegate, selector, selector, toMount, toOrig);
    }

    @Test
    void maps_paths_through_decorator() throws Exception {
        // Map directory name "mount" <-> "orig"
        RenamedFS fs = renamedFS(name -> name.equals("orig") ? "mount" : name,
                name -> name.equals("mount") ? "orig" : name,
                _ -> true);

        // getattr("/mount") should delegate to "/orig"
        fs.getattr("/mount", null);
        assertThat(delegate.calls).contains("getattr:/orig");

        // readdir on "/mount" renames child file names
        List<DirEntry> entries = fs.readdir("/mount");
        assertThat(entries).extracting(DirEntry::name).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void readdir_throws_on_duplicate_mapped_names() {
        // Mapping that renames every child to the same name
        RenamedFS fs = renamedFS(_n -> "same", _n -> _n, _p -> true);

        // delegate has two entries under /orig; mount path will be "/same" tree (due to mapping)
        // Try to list "/orig" at mount-side via original-path mapping: map "/orig" -> "/same"
        // For simplicity, query mount path that maps to "/orig":
        // We'll map name -> same always, so "/orig" remains "/orig" (selector true but dir name not changed here),
        // but child names collide -> should throw
        assertThatThrownBy(() -> fs.readdir("/orig"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate entries");
    }
}
