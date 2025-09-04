package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import eu.nahoj.fusebox.nio.transform.ChainingFS;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseOperations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal read-only delegate FS with hidden files and directories.
 * Internal names are dot-prefixed.
 */
public class HelloHiddenFS implements ChainingFS {
    private static final String HELLO = "/.hello.txt";
    private static final String DOCS_DIR = "/.docs";
    private static final String DOCS_README = "/.docs/.readme.txt";

    private static final byte[] HELLO_CONTENT = ("Hello from a hidden file!\n").getBytes();
    private static final byte[] README_CONTENT = ("This is a hidden README file.\n").getBytes();

    @Override
    public Set<FuseOperations.Operation> supportedOperations() {
        // Keep minimal set as in HelloFuse
        return EnumSet.of(
                FuseOperations.Operation.GET_ATTR,
                FuseOperations.Operation.READ_DIR,
                FuseOperations.Operation.READ,
                FuseOperations.Operation.OPEN
        );
    }

    @Override
    public FileAttributes getattr(String path, FileInfo fi) throws IOException {
        return switch (path) {
            case "/", DOCS_DIR -> FileAttributes.minimal(FileType.DIRECTORY, 0);
            case HELLO -> FileAttributes.minimal(FileType.REGULAR_FILE, HELLO_CONTENT.length);
            case DOCS_README -> FileAttributes.minimal(FileType.REGULAR_FILE, README_CONTENT.length);
            default -> throw new NoSuchFileException(path);
        };
    }

    @Override
    public List<DirEntry> readdir(String path) throws IOException {
        ArrayList<DirEntry> out = new ArrayList<>();
        if ("/".equals(path)) {
            out.add(new DirEntry("."));
            out.add(new DirEntry(".."));
            out.add(new DirEntry(HELLO.substring(1)));     // ".hello.txt"
            out.add(new DirEntry(DOCS_DIR.substring(1)));  // ".docs"
        } else if (DOCS_DIR.equals(path)) {
            out.add(new DirEntry("."));
            out.add(new DirEntry(".."));
            String name = DOCS_README.substring(DOCS_README.lastIndexOf('/') + 1);
            out.add(new DirEntry(name));
        } else {
            throw new NoSuchFileException(path);
        }
        return out;
    }

    @Override
    public void opendir(String path, FileInfo fi) throws IOException {
        if ("/".equals(path) || DOCS_DIR.equals(path)) {
            return;
        }
        throw new NoSuchFileException(path);
    }

    @Override
    public void open(String path, FileInfo fi) throws IOException {
        if (HELLO.equals(path) || DOCS_README.equals(path)) {
            return;
        }
        throw new NoSuchFileException(path);
    }

    @Override
    public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) throws IOException {
        byte[] src;
        if (HELLO.equals(path)) {
            src = HELLO_CONTENT;
        } else if (DOCS_README.equals(path)) {
            src = README_CONTENT;
        } else {
            throw new NoSuchFileException(path);
        }
        if (offset >= src.length) {
            return 0;
        }
        int n = (int) Math.min(size, src.length - offset);
        buf.put(src, (int) offset, n);
        return n;
    }
}
