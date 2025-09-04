package eu.nahoj.fusebox.vfs2.driving;

import eu.nahoj.fusebox.common.Driver;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.driving.FuseboxFSOperations;
import org.cryptomator.jfuse.api.Fuse;

public class Fusebox {

    public static void mount(String progName, FuseboxFS fs, String mountPoint) {
        FuseboxFSOperations fuseOperations = new FuseboxFSOperations(fs, Fuse.builder().errno());
        Driver.mount(progName, fuseOperations, mountPoint);
    }
}
