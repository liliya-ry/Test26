package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

public class Template {
    private static final Pattern TEXT_PATTERN = Pattern.compile("[#$]\\{([\\w\\.]+)}");
    private final Node root;

    public Template(String templatePath) throws IOException {
        File tempFile = new File(templatePath);
        Document doc = Jsoup.parse(tempFile, "UTF-8");
        root = doc.childNode(0);
    }

    public void render(TemplateContext ctx, PrintStream out)  {
        renderNode(root, ctx, out);
    }

    private void renderNode(Node node, TemplateContext ctx, PrintStream out) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();
            out.print(text);
            return;
        }

        List<Attribute> attrToPrint = new ArrayList<>();
        boolean doRender = processAttributes(node, attrToPrint, ctx, out);

        if (!doRender)
            return;

        printOpeningTag(node.nodeName(), attrToPrint, out);
        renderNodes(node, ctx, out);
        printClosingTag(node.nodeName(), out);
    }

    private boolean processAttributes(Node node, List<Attribute> attrToPrint, TemplateContext ctx, PrintStream out) {
        boolean hasEach = false;
        boolean ifCondition = true;
        boolean hasIf = false;
        boolean hasText = false;

        for (Attribute attribute : node.attributes()) {
            String attrName = attribute.getKey();
            String attrValue = attribute.getValue();
            switch (attrName) {
                case "t:if" -> {
                    ifCondition = processIf(attrValue, ctx);
                    hasIf = true;
                }
                case "t:text" -> {
                    if (!ifCondition)
                        continue;

                    String newText = getNewTextForNode(node, attrValue, hasIf, ctx);
                    printOpeningTag(node.nodeName(), attrToPrint, out);
                    out.print(newText);
                    printClosingTag(node.nodeName(), out);
                    hasText = true;
                }
                case "t:each" -> {
                    if (!ifCondition || hasText)
                        continue;

                    processEach(node, attrValue, ctx, out);
                    hasEach = true;
                }
                default -> attrToPrint.add(attribute);
            }
        }

        return  ifCondition && !hasEach && !hasText;
    }

    private void renderNodes(Node rootNode, TemplateContext ctx, PrintStream out) {
        for (Node node : rootNode.childNodes())
            renderNode(node, ctx, out);
    }

    private boolean processIf(String attrValue, TemplateContext ctx) {
        String[] attrParts = getAttrParts(attrValue);
        Object value = getAttrFromContext(attrParts, ctx);
        return value != null && !value.equals(0) && !value.equals("");
    }

    private String getNewTextForNode(Node node, String attrValue, boolean hasIf, TemplateContext ctx) {
        String[] attrParts = getAttrParts(attrValue);
        return  hasIf && attrParts.length == 1 ?
                attrParts[0] :
                getAttrFromContext(attrParts, ctx).toString();
    }

    private String[] getAttrParts(String attrValue) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches())
            return new String[]{attrValue};

        String ctxAttributeStr = matcher.group(1);
        String[] attrParts = ctxAttributeStr.split("\\.");

        if (attrParts.length == 0)
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        return attrParts;
    }

    private Object getAttrFromContext(String[] attrParts, TemplateContext ctx) {
        Object value = null;

        for (int i = 0; i < attrParts.length - 1; i++) {
            String attrKey = attrParts[i];
            String attrValue = attrParts[i + 1];
            Object attr = ctx.attributes.get(attrKey);
            try {
                Field field = attr.getClass().getDeclaredField(attrValue);
                field.setAccessible(true);
                value = field.get(attr);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Invalid context attribute. No such field: " + attrValue);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Invalid context attribute. Can not access field: " + attrValue);
            }
        }

        return value;
    }

    private void processEach(Node node, String attrValue, TemplateContext ctx, PrintStream out) {
        String[] attrParts = attrValue.split(": ");
        if (attrParts.length != 2)
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        Collection<?> values = getIterableValues(attrParts[1], ctx);
        Element newEl = (Element) node.clone();
        newEl.removeAttr("t:each");

        for (Object value : values) {
            ctx.put(attrParts[0], value);
            renderNode(newEl, ctx, out);
        }
    }

    private Collection<?> getIterableValues(String attrValue, TemplateContext ctx) {
        Matcher matcher = getMatcher(attrValue);
        String attrKey = matcher.group(1);
        Object value = ctx.attributes.get(attrKey);

        if (value.getClass().isAssignableFrom(Iterable.class)) {
            return (Collection<?>) value;
        }

        if (value.getClass().equals(Object[].class))
            throw new IllegalStateException("Context attribute is not iterable " + attrValue);

        Object[] values = (Object[])value;

        if (values.length == 0)
            throw new IllegalStateException("Can not execute each on empty array");

        return List.of(values);
    }

    private Matcher getMatcher(String attrValue) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches())
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        return matcher;
    }

    void printOpeningTag(String tagName, List<Attribute> attributes, PrintStream out) {
        String attrStr = "";
        for (Attribute attribute : attributes)
            attrStr += " " + attribute.toString();

        out.print("<" + tagName + attrStr + ">");
    }

    void printClosingTag(String tagName, PrintStream out) {
        out.print("</" + tagName + ">");
    }
}
