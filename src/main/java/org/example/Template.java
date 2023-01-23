package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

public class Template {
    private static final Pattern TEXT_PATTERN = Pattern.compile("[#$]\\{([\\w\\.]+)}");
    private final Deque<String> openedTags = new ArrayDeque<>();
    private final List<Node> nodes;
    private TemplateContext ctx;
    private PrintStream out;
    private String lastWhitespace;

    public Template(String templatePath) throws IOException {
        File tempFile = new File(templatePath);
        Document doc = Jsoup.parse(tempFile, "UTF-8");
        nodes = doc.childNodes();
    }

    public void render(TemplateContext ctx, PrintStream out) throws NoSuchFieldException, IllegalAccessException {
        this.ctx = ctx;
        this.out = out;
        renderNodes(nodes, true);
        printClosingTags();
    }

    private void renderNodes(List<Node> nodes, boolean printTexts) throws NoSuchFieldException, IllegalAccessException {
        for (Node node : nodes) {
            if (node instanceof TextNode && printTexts) {
                lastWhitespace = ((TextNode) node).getWholeText();
                out.print(lastWhitespace);
                //out.print("</" + openedTags.pop() + ">");
                continue;
            }


            Attributes attributes = node.attributes();

            if (attributes.size() == 0) {
                printNodeWithoutAttr(node);
                renderNodes(node.childNodes(), true);
                continue;
            }

            processAttributes(node, attributes);
        }
    }

    private void processAttributes(Node node, Attributes attributes) throws NoSuchFieldException, IllegalAccessException {
        boolean isEach = false;
        for (Attribute attribute : attributes) {
            String attrName = attribute.getKey();
            String attrValue = attribute.getValue();
            switch (attrName) {
                case "t:each" -> {
                    printEachNode(node, attrValue);
                    isEach = true;
                }
                case "t:text" -> printTextNode(node, attrValue);
                case "t:if" -> System.out.println("if");
            }
        }

        if (!isEach) {
            renderNodes(node.childNodes(), false);
        }
    }

    private void printNodeWithoutAttr(Node node) {
        String openingTagName = node.nodeName();
        if (openingTagName.equals("tbody") || openingTagName.equals("head")) {
            return;
        }

        out.print("<" + openingTagName + ">");
        if (openingTagName.equals("html")) {
            out.println();
        }

        openedTags.push(openingTagName);
    }

    private void printTextNode(Node node, String attrValue) throws NoSuchFieldException, IllegalAccessException {
        String[] attrParts = getAttrParts(attrValue);
        String newText = getTextFromContext(attrParts[0], attrParts[1]);
        printNode(node, attrValue, newText);
    }

    private String[] getAttrParts(String attrValue) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            throw new IllegalStateException("invalid context attribute");
        }

        String ctxAttributeStr = matcher.group(1);
        String[] attrParts = ctxAttributeStr.split("\\.");

        if (attrParts.length != 2) {
            throw new IllegalStateException("invalid context attribute");
        }

        return attrParts;
    }

    private void printNode(Node node, String attrValue, String newText) {
        ((Element) node).text(newText);
        node.removeAttr(attrValue);
        String tagName = ((Element) node).tagName();
        String strToPrint = String.format("<%s>%s</%s>", tagName, newText, tagName);
        out.print(strToPrint);
    }

    private String getTextFromContext(String attrName, String attrFieldName) throws IllegalAccessException, NoSuchFieldException {
        Object ctxAttribute = ctx.attributes.get(attrName);
        Field field = ctxAttribute.getClass().getDeclaredField(attrFieldName);
        field.setAccessible(true);
        return field.get(ctxAttribute).toString();
    }

    private void printEachNode(Node node, String attrValue) throws NoSuchFieldException, IllegalAccessException {
        Matcher matcher = getMatcher(attrValue);
        String ctxAttrName = matcher.group(1);

        Object[] values = (Object[]) ctx.attributes.get(ctxAttrName);
        Class<?> valuesClass = values[1].getClass();

        for (int i = 0; i < values.length; i++) {
            String strToPrint = "<" + node.nodeName() + ">";
            if (i != 0) {
                strToPrint = lastWhitespace + strToPrint;
            }
            out.print(strToPrint);

            for (Node childNode : node.childNodes()) {
                if (childNode instanceof TextNode) {
                    out.print(((TextNode) childNode).getWholeText());
                    continue;
                }

                String attribute = childNode.attr("t:text");
                String[] attrParts = getAttrParts(attribute);
                Field field = valuesClass.getDeclaredField(attrParts[1]);
                field.setAccessible(true);
                String newText = field.get(values[i]).toString();
                printNode(childNode, attribute, newText);
            }
            out.print("</" + node.nodeName() + ">");
        }
    }

    private Matcher getMatcher(String attrValue) {
        String[] attrParts = attrValue.split(": ");
        if (attrParts.length != 2) {
            throw new IllegalStateException("invalid context attribute");
        }

        Matcher matcher = TEXT_PATTERN.matcher(attrParts[1]);
        if (!matcher.matches()) {
            throw new IllegalStateException("invalid context attribute");
        }

        return matcher;
    }

    void printClosingTags() {
        while (!openedTags.isEmpty()) {
            out.println("</" + openedTags.pop() + ">");
        }
    }
}
