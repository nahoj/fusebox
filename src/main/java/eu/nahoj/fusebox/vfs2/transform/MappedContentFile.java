package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * File wrapper that maps its contents. This file is read-only.
 * For simplicity, mapped contents are computed lazily and cached in-memory per wrapper instance.
 */
@RequiredArgsConstructor
public class MappedContentFile implements DecoratedFile {

    private final FuseboxFile delegate;
    private final UnaryOperator<FuseboxContent> mapper;

    // TODO add TTL / use readymade impl
    private final AtomicReference<FuseboxContent> cachedMapped = new AtomicReference<>();

    @Override
    public FuseboxFS fs() {
        return delegate().fs();
    }

    @Override
    public FuseboxFile delegate() {
        return delegate;
    }

    @Override
    public FileAttributes getAttributes() throws IOException {
        return delegate.getAttributes()
                .readOnly()
                .withSize(getOrComputeMappedContent().size());
    }

    @Override
    public FuseboxContent openReadable() throws IOException {
        return getOrComputeMappedContent();
    }

    private FuseboxContent getOrComputeMappedContent() throws IOException {
        FuseboxContent c = cachedMapped.get();
        if (c != null) return c;
        synchronized (cachedMapped) {
            c = cachedMapped.get();
            if (c != null) return c;
            try (FuseboxContent src = delegate.openReadable()) {
                FuseboxContent mapped = mapper.apply(src);
                cachedMapped.set(mapped);
                return mapped;
            }
        }
    }
}
