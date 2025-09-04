package eu.nahoj.fusebox.nio.transform;

import java.io.IOException;

/**
 * High-level, read-only content generator.
 * Implementations take the source file's bytes and return the transformed bytes to expose.
 *
 * Examples:
 * - Render Markdown to HTML
 * - Transpile a template into text
 * - Filter/normalize textual content
 */
@FunctionalInterface
public interface ContentGenerator {
    /**
     * @param path   the FS path (as seen by this layer), e.g. "/docs/readme.md"
     * @param source the raw source file bytes as read from the delegate FS
     * @return transformed bytes to expose for reads
     */
    byte[] generate(String path, byte[] source) throws IOException;
}
