package org.example;
public class TemplateException extends Throwable {
    private final String message;

    public TemplateException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
