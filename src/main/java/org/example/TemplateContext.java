package org.example;

import java.util.HashMap;
import java.util.Map;

public class TemplateContext {
    Map<String, Object> attributes = new HashMap<>();

    public void put(String attributeName, Object attributeValue) {
        attributes.put(attributeName, attributeValue);
    }
}
