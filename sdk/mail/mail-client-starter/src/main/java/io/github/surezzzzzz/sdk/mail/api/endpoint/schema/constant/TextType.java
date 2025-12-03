package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/10 15:31
 */
public enum TextType {
    HTML("html"), TEXT("text");

    private String type;

    TextType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}