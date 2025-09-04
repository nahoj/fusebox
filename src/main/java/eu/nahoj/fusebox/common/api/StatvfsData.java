package eu.nahoj.fusebox.common.api;

import lombok.Builder;
import lombok.Data;
import org.cryptomator.jfuse.api.Statvfs;

@Data
@Builder
public class StatvfsData implements Statvfs {
    private long bsize;
    private long frsize;
    private long blocks;
    private long bfree;
    private long bavail;
    private long nameMax;

    public static void copy(Statvfs from, Statvfs to) {
        to.setBsize(from.getBsize());
        to.setFrsize(from.getFrsize());
        to.setBlocks(from.getBlocks());
        to.setBfree(from.getBfree());
        to.setBavail(from.getBavail());
        to.setNameMax(from.getNameMax());
    }
}
