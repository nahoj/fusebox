package eu.nahoj.fusebox.common.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.function.Predicate;

public class Functions {

    public static Predicate<String> glob(String pattern) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        return path -> pathMatcher.matches(Path.of(path));
    }
}
