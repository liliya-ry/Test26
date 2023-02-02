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

    public void render(TemplateContext ctx, PrintStream out) throws TemplateException {
        renderNode(root, ctx, out);
    }

    private void renderNode(Node node, TemplateContext ctx, PrintStream out) throws TemplateException {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();
            out.print(text);
            return;
        }

        boolean hasIf = false;
        boolean ifCondition = true;
        String ifAttr = node.attr("t:if");
        if (!ifAttr.isEmpty()) {
            ifCondition = processIf(ifAttr, ctx);
            hasIf = true;
        }

        if (!ifCondition)
            return;

        String textAttr = node.attr("t:text");
        if (!textAttr.isEmpty()) {
            printTextNode(node, ctx, out, hasIf);
            return;
        }

        String eachAttr = node.attr("t:each");
        if (!eachAttr.isEmpty()) {
            processEach(node, eachAttr, ctx, out);
            return;
        }

        printOpeningTag(node, out);
        renderNodes(node, ctx, out);
        printClosingTag(node.nodeName(), out);
    }

    private void printTextNode(Node node, TemplateContext ctx, PrintStream out, boolean hasIf) throws TemplateException {
        String newText = getNewTextForNode(node.attr("t:text"), hasIf, ctx);
        printOpeningTag(node, out);
        out.print(newText);
        printClosingTag(node.nodeName(), out);
    }

    private void renderNodes(Node rootNode, TemplateContext ctx, PrintStream out) throws TemplateException {
        for (Node node : rootNode.childNodes())
            renderNode(node, ctx, out);
    }

    private boolean processIf(String attrValue, TemplateContext ctx) throws TemplateException {
        String[] attrParts = getAttrParts(attrValue);
        Object value = getAttrFromContext(attrParts, ctx);
        return value != null && !value.equals(0) && !value.equals("");
    }

    private String getNewTextForNode(String attrValue, boolean hasIf, TemplateContext ctx) throws TemplateException {
        String[] attrParts = getAttrParts(attrValue);
        return  hasIf && attrParts.length == 1 ?
                attrParts[0] :
                getAttrFromContext(attrParts, ctx).toString();
    }

    private String[] getAttrParts(String attrValue) throws TemplateException {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches())
            return new String[]{attrValue};

        String ctxAttributeStr = matcher.group(1);
        String[] attrParts = ctxAttributeStr.split("\\.");

        if (attrParts.length == 0)
            throw new TemplateException("Invalid context attribute " + attrValue);

        return attrParts;
    }

    private Object getAttrFromContext(String[] attrParts, TemplateContext ctx)  throws TemplateException {
        Object value = null;

        for (int i = 0; i < attrParts.length - 1; i++) {
            String attrKey = attrParts[i];
            String attrValue = attrParts[i + 1];
            Object attr = ctx.get(attrKey);
            try {
                Field field = attr.getClass().getDeclaredField(attrValue);
                field.setAccessible(true);
                value = field.get(attr);
            } catch (NoSuchFieldException e) {
                throw new TemplateException("Invalid context attribute. No such field: " + attrValue);
            } catch (IllegalAccessException e) {
                throw new TemplateException("Invalid context attribute. Can not access field: " + attrValue);
            }
        }

        return value;
    }

    private void processEach(Node node, String attrValue, TemplateContext ctx, PrintStream out) throws TemplateException {
        String[] attrParts = attrValue.split(": ");
        if (attrParts.length != 2)
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        Collection<?> values = getIterableValues(attrParts[1], ctx);
        Element newEl = (Element) node.clone();
        newEl.removeAttr("t:each");

        String attrKey = attrParts[0];
        Object oldValue = ctx.get(attrKey);

        for (Object value : values) {
            ctx.put(attrKey, value);
            renderNode(newEl, ctx, out);
        }

        if (oldValue != null)
            ctx.put(attrKey, oldValue);
    }

    private Collection<?> getIterableValues(String attrValue, TemplateContext ctx) throws TemplateException {
        Matcher matcher = getMatcher(attrValue);
        String attrKey = matcher.group(1);
        Object value = ctx.get(attrKey);

        if (value.getClass().isAssignableFrom(Iterable.class)) {
            return (Collection<?>) value;
        }

        if (value.getClass().equals(Object[].class))
            throw new TemplateException("Context attribute is not iterable " + attrValue);

        Object[] values = (Object[])value;

        if (values.length == 0)
            throw new TemplateException("Can not execute each on empty array");

        return List.of(values);
    }

    private Matcher getMatcher(String attrValue) throws TemplateException {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches())
            throw new TemplateException("Invalid context attribute " + attrValue);

        return matcher;
    }

    void printOpeningTag(Node node, PrintStream out) {
        String attrStr = "";
        for (Attribute attribute : node.attributes()) {
            if (!attribute.getKey().startsWith("t:"))
                attrStr += " " + attribute;
        }

        out.print("<" + node.nodeName() + attrStr + ">");
    }

    void printClosingTag(String tagName, PrintStream out) {
        out.print("</" + tagName + ">");
    }
}
