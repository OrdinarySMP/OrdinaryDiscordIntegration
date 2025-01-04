package tronka.ordinarydiscordintegration.chat;

import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.ParserBuilder;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class TextReplacer {
    private final ParserBuilder builder;
    private final Map<String, TextNode> placeholders;
    private TagLikeParser.Format format;

    private TextReplacer(TagLikeParser.Format format) {
        placeholders = new HashMap<>();
        builder = NodeParser.builder()
                .globalPlaceholders()
                .placeholders(format, placeholders::get)
                .simplifiedTextFormat();
    }

    public TextReplacer replace(String key, String value) {
        return replace(key, TextNode.of(value));
    }


    public TextReplacer replace(String key, TextNode node) {
        placeholders.put(key, node);
        return this;
    }

    private NodeParser getParser() {
        return builder.build();
    }

    public Text apply(String text) {
        return getParser().parseNode(text).toText();
    }

    public TextNode applyNode(String text) {
        return getParser().parseNode(text);
    }

    public static TextReplacer create() {
        return new TextReplacer(TagLikeParser.PLACEHOLDER);
    }

    public static TextReplacer create(TagLikeParser.Format format) {
        return new TextReplacer(format);
    }
}
