package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilteredFSTest {

    TestDelegateFS delegate;

    @BeforeEach
    void setUp() {
        delegate = new TestDelegateFS();
        delegate.setFile("/a", FileType.DIRECTORY, 0755);
        delegate.setFile("/b", FileType.DIRECTORY, 0755);
        delegate.setFile("/a/x.txt", FileType.REGULAR_FILE, 0644);
        delegate.setFile("/a/y.txt", FileType.REGULAR_FILE, 0644);
        delegate.setDirEntries("/", "a", "b");
        delegate.setDirEntries("/a", "x.txt", "y.txt");
    }

    @Test
    void checkPath_blocks_non_matching_paths_and_filters_readdir() throws Exception {
        // Build a FilteredFS that permits only paths starting with "a"
        FilteredFS fs = new FilteredFS(delegate, s -> s.startsWith("a"));

        // getattr on matching path delegates
        fs.getattr("/a", null);
        assertThat(delegate.calls).contains("getattr:/a");

        // getattr on non-matching path throws
        assertThatThrownBy(() -> fs.getattr("/b", null))
                .isInstanceOf(NoSuchFileException.class);

        // readdir on root is filtered to only show "a"
        List<DirEntry> root = fs.readdir("/");
        assertThat(root).extracting(DirEntry::name).containsExactly("a");

        // readdir on /a shows both files
        List<DirEntry> a = fs.readdir("/a");
        assertThat(a).extracting(DirEntry::name).containsExactlyInAnyOrder("x.txt", "y.txt");
    }
}
