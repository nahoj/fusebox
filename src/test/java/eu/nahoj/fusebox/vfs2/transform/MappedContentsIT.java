package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import eu.nahoj.fusebox.vfs2.driven.LocalFS;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static eu.nahoj.fusebox.common.util.Functions.glob;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class MappedContentsIT {

    private @Nullable FileObject rootFo;

    @AfterEach
    void tearDown() throws Exception {
        if (rootFo != null) rootFo.close();
    }

    @Test
    void maps_markdown_to_html_and_keeps_other_files() throws Exception {
        // Prepare temp fs
        Path tmp = Files.createTempDirectory("vfs2mappedtest");
        Path md = tmp.resolve("readme.md");
        String mdContent = "# Title\nHello <world> & \"you\"\n";
        Files.writeString(md, mdContent, UTF_8);
        Path txt = tmp.resolve("plain.txt");
        Files.writeString(txt, "abc", UTF_8);

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        LocalFS base = new LocalFS(rootFo);
        MarkdownToHtmlMapper mapper = new MarkdownToHtmlMapper();
        FuseboxFS fs = base.mapFiles(glob("**.md"), file -> file.mapContent(c -> c.mapAsString(mapper)));

        // Directory listing contains both files
        FuseboxFile dir = fs.resolveFile("");
        List<String> names = dir.getEntries().stream().map(DirEntry::name).toList();
        assertThat(names).contains("readme.md", "plain.txt");

        // Mapped .md attributes size equals mapped bytes length
        FuseboxFile mdMapped = fs.resolveFile("readme.md");
        var expectedHtml = mapper.apply(mdContent);
        assertThat(mdMapped.getAttributes().size()).isEqualTo(expectedHtml.getBytes(UTF_8).length);

        // Reading the mapped file yields HTML content
        try (FuseboxContent readable = mdMapped.openReadable()) {
            String html = readable.asString();
            assertThat(html).startsWith("<!doctype html>");
            assertThat(html).contains("<h1>Title</h1>");
            assertThat(html).contains("<p>Hello &lt;world&gt; &amp; &quot;you&quot;</p>");
        }

        // Non-mapped file remains unchanged
        FuseboxFile txtFile = fs.resolveFile("plain.txt");
        assertThat(txtFile.getAttributes().size()).isEqualTo(3L);
        try (FuseboxContent readable = txtFile.openReadable()) {
            assertThat(readable.asString()).isEqualTo("abc");
        }
    }
}
