package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

public class Template {
    private static final Pattern TEXT_PATTERN = Pattern.compile("[#$]\\{([\\w\\.]+)}");
    private static final Pattern EQUALITY_PATTERN = Pattern.compile("\\$\\{([\\w\\.='\\s]+)}");
    private final Node root;
    private TemplateContext ctx;
    private PrintStream out;
    private String lastWhitespace;

    public Template(String templatePath) throws IOException {
        File tempFile = new File(templatePath);
        Document doc = Jsoup.parse(tempFile, "UTF-8");
        root = doc.root().childNode(0);
    }

    public void render(TemplateContext ctx, PrintStream out) throws NoSuchFieldException, IllegalAccessException {
        this.ctx = ctx;
        this.out = out;
        renderNodes(root);
        out.print(root);
    }

    private void renderNodes(Node rootNode) throws NoSuchFieldException, IllegalAccessException {
        printOpeningTag(rootNode.nodeName());
        List<Node> childNodes = rootNode.childNodes();

        for (int i = 0; i < childNodes.size(); i++) {
            Node node = childNodes.get(i);
            if (!(node instanceof TextNode)) {
                processAttributes(node);
                continue;
            }

            if (i == childNodes.size() - 1 && childNodes.size() != 1)
                continue;

            lastWhitespace = ((TextNode) node).getWholeText();
            out.print(lastWhitespace);
        }

        printClosingTag(rootNode.nodeName());
    }

    private void processAttributes(Node node) throws NoSuchFieldException, IllegalAccessException {
        Attributes attributes = node.attributes();
        if (attributes.size() == 0) {
            renderNodes(node);
            return;
        }

        boolean isEach = false;
        boolean ifCondition = true;
        boolean hasIf = false;

        for (Attribute attribute : attributes) {
            String attrName = attribute.getKey();
            switch (attrName) {
                case "t:if" -> {
                    ifCondition = processIf(attribute.getValue());
                    hasIf = true;
                }
                case "t:each" -> {
                    if (!ifCondition)
                        return;

                    processEach(node, attribute);
                    isEach = true;
                }
                case "t:text" -> {
                    if (!ifCondition)
                        return;
                    fixTextNode(node, attribute, hasIf);
                }
            }
        }

        if (!isEach)
            renderNodes(node);
    }

    private boolean processIf(String attrValue) {
        Matcher matcher = EQUALITY_PATTERN.matcher(attrValue);
        if (!matcher.matches())
            throw new IllegalStateException("Invalid expression " + attrValue);

        String expression = matcher.group(1);
        String[] exprParts = expression.split("==");

        if (exprParts.length != 2)
            throw new IllegalStateException("Invalid expression " + attrValue);

        Object actualValue = getAttrFromContext(exprParts[0].split("\\."));
        String expectedValue = exprParts[1].substring(1, exprParts[1].length() - 1);

        return expectedValue.equals(actualValue);
    }

    private void fixTextNode(Node node, Attribute attribute, boolean hasIf) {
        String[] attrParts = getAttrParts(attribute.getValue(), hasIf);
        String newText = hasIf && attrParts.length == 1 ?
                attrParts[0] :
                getAttrFromContext(attrParts).toString();
        ((Element) node).text(newText);
    }

    private String[] getAttrParts(String attrValue, boolean hasIf) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            if (!hasIf)
                throw new IllegalStateException("Invalid context attribute " + attrValue);
            return new String[]{attrValue};
        }


        String ctxAttributeStr = matcher.group(1);
        String[] attrParts = ctxAttributeStr.split("\\.");

        if (attrParts.length == 0) {
            if (!hasIf)
                throw new IllegalStateException("Invalid context attribute " + attrValue);
            return new String[]{attrValue};
        }

        return attrParts;
    }

    private Object getAttrFromContext(String[] attrParts) {
        Object value = null;
        for (int i = 0; i < attrParts.length - 1; i++) {
            Object attr = ctx.attributes.get(attrParts[i]);
            try {
                Field field = attr.getClass().getDeclaredField(attrParts[i + 1]);
                field.setAccessible(true);
                value = field.get(attr);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Invalid context attribute. No such field: " + attrParts[i + 1]);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Invalid context attribute. Can not access field: " + attrParts[i + 1]);
            }
        }

        return value;
    }

    private void processEach(Node node, Attribute attribute) throws NoSuchFieldException, IllegalAccessException {
        Matcher matcher = getMatcher(attribute.getValue());
        String attrKey = matcher.group(1);
        Object attrValue = ctx.attributes.get(attrKey);
        if (attrValue.getClass().equals(Object[].class)) {
            throw new IllegalStateException("Context attribute is not array " + attrValue);
        }
        Object[] values = (Object[])attrValue;

        Class<?> valuesClass = values[1].getClass();
        List<Node> childNodesCopies = new ArrayList<>();
        List<Field> fields = extractFields(node, childNodesCopies, valuesClass);
        List<String> whitespaces = findWhiteSpace(node);

        printEachNodes(node, values, fields, childNodesCopies, whitespaces);
    }

    private void printEachNodes(Node node, Object[] values, List<Field> fields, List<Node> childNodesCopies, List<String> whitespaces) throws IllegalAccessException {
        for (int i = 0; i < values.length; i++) {
            if (i != 0)
                out.print(lastWhitespace);

            printOpeningTag(node.nodeName());

            for (int j = 0; j < fields.size(); j++) {
                Element element = (Element) childNodesCopies.get(j);

                Field field = fields.get(j);
                String newText = field.get(values[i]).toString();

                element.text(newText);
                element.removeAttr("t:text");

                out.print(whitespaces.get(0));
                out.print(element);
            }

            out.print(whitespaces.get(whitespaces.size() - 1));
            printClosingTag(node.nodeName());
        }
        out.println();
    }

    private List<String> findWhiteSpace(Node rootNode) {
        List<String> whitespaces = new ArrayList<>();
        for (Node node : rootNode.childNodes()) {
            if (node instanceof TextNode) {
                String whitespace = ((TextNode) node).getWholeText();
                whitespaces.add(whitespace);
            }
        }
        return whitespaces;
    }

    private List<Field> extractFields(Node rootNode, List<Node> childNodesCopies, Class<?> valuesClass) throws NoSuchFieldException, IllegalAccessException {
        List<Field> fields = new ArrayList<>();
        for (Node node : rootNode.childNodes()) {
            if (node instanceof TextNode)
                continue;

            childNodesCopies.add(node.clone());
            Attributes attributes = node.attributes();
            if (attributes.size() == 0)
                continue;

            for (Attribute attribute : attributes) {
                if (attribute.getKey().equals("t:if")) {
                    throw new IllegalAccessException("Can not execute if inside of each");
                }

                if (attribute.getKey().equals("t:text")) {
                    String[] attrParts = getAttrParts(attribute.getValue(), false);
                    Field field = valuesClass.getDeclaredField(attrParts[1]);
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private Matcher getMatcher(String attrValue) {
        String[] attrParts = attrValue.split(": ");
        if (attrParts.length != 2)
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        Matcher matcher = TEXT_PATTERN.matcher(attrParts[1]);
        if (!matcher.matches())
            throw new IllegalStateException("Invalid context attribute " + attrValue);

        return matcher;
    }

    void printOpeningTag(String tagName) {
        if (tagName.equals("tbody") || tagName.equals("head"))
            return;

        out.print("<" + tagName + ">");

        if (tagName.equals("html"))
            out.println();
    }

    void printClosingTag(String tagName) {
        if (tagName.equals("tbody") || tagName.equals("head"))
            return;

        if (tagName.equals("html"))
            out.println();

        out.print("</" + tagName + ">");
    }
}
