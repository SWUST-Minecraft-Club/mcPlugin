package com.example.yudreamui.protocol;

import com.google.gson.*;
import java.io.StringReader;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/** Versioned semantic view shared by native, inventory, dialog, and optional plugin adapters. */
public record FallbackPlan(int schema, String title, String body, List<Field> fields, List<Button> buttons) {
    public static final int SCHEMA = 1;
    public record Field(String id, String label, String type, String initial, int maxLength, float min, float max, float step) {}
    public record Button(String id, String label, String action, String icon, boolean disabled, List<String> inputs) {
        public Button { inputs = List.copyOf(inputs); }
    }
    public FallbackPlan {
        fields = List.copyOf(fields); buttons = List.copyOf(buttons);
        if (schema != SCHEMA || fields.size() > 128 || buttons.size() > 128) throw new IllegalArgumentException("Invalid fallback schema");
        var ids = new HashSet<String>();
        for (var field : fields) {
            if (!field.id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(field.id)) throw new IllegalArgumentException("Invalid/duplicate field ID");
            if (!Set.of("text", "checkbox", "range").contains(field.type) || field.maxLength < 1 || field.maxLength > 1024
                || !Float.isFinite(field.min) || !Float.isFinite(field.max) || field.min > field.max || !Float.isFinite(field.step) || !(field.step > 0)) throw new IllegalArgumentException("Invalid fallback field");
            if(field.type.equals("checkbox")&&!Set.of("true","false").contains(field.initial))throw new IllegalArgumentException("Invalid checkbox default");
            if(field.type.equals("range")&&!field.initial.isBlank()) {
                float initial=Float.parseFloat(field.initial);
                if(!Float.isFinite(initial)||initial<field.min||initial>field.max)throw new IllegalArgumentException("Invalid range default");
            }
        }
        var controls = new HashSet<String>();
        for (var button : buttons) {
            if (!button.id.matches("[A-Za-z0-9_-]{1,64}") || !controls.add(button.id) || !button.action.matches("server:[A-Za-z0-9_-]{1,64}") || !ids.containsAll(button.inputs))
                throw new IllegalArgumentException("Invalid fallback button");
        }
    }
    public Button button(String id) { return buttons.stream().filter(b -> b.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown UI control")); }
    public Field field(String id) { return fields.stream().filter(f -> f.id.equals(id)).findFirst().orElseThrow(); }
    public JsonObject validate(String id, String action, JsonObject submitted) {
        var button = button(id);
        if (button.disabled || !button.action.equals(action)) throw new IllegalArgumentException("Inactive UI action");
        var result = new JsonObject();
        for (String key : button.inputs) {
            var field = field(key); var value = submitted.get(key);
            if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing UI field: " + key);
            switch (field.type) {
                case "checkbox" -> {
                    if (!value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid checkbox");
                    result.addProperty(key, value.getAsBoolean());
                }
                case "range" -> {
                    float number = value.getAsFloat();
                    if (!Float.isFinite(number) || number < field.min || number > field.max) throw new IllegalArgumentException("UI range out of bounds");
                    result.addProperty(key, number);
                }
                default -> {
                    if(!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid text type");
                    String text = value.getAsString();
                    if (text.codePointCount(0, text.length()) > field.maxLength || text.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid UI text");
                    result.addProperty(key, text);
                }
            }
        }
        return result;
    }
    public String renderBody(JsonObject state) {
        String text = body;
        for (var entry : state.entrySet()) if (entry.getValue().isJsonPrimitive()) text = text.replace("{" + entry.getKey() + "}", entry.getValue().getAsString());
        return text;
    }
    public static FallbackPlan fromHtml(String html, String title) {
        try {
            String compiled = HtmlCompiler.compile(html, "");
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(compiled)));
            var nodes = document.getElementsByTagName("*");
            var fields = new ArrayList<Field>(); var buttons = new ArrayList<Button>(); var body = new StringBuilder();
            for (int i = 0; i < nodes.getLength(); i++) {
                var e = (Element) nodes.item(i); String id = e.getAttribute("id");
                String label = e.hasAttribute("aria-label") ? e.getAttribute("aria-label") : id;
                if (e.hasAttribute("bind")) body.append(e.hasAttribute("fallback-label") ? e.getAttribute("fallback-label") + "：" : "")
                    .append('{').append(e.getAttribute("bind")).append("}\n");
                if (e.getTagName().equals("input") || e.getTagName().equals("textarea")) {
                    String type = e.getAttribute("type"); if (type.isEmpty()) type = "text";
                    if (!Set.of("text", "checkbox", "range").contains(type)) continue;
                    fields.add(new Field(id, label, type, type.equals("checkbox") ? Boolean.toString(e.hasAttribute("checked")) : e.getTagName().equals("textarea") ? e.getTextContent() : e.getAttribute("value"),
                        e.hasAttribute("maxlength") ? Integer.parseInt(e.getAttribute("maxlength")) : 128,
                        number(e, "min", 0), number(e, "max", 100), number(e, "step", 1)));
                }
                if (e.getAttribute("action").startsWith("server:")) {
                    List<String> inputs = e.getAttribute("input-ids").isBlank() ? List.of() : List.of(e.getAttribute("input-ids").trim().split("\\s+"));
                    String caption=e.hasAttribute("fallback-label")?e.getAttribute("fallback-label"):e.hasAttribute("aria-label")?label:e.getTextContent().strip();
                    buttons.add(new Button(id, caption, e.getAttribute("action"), e.getAttribute("fallback-icon"), e.hasAttribute("disabled"), inputs));
                }
            }
            return new FallbackPlan(SCHEMA, title, body.toString().strip(), fields, buttons);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Invalid semantic fallback", e); }
    }
    private static float number(Element e, String name, float fallback) { return e.hasAttribute(name) ? Float.parseFloat(e.getAttribute(name)) : fallback; }
}
