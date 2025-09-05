package eu.nahoj.fusebox.vfs2.driving;

import eu.nahoj.fusebox.common.Driver;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import org.cryptomator.jfuse.api.Fuse;

public class Fusebox {

    public static void mount(String progName, FuseboxFS fs, String mountPoint) {
        FuseboxOperations fuseOperations = new FuseboxOperations(fs, Fuse.builder().errno());
        Driver.mount(progName, fuseOperations, mountPoint);
    }
}
