///usr/bin/env LC_ALL=C JBANG_RUNTIME_OPTIONS="--enable-native-access=ALL-UNNAMED" jbang "$0" "$@" ; exit $?
// LC_ALL=C is not mandatory but improves error handling
//JAVA 24
//DEPS eu.nahoj:fusebox:1.0-SNAPSHOT

import eu.nahoj.fusebox.nio.api.FuseboxFS;
import eu.nahoj.fusebox.nio.driven.LocalFS;
import eu.nahoj.fusebox.nio.driving.Fusebox;

import static eu.nahoj.fusebox.common.util.Functions.glob;
import static java.nio.charset.StandardCharsets.UTF_8;

// ⚠️ Exit before editing as changes run live! ⚠️
public static void main(String[] args) {
    if (args.length != 2) {
        System.err.println("Usage: markdown-to-html.jsh <source> <mountpoint>");
        System.err.println("Hint: ensure libfuse is installed and JAVA_LIBRARY_PATH points to its directory.");
        System.exit(1);
    }

    FuseboxFS fs = LocalFS.at(args[0])
            .mapFileContents(
                    glob("**.md"),
                    (path, src) ->
                            ("<html><body><pre>" + new String(src, UTF_8) + "</pre></body></html>").getBytes(UTF_8)
            )
            .mapFileNames(
                    glob("**.md"), // Children of the root
                    glob("**.html"),
                    origName -> origName.substring(0, origName.length() - 3) + ".html",
                    mountName -> mountName.substring(0, mountName.length() - 5) + ".md"
            )
            ;

    Fusebox.mount("markdown-to-html.jsh", fs, args[1]);
}

main(args);
