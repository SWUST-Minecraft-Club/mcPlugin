package com.example.yudreamui.protocol;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** A deliberately checked HTML/XHTML subset compiled to RML, retaining responsive CSS. */
public final class HtmlCompiler {
    private static final Set<String> TAGS = Set.of("html", "rml", "head", "body", "title", "style", "div", "span", "p", "h1", "h2", "h3", "label", "button", "input", "textarea", "select", "option", "img", "br", "progress", "section", "main", "header", "footer", "nav", "form", "mcslot", "mcterrain");
    private static final Map<String, String> COMPONENTS = Map.ofEntries(
            Map.entry("mc-panel", "div"), Map.entry("mc-card", "div"), Map.entry("mc-button", "button"),
            Map.entry("mc-input", "input"), Map.entry("mc-checkbox", "input"), Map.entry("mc-slider", "input"),
            Map.entry("mc-progress", "progress"), Map.entry("mc-scroll", "div"), Map.entry("mc-slot", "mcslot"),
            Map.entry("mc-badge", "span"), Map.entry("mc-tabs", "div"), Map.entry("mc-tab", "button"),
            Map.entry("mc-modal", "div"), Map.entry("mc-terrain", "mcterrain"));
    private HtmlCompiler() {}
    public record Compiled(String rml, int density) {}
    public static String compile(String html, String theme) {
        return compileDocument(html, theme).rml();
    }
    public static Compiled compileDocument(String html, String theme) {
        if (html.length() > 200_000) throw new IllegalArgumentException("UI document too large");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(html)));
            var root=document.getDocumentElement();
            String requestedDensity=root.getAttribute("ui-density");
            if (!requestedDensity.isEmpty() && !Set.of("1", "2").contains(requestedDensity))
                throw new IllegalArgumentException("UI density must be 1 or 2");
            int density=requestedDensity.equals("2")?2:1;
            if(!Set.of("html","rml").contains(root.getTagName())||document.getElementsByTagName("head").getLength()!=1||document.getElementsByTagName("body").getLength()!=1)
                throw new IllegalArgumentException("UI requires one html/rml root, head, and body");
            validate(root, 0, new HashSet<>(), new int[]{0});
            if(document.getElementsByTagName("mcterrain").getLength()>4)throw new IllegalArgumentException("At most four terrain views per document");
            document.renameNode(document.getDocumentElement(), null, "rml");
            var head = document.getElementsByTagName("head").item(0);
            if (head == null) throw new IllegalArgumentException("UI requires head");
            var styles = document.createElement("style");
            styles.appendChild(document.createTextNode(theme));
            head.insertBefore(styles, head.getFirstChild());
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            var output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return new Compiled(output.toString(),density);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Invalid HTML UI: " + e.getMessage(), e); }
    }
    private static void validateCss(String source) {
        String css=source.toLowerCase(Locale.ROOT);
        if(css.contains("@import")||css.contains("url(")||css.contains("@font-face"))
            throw new IllegalArgumentException("Declare images/fonts in the asset manifest");
    }
    private static void validate(Element element, int depth, Set<String> ids, int[] nodes) {
        if (depth > 48) throw new IllegalArgumentException("UI nesting too deep");
        if(++nodes[0]>4096)throw new IllegalArgumentException("Too many UI elements");
        if(element.hasAttribute("id")&&(!element.getAttribute("id").matches("[A-Za-z0-9_-]{1,64}")||!ids.add(element.getAttribute("id"))))
            throw new IllegalArgumentException("Invalid/duplicate UI ID");
        if(element.hasAttribute("action")&&!element.getAttribute("action").matches("(?:server|tab|toggle|count|filter):[A-Za-z0-9_-]{1,64}|ui:close"))
            throw new IllegalArgumentException("Unsupported UI action");
        String tag = element.getTagName();
        if (tag.equals("mc-terrain") || tag.equals("mcterrain")) {
            new TerrainSpec(element.getAttribute("dimension"), Integer.parseInt(element.getAttribute("chunk-x")),
                    Integer.parseInt(element.getAttribute("chunk-z")), Integer.parseInt(element.getAttribute("chunks")));
        }
        if (COMPONENTS.containsKey(tag)) {
            if(tag.equals("mc-modal")) {
                var card=element.getOwnerDocument().createElement("div");card.setAttribute("class","mc-modal-card");
                while(element.getFirstChild()!=null)card.appendChild(element.getFirstChild());
                element.appendChild(card);
            }
            element.setAttribute("class", (element.getAttribute("class") + " " + tag).trim());
            if (tag.equals("mc-checkbox")) element.setAttribute("type", "checkbox");
            if (tag.equals("mc-slider")) element.setAttribute("type", "range");
            element.getOwnerDocument().renameNode(element, null, COMPONENTS.get(tag));
        } else if (!TAGS.contains(tag)) throw new IllegalArgumentException("Unsupported UI element: " + tag);
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attribute = attributes.item(i);
            if (attribute.getNodeName().equals("style")) validateCss(attribute.getNodeValue());
            if (attribute.getNodeName().startsWith("on")) throw new IllegalArgumentException("Use action declarations instead of executable event handlers");
            if (attribute.getNodeName().equals("src") && !attribute.getNodeValue().matches("(asset|builtin):[A-Za-z0-9_./:-]+"))
                throw new IllegalArgumentException("Images must use asset: or builtin: identifiers");
        }
        if (element.getTagName().equals("style")) {
            validateCss(element.getTextContent());
            element.setTextContent(element.getTextContent().replace("@media(", "@media ("));
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element e) validate(e, depth + 1, ids, nodes);
    }
}
