///usr/bin/env LC_ALL=C JBANG_RUNTIME_OPTIONS="-Djava.library.path=$JAVA_LIBRARY_PATH --enable-native-access=ALL-UNNAMED" jbang "$0" "$@" ; exit $?
// LC_ALL=C is not mandatory but improves error handling
//JAVA 24
//DEPS eu.nahoj:fusebox:1.0-SNAPSHOT

import eu.nahoj.fusebox.nio.api.FuseboxFS;
import eu.nahoj.fusebox.nio.driven.LocalFS;
import eu.nahoj.fusebox.nio.driving.Fusebox;

import static eu.nahoj.fusebox.common.util.Functions.glob;

// Fusebox demo: expose a delegate FS that contains only hidden entries (names starting with '.')
// and make them visible without the leading dot via RenamedFS.
// Requires libfuse to be installed and reachable via JAVA_LIBRARY_PATH or java.library.path.

// ⚠️ Exit before editing as changes run live! ⚠️
public static void main(String[] args) {
    if (args.length != 2) {
        System.err.println("Usage: renamed-hidden-demo.jsh <source> <mountpoint>");
        System.err.println("Hint: ensure libfuse is installed and JAVA_LIBRARY_PATH points to its directory.");
        System.exit(1);
    }

    FuseboxFS fs = LocalFS.at(args[0])
            .filterPaths(glob(".**")) // Hidden
            .mapFileNames(
                    glob("*"), // Children of the root
                    glob("*"),
                    origName -> origName.substring(1),
                    mountName -> "." + mountName
            )
            .withReadOnlyDirs(""::equals) // Root
            ;

    Fusebox.mount("renamed-hidden-demo.jsh", fs, args[1]);
}

main(args);
