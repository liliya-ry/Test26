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
    private TemplateContext ctx;
    private PrintStream out;

    public Template(String templatePath) throws IOException {
        File tempFile = new File(templatePath);
        Document doc = Jsoup.parse(tempFile, "UTF-8");
        root = doc.childNode(0);
    }

    public void render(TemplateContext ctx, PrintStream out)  {
        this.ctx = ctx;
        this.out = out;
        renderNode(root);
    }

    private void renderNode(Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();
            out.print(text);
            return;
        }

        List<Attribute> attrToPrint = new ArrayList<>();
        boolean hasEach = processAttributes(node, attrToPrint);

        if (hasEach)
            return;

        printOpeningTag(node.nodeName(), attrToPrint);
        renderNodes(node);
        printClosingTag(node.nodeName());
    }

    private void renderNodes(Node rootNode) {
        for (Node node : rootNode.childNodes())
            renderNode(node);
    }

    private boolean processAttributes(Node node, List<Attribute> attrToPrint) {
        boolean hasEach = false;
        boolean ifCondition = true;
        boolean hasIf = false;

        for (Attribute attribute : node.attributes()) {
            String attrName = attribute.getKey();
            String attrValue = attribute.getValue();

            switch (attrName) {
                case "t:if" -> {
                    ifCondition = processIf(attrValue);
                    hasIf = true;
                }
                case "t:each" -> {
                    if (ifCondition) {
                        processEach(node, attrValue);
                        hasEach = true;
                    }
                }
                case "t:text" -> {
                    if (ifCondition)
                        fixTextNode(node, attrValue, hasIf);
                }
                default -> attrToPrint.add(attribute);
            }
        }

        return hasEach;
    }

    private boolean processIf(String attrValue) {
        String[] attrParts = getAttrParts(attrValue);
        Object value = getAttrFromContext(attrParts);
        return value != null && !value.equals(0) && !value.equals("");
    }

    private void fixTextNode(Node node, String attrValue, boolean hasIf) {
        String[] attrParts = getAttrParts(attrValue);
        String newText = hasIf && attrParts.length == 1 ?
                attrParts[0] :
                getAttrFromContext(attrParts).toString();
        ((Element) node).text(newText);
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

    private Object getAttrFromContext(String[] attrParts) {
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

    private void processEach(Node node, String attrValue) {
        String[] attrParts = attrValue.split(": ");
        if (attrParts.length != 2)
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        Object[] values = getObjectsArray(attrParts[1]);
        Element newEl = (Element) node.clone();
        newEl.removeAttr("t:each");

        for (Object value : values) {
            ctx.put(attrParts[0], value);
            renderNode(newEl);
        }
    }

    private Object[] getObjectsArray(String attrValue) {
        Matcher matcher = getMatcher(attrValue);
        String attrKey = matcher.group(1);
        Object value = ctx.attributes.get(attrKey);

        if (value.getClass().equals(Object[].class))
            throw new IllegalStateException("Context attribute is not array " + attrValue);

        Object[] values = (Object[])value;

        if (values.length == 0)
            throw new IllegalStateException("Can not execute each on empty array");

        return values;
    }

    private Matcher getMatcher(String attrValue) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches())
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        return matcher;
    }

    void printOpeningTag(String tagName, List<Attribute> attributes) {
        String attrStr = "";
        for (Attribute attribute : attributes)
            attrStr += " " + attribute.toString();

        out.print("<" + tagName + attrStr + ">");
    }

    void printClosingTag(String tagName) {
        out.print("</" + tagName + ">");
    }
}
