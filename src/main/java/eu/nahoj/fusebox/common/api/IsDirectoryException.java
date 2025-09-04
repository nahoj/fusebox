package eu.nahoj.fusebox.common.api;

import java.io.IOException;

public class IsDirectoryException extends IOException {

    public IsDirectoryException() {
        super("Is a directory");
    }
}
