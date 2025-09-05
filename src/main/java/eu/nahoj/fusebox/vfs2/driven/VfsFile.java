package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.util.RandomAccessMode;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static eu.nahoj.fusebox.common.api.FileType.DIRECTORY;
import static eu.nahoj.fusebox.common.api.FileType.REGULAR_FILE;
import static java.nio.file.attribute.PosixFilePermission.*;
import static java.util.Objects.requireNonNull;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.*;

@Accessors(fluent = true)
@AllArgsConstructor
public class VfsFile implements FuseboxFile {

    private static final Logger LOG = LoggerFactory.getLogger(VfsFile.class);

    public static final Set<Operation> IMPLEMENTED_OPERATIONS =
            EnumSet.of(GET_ATTR, CHMOD, MKDIR, OPEN_DIR, READ_DIR, RELEASE_DIR, RMDIR, OPEN, READ, RELEASE, UNLINK);

    @Getter
    private final VfsFS fs;
    @Getter
    private final Path path;
    protected final FileObject fo;

//    @Override
//    public Path path() {
//        String n = fo.getName().getBaseName();
//        LOG.trace("name() -> {}", n);
//        return n;
//    }

    @Override
    public FileAttributes getAttributes() throws IOException {
        LOG.trace("getAttributes({})", fo.getName());
        FileType type = switch (fo.getType()) {
            case FILE -> REGULAR_FILE;
            case FOLDER -> DIRECTORY;
            case IMAGINARY -> throw new FileNotFoundException(fo.getName());
            case FILE_OR_FOLDER -> {
                String message = "vfs2.FileType.FILE_OR_FOLDER not supported: " + fo.getName();
                LOG.error(message);
                throw new UnsupportedOperationException(message);
            }
        };
        long size;
        if (type == REGULAR_FILE) {
            try {
                size = fo.getContent().getSize();
            } catch (FileSystemException e) {
                // Size may be unavailable; signal unknown with -1
                FileAttributes attrs = FileAttributes.minimal(type, -1);
                LOG.trace("getAttributes({}) -> type={}, size=-1 (unknown)", fo.getName(), type);
                return attrs;
            }
        } else {
            size = 0L;
        }
        LOG.trace("getAttributes({}) -> type={}, size={}", fo.getName(), type, size);
        return FileAttributes.minimal(type, size);
    }

    @Override
    public void setPermissions(Set<PosixFilePermission> permissions) throws IOException {
        fo.setReadable(permissions.contains(OTHERS_READ), false);
        fo.setReadable(permissions.contains(OWNER_READ), true);
        fo.setWritable(permissions.contains(OTHERS_WRITE), false);
        fo.setWritable(permissions.contains(OWNER_WRITE), true);
        fo.setExecutable(permissions.contains(OTHERS_EXECUTE), false);
        fo.setExecutable(permissions.contains(OWNER_EXECUTE), true);
    }

    // Directories

    @Override
    public void createDirectory(Set<PosixFilePermission> permissions) throws IOException {
        fo.createFolder();
        setPermissions(permissions);
    }

    @Override
    public boolean existsAndIsDirectory() throws IOException {
        return fo.exists() && fo.isFolder();
    }

    @Override
    public List<DirEntry> getEntries() throws FileSystemException {
        LOG.trace("getEntries({})", fo.getName());
        List<DirEntry> list = Arrays.stream(fo.getChildren())
                .map(fo -> new DirEntry(fo.getName().getBaseName()))
                .toList();
        LOG.trace("getEntries({}) -> {} entries", fo.getName(), list.size());
        return list;
    }

    // Files

    @Override
    public FuseboxContent openReadable() throws FileSystemException {
        LOG.trace("openReadable({})", fo.getName());
        if (!fo.getType().hasContent()) {
            throw new FileTypeHasNoContentException("Not a regular file: " + fo.getName());
        }
        RandomAccessContent rac = requireNonNull(fo.getContent().getRandomAccessContent(RandomAccessMode.READ));
        FuseboxContent readable = new RacContent(rac);
        LOG.trace("openReadable({}) -> success", fo.getName());
        return readable;
    }

    @Override
    public void delete() throws IOException {
        fo.delete();
    }
}
