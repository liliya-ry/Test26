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

    public void render(TemplateContext ctx, PrintStream out) throws Exception {
        this.ctx = ctx;
        Document doc = originalDoc.clone();
        renderNodes(doc);
        out.print(doc);
    }

    private void renderNodes(Node rootNode) throws Exception {
        List<Element> elementsToRemove = new ArrayList<>();
        for (Node node : rootNode.childNodes()) {
            Attributes attributes = node.attributes();

            if (attributes.size() == 0) {
                renderNodes(node);
                continue;
            }

            Element newElement = processAttributes(node, attributes);
            if (newElement == null) {
                continue;
            }

            if (newElement.parent() == null) {
                rootNode.replaceWith(newElement);
            } else {
                elementsToRemove.add(newElement);
            }

        }

        for (Element element : elementsToRemove) {
            element.remove();
        }
    }

    private Element processAttributes(Node node, Attributes attributes) throws Exception {
        boolean hasIfAttr = false;
        boolean ifCondition = true;
        boolean deleteNode = false;

        for (Attribute attribute : attributes) {
            String attrName = attribute.getKey();
            switch (attrName) {
                case "t:if" -> {
                    hasIfAttr = true;
                    ifCondition = parseCondition(attribute.getValue());
                }
                case "t:each" -> {
                    if (hasIfAttr) {
                        throw new IllegalStateException("t:if combined with t:each is not supported");
                    }
                    return generateNewElementFromEach(node, attribute);
                }
                case "t:text" -> deleteNode = fixTextNode(node, attribute, ifCondition);
            }
        }

        //no t:each attribute
        if (deleteNode) {
            return (Element)node;
        }

        renderNodes(node);
        return null;
    }

    private boolean parseCondition(String value) throws Exception {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> isObjectTextNotNull(value);
        };
    }

    private boolean isObjectTextNotNull(String value) throws Exception {
        String[] attrParts = getAttrParts(value);
        if (attrParts == null) {
            return true;
        }

        String text = getTextFromContext(attrParts);
        return text != null;
    }

    private boolean fixTextNode(Node node, Attribute attribute, boolean ifCondition) throws Exception {
        if (!ifCondition) {
            return true;
        }

        node.removeAttr("t:if");
        node.removeAttr(attribute.getKey());

        String attrValue = attribute.getValue();
        String[] attrParts = getAttrParts(attrValue);
        String newText = attrParts == null ? attrValue : getTextFromContext(attrParts);
        ((Element) node).text(newText);
        return false;
    }

    private String[] getAttrParts(String attrValue) {
        Matcher matcher = TEXT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            return null;
        }

        String ctxAttributeStr = matcher.group(1);
        return ctxAttributeStr.split("\\.");
    }

    private String getTextFromContext(String[] attrParts) throws Exception {
        Object fieldValue = null;

        for (int i = 0; i < attrParts.length - 1; i++) {
            String attrKey = attrParts[0];
            Object attrValue = ctx.attributes.get(attrKey);

            if (attrValue == null) {
                return null;
            }

            String fieldName = attrParts[1];
            Field field = attrValue.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            fieldValue = field.get(attrValue);
        }

        return fieldValue == null ? null : fieldValue.toString();
    }

    private Element generateNewElementFromEach(Node node, Attribute attribute) throws Exception {
        Object[] values = getObjectValues(attribute);
        Class<?> valuesClass = values[1].getClass();

        List<Field> fields = new ArrayList<>();
        String childNodesNames = extractFields(node, valuesClass, fields);

        List<Element> newElements = createNewChildElements(node, values, fields, childNodesNames);
        Element newElement = new Element(node.parent().nodeName());
        newElements.forEach(newElement::appendChild);
        return newElement;
    }

    private List<Element> createNewChildElements(Node node, Object[] values, List<Field> fields, String childNodesNames) throws Exception {
        List<Element> newElements = new ArrayList<>();

        for (Object value : values) {
            Element newElement = new Element(node.nodeName());

            for (Field field : fields) {
                String newText = field.get(value).toString();
                Element newChildEl = new Element(childNodesNames);
                newChildEl.text(newText);
                newElement.appendChild(newChildEl);
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

    private String extractFields(Node rootNode, Class<?> valuesClass, List<Field> fields) throws Exception{
        String childNodesNames = null;

        for (Node node : rootNode.childNodes()) {
            if (node instanceof TextNode) {
                continue;
            }

            if (childNodesNames == null) {
                childNodesNames = node.nodeName();
            }

            String ifAttr = node.attr("t:if");
            boolean ifCondition = parseCondition(ifAttr);
            if (!ifCondition) {
                continue;
            }

            String textAttr = node.attr("t:text");
            String[] attrParts = getAttrParts(textAttr);
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
