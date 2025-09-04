package eu.nahoj.fusebox.vfs2.util;

import java.nio.file.Path;

import static eu.nahoj.fusebox.vfs2.transform.MappedNamesFS.EMPTY_PATH;

public class MiscUtils {

    public static Path parentPath(Path path) {
        if (path.getNameCount() > 1) {
            return path.getParent();
        } else if (!path.toString().isEmpty()) {
            return EMPTY_PATH;
        } else {
            throw new RuntimeException("Empty path");
        }
    }
}
