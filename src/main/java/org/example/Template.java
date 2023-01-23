package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

public class Template {
    private static final Pattern TEXT_PATTERN = Pattern.compile("[#$]\\{([\\w\\.]+)}");
    private final Document originalDoc;
    private TemplateContext ctx;

    public Template(String templatePath) throws IOException {
        File tempFile = new File(templatePath);
        originalDoc = Jsoup.parse(tempFile, "UTF-8");
    }

    public void render(TemplateContext ctx, PrintStream out) throws NoSuchFieldException, IllegalAccessException {
        this.ctx = ctx;
        Document doc = originalDoc.clone();
        renderNodes(doc);
        out.print(doc);
    }

    private void renderNodes(Node rootNode) throws NoSuchFieldException, IllegalAccessException {
        for (Node node : rootNode.childNodes()) {
            Attributes attributes = node.attributes();

            if (attributes.size() == 0) {
                renderNodes(node);
                continue;
            }

            Element newElement = processAttributes(node, attributes);
            if (newElement != null) {
                rootNode.replaceWith(newElement);
            }
        }
    }

    private Element processAttributes(Node node, Attributes attributes) throws NoSuchFieldException, IllegalAccessException {
        boolean hasIfAttr = false;
        for (Attribute attribute : attributes) {
            String attrName = attribute.getKey();
            switch (attrName) {
                case "t:if" -> {
                    hasIfAttr = true;
                    System.out.println("if"); //todo add if logic
                }
                case "t:each" -> {
                    if (hasIfAttr) {
                        throw new IllegalStateException("t:if combined with t:each is not supported");
                    }
                    return generateNewElementFromEach(node, attribute);
                }
                case "t:text" -> fixTextNode(node, attribute);
            }
        }

        renderNodes(node);
        return null;
    }

    private void fixTextNode(Node node, Attribute attribute) throws NoSuchFieldException, IllegalAccessException {
        String[] attrParts = getAttrParts(attribute.getValue());
        String newText = getTextFromContext(attrParts);
        ((Element) node).text(newText);
        node.removeAttr(attribute.getKey());
    }

    private String[] getAttrParts(String attrValue) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            throw new IllegalStateException("invalid context attribute " + attrValue);
        }

        String ctxAttributeStr = matcher.group(1);
        return ctxAttributeStr.split("\\.");
    }

    private String getTextFromContext(String[] attrParts) throws IllegalAccessException, NoSuchFieldException {
        Object fieldValue = null;

        for (int i = 0; i < attrParts.length - 1; i++) {
            String attrKey = attrParts[0];
            Object attrValue = ctx.attributes.get(attrKey);

            if (attrValue == null) {
                throw new IllegalStateException("Missing context attribute " + attrKey);
            }

            String fieldName = attrParts[1];
            Field field = attrValue.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            fieldValue = field.get(attrValue);
        }

        return fieldValue.toString();
    }

    private Element generateNewElementFromEach(Node node, Attribute attribute) throws NoSuchFieldException, IllegalAccessException {
        Object[] values = getObjectValues(attribute);
        Class<?> valuesClass = values[1].getClass();

        List<Field> fields = new ArrayList<>();
        String childNodesNames = extractFields(node, valuesClass, fields);

        List<Element> newElements = createNewChildElements(node, values, fields, childNodesNames);
        Element newElement = new Element(node.parent().nodeName());
        newElements.forEach(newElement::appendChild);
        return newElement;
    }

    private List<Element> createNewChildElements(Node node, Object[] values, List<Field> fields, String childNodesNames) throws IllegalAccessException {
        List<Element> newElements = new ArrayList<>();

        for (Object value : values) {
            Element newElement = new Element(node.nodeName());

            for (Field field : fields) {
                String newText = field.get(value).toString();
                Element newEl = new Element(childNodesNames);
                newEl.text(newText);
                newElement.appendChild(newEl);
            }

            newElements.add(newElement);
        }

        return newElements;
    }

    private Object[] getObjectValues(Attribute attribute) {
        Matcher matcher = getMatcher(attribute.getValue());
        String attrKey = matcher.group(1);
        Object valuesObj = ctx.attributes.get(attrKey);

        if (!valuesObj.getClass().isArray()) {
            throw new IllegalStateException("Context attribute is not iterable " + valuesObj);
        }

        return (Object[]) valuesObj;
    }

    private String extractFields(Node rootNode, Class<?> valuesClass, List<Field> fields) throws NoSuchFieldException {
        String childNodesNames = null;

        for (Node node : rootNode.childNodes()) {
            if (node instanceof TextNode) {
                continue;
            }

            if (childNodesNames == null) {
                childNodesNames = node.nodeName();
            }

            String attr = node.attr("t:text");
            String[] attrParts = getAttrParts(attr);
            Field field = valuesClass.getDeclaredField(attrParts[1]);
            field.setAccessible(true);
            fields.add(field);
        }

        return childNodesNames;
    }

    private Matcher getMatcher(String attrValue) {
        String[] attrParts = attrValue.split(": ");
        if (attrParts.length != 2) {
            throw new IllegalStateException("invalid context attribute " + attrValue);
        }

        Matcher matcher = TEXT_PATTERN.matcher(attrParts[1]);
        if (!matcher.matches()) {
            throw new IllegalStateException("invalid context attribute" + attrParts[1]);
        }

        return matcher;
    }
}
