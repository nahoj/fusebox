# fusebox

This is a Java library that aims to make it easy to transform "views" of file hierarchies in a functional, composable way, and mount them as FUSE filesystems. It is built on [jfuse](https://github.com/cryptomator/jfuse) and [VFS](https://commons.apache.org/proper/commons-vfs/).

Transformations can be, for instance:
- filtering files
- changing file names or contents
- making some files or the whole FS read-only

Is it actually useful? Who knows?

As an example, here is the main part of a JBang script that mounts a view of an existing directory where all Markdown files are transformed to HTML:

```java
    FuseboxFS fs = LocalFS.at(args[0])
            .mapFiles(glob("**.md"), file ->  // Selecting all .md files
                    file.mapContent(content -> content.mapAsString(md ->
                            mdToHtml(file.name(), md)
                    ))
            )
            // Renaming needs to be defined both ways
            .mapNames(glob("**.md"), glob("**.html"),
                    name -> name.substring(0, name.length() - 3) + ".html",
                    name -> name.substring(0, name.length() - 5) + ".md"
            );
    Fusebox.mount("markdown_to_html", fs, args[1]);
```

Full script: [scripts/vfs2/markdown_to_html.java](scripts/vfs2/markdown_to_html.java).

> ⚠️ This is very experimental and barely tested. Don’t run code from this repo unless you’re okay with the risk of system crash or data loss. ⚠️️️

## Two designs in this repo

### VFS-style Filesystem/File/Content interface

The above example uses a design with transformations at 3 levels (file system - file - file content) inspired by Apache's VFS lib, but with simpler interfaces. The implementation also uses VFS to access the local file system.

The current implementation covers only some operations (read and delete files, dir. operations, readlink, chmod) and transformations, but it could be extended to cover more ground.

Since it has a VFS adapter, it could also probably be used to mount any existing VFS filesystem implementation.

This part of the lib can be found in `eu.nahoj.fusebox.vfs2`.

### The earlier (nio-backed) design, with most operations implemented

My first design had transformations only at the FS level. It is less elegant, but I left it for comparison as I implemented a majority of jfuse operations in it.

This is a script that takes a directory with hidden files (e.g. your home directory) and shows them non-hidden at the mount point. You can then access them normally, read/write.

```java
    FuseboxFS fs = LocalFS.at(args[0])
            .filterPaths(glob(".**")) // Hidden files at the root and all their descendants
            .mapFileNames(
                    glob("*"), // Children of the root
                    glob("*"),
                    origName -> origName.substring(1),
                    mountName -> "." + mountName
            )
            .withReadOnlyDirs(""::equals); // Make the root directory read-only
    Fusebox.mount("renamed-hidden-demo", fs, args[1]);
```

Full script: [scripts/nio/renamed-hidden-demo.jsh](scripts/nio/renamed-hidden-demo.jsh), lib code in `eu.nahoj.fusebox.nio`.

## Prerequisites

- Linux (other Unixes might work)
- libfuse3 from your package manager
- Java 22+, Maven, JBang to run scripts
  - They can be installed with [SdkMan](https://sdkman.io/)
- Development: direnv, go-task for convenience

## Environment variables

For build and running demo scripts, you can use direnv from the project root.

As a general rule:

1. Your JAVA_HOME must be set.
2. jfuse must be able to find `libfuse3.so` in `java.library.path`. To achieve this you can do one of the following:
   - Add the directory that contains `libfuse3.so` to `LD_LIBRARY_PATH`
     - (`LD_LIBRARY_PATH` typically gets added to `java.library.path`.)
   - Just symlink the library file to a directory that is already in `java.library.path`.
     - (`java.library.path` will be printed if you run any of the scripts and `libfuse3.so` is not in it.)
   - Add `//RUNTIME_OPTIONS -Djava.library.path=...` to a JBang script.
   - Etc.

## Build

```bash
direnv allow
direnv exec . mvn -Ps install
```

## Run

1. See the warning earlier.
2. You can run the scripts in `scripts/` by giving them a source directory and a mount directory on the command line. 
