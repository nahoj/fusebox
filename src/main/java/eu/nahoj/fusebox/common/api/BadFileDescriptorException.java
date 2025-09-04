package eu.nahoj.fusebox.common.api;

import java.nio.file.FileSystemException;

/**
 * Exception thrown when an invalid or bad file handle is encountered.
 * This typically occurs when attempting to perform operations on a file
 * with a handle that is no longer valid or never existed.
 */
public class BadFileDescriptorException extends FileSystemException {
    
    public BadFileDescriptorException(String file) {
        super(file, null, "Bad file descriptor");
    }
}
