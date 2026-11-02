package com.intumit.announcement;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

/** Only the editor's finite formatting vocabulary may become rendered HTML. */
@Component("richText")
public class RichTextService {
    private static final int MAX_TEXT = 10000;
    private static final int MAX_HTML = 100000;
    private static final Set<String> BLOCKS = Set.of("p", "h2", "h3", "blockquote", "pre", "li");
    private static final Set<String> CLASSES = allowedClasses();
    private static final Cleaner CLEANER = new Cleaner(new Safelist()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "h2", "h3", "blockquote", "pre", "code", "ol", "ul", "li", "span")
            .addAttributes(":all", "class"));

    public Body normalize(String plainText, String html) {
        if (html != null && html.length() > MAX_HTML) throw new IllegalArgumentException("內文格式資料過多，請減少格式後再試");
        if (html == null || html.isBlank()) {
            String text = plainText == null ? "" : plainText.strip();
            validateText(text);
            return new Body(text, null);
        }
        String clean = safeHtml(html);
        if (clean.length() > MAX_HTML) throw new IllegalArgumentException("內文格式資料過多，請減少格式後再試");
        String text = plainText(clean);
        validateText(text);
        return new Body(text, clean);
    }

    /** Recheck on rendering as well, so imported or manually changed rows stay safe. */
    public String safeHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        Document dirty = Jsoup.parseBodyFragment(html);
        dirty.select("script,style,iframe,object,embed,svg,math,template,noscript,textarea,title,xmp,plaintext").remove();
        Document clean = CLEANER.clean(dirty);
        clean.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.html);
        for (Element element : clean.body().getAllElements()) {
            Set<String> kept = new LinkedHashSet<>();
            for (String className : element.classNames()) {
                if (CLASSES.contains(className)) kept.add(className);
            }
            element.removeAttr("class");
            if (!kept.isEmpty()) element.classNames(kept);
        }
        return clean.body().html();
    }

    private String plainText(String html) {
        var text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override public void head(Node node, int depth) {
                if (node instanceof TextNode leaf) text.append(leaf.getWholeText());
                else if (node instanceof Element element) {
                    if (element.normalName().equals("br")) text.append('\n');
                    else if (BLOCKS.contains(element.normalName())) newline(text);
                }
            }
            @Override public void tail(Node node, int depth) {
                if (node instanceof Element element && BLOCKS.contains(element.normalName())) newline(text);
            }
        }, Jsoup.parseBodyFragment(html).body());
        return text.toString().replace('\u00a0', ' ').strip();
    }

    private static void newline(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') text.append('\n');
    }

    private void validateText(String text) {
        boolean visible = text.codePoints().anyMatch(c -> !Character.isWhitespace(c)
                && !Character.isSpaceChar(c) && Character.getType(c) != Character.FORMAT);
        if (!visible) throw new IllegalArgumentException("請輸入公布內容");
        if (text.length() > MAX_TEXT) throw new IllegalArgumentException("公布內容最多 10,000 字");
    }

    private static Set<String> allowedClasses() {
        var classes = new LinkedHashSet<String>();
        for (String size : new String[]{"14", "16", "18", "20", "24", "28", "32"}) classes.add("ql-size-" + size);
        for (String color : new String[]{"black", "firebrick", "darkorange", "forestgreen", "royalblue", "indigo", "dimgray"}) classes.add("ql-color-" + color);
        for (String color : new String[]{"lightyellow", "lightcyan", "lavender", "honeydew"}) classes.add("ql-bg-" + color);
        for (String align : new String[]{"center", "right", "justify"}) classes.add("ql-align-" + align);
        for (int indent = 1; indent <= 8; indent++) classes.add("ql-indent-" + indent);
        return Set.copyOf(classes);
    }

    public record Body(String text, String html) { }
}
