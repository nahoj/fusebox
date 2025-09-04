package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.util.SimpleFileInfo;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.springframework.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.cryptomator.jfuse.api.FuseOperations.Operation.GET_ATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.OPEN;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READ;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.RELEASE;

/**
 * High-level content view: for selected paths, expose transformed, read-only bytes.
 *
 * This layer does not rename files. Compose with {@link RenamedFS} if you want
 * different names/extensions on top of the transformed content.
 */
@Accessors(fluent = true)
@RequiredArgsConstructor
public class ContentViewFS extends BaseFS implements DecoratedFS {

    @Getter
    private final FuseboxFS delegate;

    private final Predicate<String> pathSelector;
    private final ContentGenerator generator;

    private final EnumSet<Operation> supportedOps = EnumSet.of(GET_ATTR, OPEN, READ, RELEASE);

    private final AtomicLong fhGen = new AtomicLong(1L);
    private final ConcurrentMap<Long, byte[]> openContent = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> renderedSizeByPath = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> fhToPath = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> openCountByPath = new ConcurrentHashMap<>();

    @Override
    public Set<Operation> supportedOperations() {
        // Ensure core ops are declared supported
        return SetUtils.union(delegate.supportedOperations(), supportedOps);
    }

    private boolean matches(String path) {
        return pathSelector.test(path);
    }

    // -------------- Attributes --------------

    @Override
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        FileAttributes base = delegate().getattr(path, fi);
        // For matching files, ensure size reflects transformed content early to avoid truncation.
        if (!base.isDirectory() && matches(path)) {
            Long cached = renderedSizeByPath.get(path);
            if (cached != null) {
                return base.withSize(cached);
            }

            // Heuristic: only compute eagerly for small files; otherwise over-estimate to avoid truncation.
            final long MAX_EAGER_SIZE = 512L * 1024; // 512 KiB
            if (base.size() <= MAX_EAGER_SIZE) {
                byte[] source = readAllBytesFromDelegate(path);
                byte[] out = generator.generate(path, source);
                long sz = (long) out.length;
                renderedSizeByPath.put(path, sz);
                return base.withSize(sz);
            } else {
                // Over-estimate to ensure readers don't stop early; refined on open()
                long est;
                long s = base.size();
                // 2x + 4KiB, with overflow guard
                if (s > Long.MAX_VALUE / 2) est = Long.MAX_VALUE; else est = s * 2 + 4096;
                renderedSizeByPath.put(path, est);
                return base.withSize(est);
            }
        }
        return base;
    }

    // -------------- Files --------------

    private static final Set<StandardOpenOption> WRITE_INTENT = EnumSet.of(
            StandardOpenOption.APPEND,
            StandardOpenOption.CREATE,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
    );

    @Override
    public void open(String path, FileInfo fi) throws IOException {
        if (!matches(path)) {
            delegate().open(path, fi);
            return;
        }
        // If caller intends to write, pass-through to delegate (we only transform read views)
        if (!SetUtils.intersection(fi.getOpenFlags(), WRITE_INTENT).isEmpty()) {
            delegate().open(path, fi);
            return;
        }
        // Read full source bytes from delegate using a temporary handle
        byte[] source = readAllBytesFromDelegate(path);
        byte[] out = generator.generate(path, source);

        long fh = fhGen.incrementAndGet();
        fi.setFh(fh);
        openContent.put(fh, out);
        fhToPath.put(fh, path);
        renderedSizeByPath.put(path, (long) out.length);
        openCountByPath.compute(path, (p, c) -> {
            if (c == null) c = new AtomicLong(0);
            c.incrementAndGet();
            return c;
        });
    }

    private byte[] readAllBytesFromDelegate(String path) throws IOException {
        FileInfo ufi = new SimpleFileInfo();
        try {
            delegate().open(path, ufi);
            long offset = 0L;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
            while (true) {
                buf.clear();
                int r = delegate().read(path, buf, buf.remaining(), offset, ufi);
                if (r <= 0) break;
                bos.write(buf.array(), 0, r);
                offset += r;
            }
            return bos.toByteArray();
        } finally {
            try { delegate().release(path, ufi); } catch (Exception ignore) {}
        }
    }

    @Override
    public int read(String path, ByteBuffer dst, long count, long offset, FileInfo fi) throws IOException {
        byte[] data = openContent.get(fi.getFh());
        if (data == null) {
            return delegate().read(path, dst, count, offset, fi);
        }
        if (offset >= data.length) return 0;
        int toCopy = (int) Math.min(Math.min(count, dst.remaining()), data.length - offset);
        dst.put(data, (int) offset, toCopy);
        return toCopy;
    }

    @Override
    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        if (openContent.containsKey(fi.getFh())) {
            throw new ReadOnlyFileSystemException();
        }
        // Invalidate any cached transformed size as source may change
        if (matches(path)) {
            renderedSizeByPath.remove(path);
        }
        return delegate().write(path, buf, count, offset, fi);
    }

    @Override
    public void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        if (fi != null && openContent.containsKey(fi.getFh())) {
            throw new ReadOnlyFileSystemException();
        }
        if (matches(path)) {
            renderedSizeByPath.remove(path);
        }
        delegate().truncate(path, size, fi);
    }

    @Override
    public void release(String path, FileInfo fi) throws IOException {
        byte[] removed = openContent.remove(fi.getFh());
        if (removed != null) {
            String p = fhToPath.remove(fi.getFh());
            if (p != null) {
                AtomicLong cnt = openCountByPath.get(p);
                if (cnt != null && cnt.decrementAndGet() <= 0) {
                    openCountByPath.remove(p, cnt);
                    renderedSizeByPath.remove(p);
                }
            }
            // Nothing else to do; we closed the temporary handle earlier
            return;
        }
        delegate().release(path, fi);
    }
}
