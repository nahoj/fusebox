package eu.nahoj.fusebox.common;

import eu.nahoj.fusebox.common.util.ExceptionUtils.ThrowingSupplier;
import org.cryptomator.jfuse.api.Errno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import static eu.nahoj.fusebox.common.util.NullUtils.mapOrNull;

/**
 * Utility methods for mapping exceptions to errno values.
 */
public final class ExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    private ExceptionHandler() {}

    public static int catchErrno(Errno errno, ThrowingSupplier<Integer> action) {
        try {
            return action.get();
        } catch (Exception e) {
            return translateException(errno, e);
        }
    }

    private static int translateException(Errno errno, Exception exception) {
        LOG.trace("Matching exception {}: {}", exception.getClass().getName(), exception.getMessage());
        return switch (exception) {
            // Wrapped exceptions
            case java.io.UncheckedIOException ex -> translateException(errno, ex.getCause());
            case java.nio.file.DirectoryIteratorException ex -> translateException(errno, ex.getCause());

            // EACCES
            case java.nio.file.AccessDeniedException _ -> -errno.eacces();

            // EIO (async close during blocking I/O) — must be before ClosedChannelException
            case java.nio.channels.AsynchronousCloseException _ -> -errno.eio(); // no EINTR available

            // EBADF
            case eu.nahoj.fusebox.common.api.BadFileDescriptorException _,
                 java.nio.channels.ClosedChannelException _,
                 java.nio.file.ClosedDirectoryStreamException _ // operating on closed dir stream
                    -> -errno.ebadf();

            // EEXIST
            case java.nio.file.FileAlreadyExistsException _ -> -errno.eexist();

            // EINVAL
            case java.nio.file.InvalidPathException _,
                 java.nio.file.NotLinkException _, // POSIX readlink on non-symlink
                 java.nio.file.ProviderMismatchException _ // wrong provider for given Path
                    -> -errno.einval();

            // EISDIR
            case eu.nahoj.fusebox.common.api.IsDirectoryException _,
                 org.apache.commons.vfs2.FileTypeHasNoContentException _ -> -errno.eisdir(); // Unsure

            // ENOENT
            case java.nio.file.NoSuchFileException _,
                 org.apache.commons.vfs2.FileNotFoundException _ -> -errno.enoent();

            // ENOLCK
            case java.nio.channels.FileLockInterruptionException _,
                 java.nio.channels.OverlappingFileLockException _ -> -errno.enolck();

            // ENOMEM: Do not catch OutOfMemoryError

            // ENOTDIR
            case java.nio.file.NotDirectoryException _,
                 org.apache.commons.vfs2.FileNotFolderException _ -> -errno.enotdir();

            // ENOTEMPTY
            case java.nio.file.DirectoryNotEmptyException _ -> -errno.enotempty();

            // ERANGE
            case java.nio.BufferOverflowException _ -> -errno.erange();

            // EROFS
            case java.nio.file.ReadOnlyFileSystemException _ -> -errno.erofs();

            // ENOSYS
            case org.apache.commons.lang3.NotImplementedException _ -> -errno.enosys();

            // ENOTSUP
            case java.lang.UnsupportedOperationException _,
                 java.nio.file.AtomicMoveNotSupportedException _ // Should be EXDEV
                    -> -errno.enotsup();

            // Other IOException
            case java.nio.file.FileSystemLoopException _ -> -errno.eio(); // no ELOOP in Errno
            case java.nio.file.FileSystemException ex -> translateText(errno, ex.getReason());
            case org.apache.commons.vfs2.FileSystemException ex -> translateVfs2Code(errno, ex.getCode());
            case java.io.IOException ex -> mapOrNull(ex.getMessage(), msg ->
                    translateText(errno, msg.substring(Math.max(0, msg.length()-40)))
            );

            // Default catch-all
            case RuntimeException ex -> throw ex;
            // Includes nio: not path operation errors; indicate misconfiguration/usage
            // java.nio.file.ClosedWatchServiceException -> not part of path I/O operations
            // java.nio.file.FileSystemAlreadyExistsException -> programmatic: creating FS twice
            // java.nio.file.FileSystemNotFoundException -> programmatic: FS for URI not found
            // java.nio.file.ProviderNotFoundException -> environment/provider missing

            default -> -errno.eio();
        };
    }
    
    private static int translateVfs2Code(Errno errno, String code) {
        if (code.contains("read-only")) {
            return -errno.eacces();
        }
        if (code.contains("dest-exists")) {
            return -errno.eexist();
        }
        if (code.contains("attribute-no-exist") || code.contains("attributes-no-exist")) {
            return -errno.enoattr(); // Alias for enodata() in LinuxErrno
        }
        if (code.contains("not-supported") || code.contains("missing-capability")) {
            return -errno.enotsup();
        }
        return -errno.eio();
    }

    private static int translateText(Errno errno, @Nullable String text) {
        if (text == null) {
            return -errno.eio();
        }
        String t = text.toLowerCase();
        if (t.contains("argument list too long")) {
            return -errno.e2big();
        }
        if (t.contains(/*operation*/ "not permitted") || t.contains("permission"/* denied*/)) {
            return -errno.eacces();
        }
        if (t.contains("file descriptor")) { // Bad file descriptor / File descriptor in bad state
            return -errno.ebadf();
        }
        if (t.contains("file exists")) {
            return -errno.eexist();
        }
        if (t.contains("invalid argument")) {
            return -errno.einval();
        }
        if (t.contains("is a directory")) {
            return -errno.eisdir();
        }
        if (t.contains("file name") && t.contains("too long")) {
            return -errno.enametoolong();
        }
        if (t.contains("attribute not found")) {
            return -errno.enoattr();
        }
        if (t.contains("no data"/* available*/) || t.contains("no message available")) {
            return -errno.enodata();
        }
        if (t.contains("no such file"/* or directory*/)) {
            return -errno.enoent();
        }
        if (t.contains("no locks"/* available*/)) {
            return -errno.enolck();
        }
        if (t.contains(/*function */"not implemented")) {
            return -errno.enosys();
        }
        if (t.contains("not a directory")) {
            return -errno.enotdir();
        }
        if (t.contains(/*directory */"not empty")) {
            return -errno.enotempty();
        }
        if (t.contains(/*operation */"not supported")) {
            return -errno.enotsup();
        }
        if (t.contains("result too large")) {
            return -errno.erange();
        }
        if (t.contains("read-only file system")) {
            return -errno.erofs();
        }
        return -errno.eio();
    }
}
