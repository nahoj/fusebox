package eu.nahoj.fusebox.vfs2.transform;

import java.util.function.UnaryOperator;

/**
 * Dummy Markdown -> HTML mapper.
 *
 * Rules:
 * - Escapes HTML special chars.
 * - Lines starting with 1..6 '#' become <h1>.. <h6>.
 * - Other non-empty lines become <p>text</p>.
 * - Blank lines preserved as empty lines between blocks.
 */
public class MarkdownToHtmlMapper implements UnaryOperator<String> {

    @Override
    public String apply(String md) {
        String[] lines = md.split("\r?\n", -1);
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html><html><head><meta charset=\"utf-8\"></head><body>\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                out.append("\n");
                continue;
            }
            int hashes = countLeading(line, '#');
            if (hashes >= 1 && hashes <= 6 && (line.length() == hashes || line.charAt(hashes) == ' ')) {
                String text = line.substring(Math.min(hashes + 1, line.length())).trim();
                out.append('<').append('h').append((char) ('0' + hashes)).append('>')
                        .append(escapeHtml(text))
                        .append("</h").append((char) ('0' + hashes)).append(">\n");
            } else {
                out.append("<p>").append(escapeHtml(line)).append("</p>\n");
            }
        }
        out.append("</body></html>\n");
        return out.toString();
    }

    private static int countLeading(String s, char c) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == c) i++;
        return i;
    }

    private static String escapeHtml(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }
}
