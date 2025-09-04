///usr/bin/env LC_ALL=C jbang "$0" "$@" ; exit $?
// LC_ALL=C is not mandatory but improves error handling in some cases
//JAVA 24
//DEPS eu.nahoj:fusebox:1.0-SNAPSHOT
//DEPS org.commonmark:commonmark:0.25.1
//RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED
// RUNTIME_OPTIONS -Dorg.slf4j.simpleLogger.log.eu.nahoj.fusebox=trace
//RUNTIME_OPTIONS -Dorg.slf4j.simpleLogger.showDateTime=true
//RUNTIME_OPTIONS -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS

package vfs2;

import eu.nahoj.fusebox.vfs2.driven.LocalFS;
import eu.nahoj.fusebox.vfs2.driving.Fusebox;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import static eu.nahoj.fusebox.common.util.Functions.glob;

public class markdown_to_html {
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private static String mdToHtml(String title, String md) {
        var sb = new StringBuilder("<html><head><title>")
                .append(title)
                .append("</title></head><body>\n");
        RENDERER.render(PARSER.parse(md), sb);
        return sb.append("\n</body></html>").toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: markdown_to_html.java <source> <mountpoint>");
            System.exit(1);
        }

        var fs = LocalFS.at(args[0])
                .mapFiles(glob("**.md"), file ->
                        file.mapContent(content -> content.mapAsString(md ->
                                mdToHtml(file.name(), md)
                        ))
                )
                .mapNames(glob("**.md"), glob("**.html"),
                        name -> name.substring(0, name.length() - 3) + ".html",
                        name -> name.substring(0, name.length() - 5) + ".md"
                );

        Fusebox.mount("markdown_to_html.java", fs, args[1]);
    }
}
