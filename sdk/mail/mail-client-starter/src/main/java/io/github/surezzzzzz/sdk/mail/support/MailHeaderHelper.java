package io.github.surezzzzzz.sdk.mail.support;

import io.github.surezzzzzz.sdk.mail.constant.MailConstant;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mail Header Helper
 *
 * @author surezzzzzz
 */
public final class MailHeaderHelper {

    private MailHeaderHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String firstHeader(MimeMessage message, String headerName) throws MessagingException {
        String[] values = message.getHeader(headerName);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    public static String messageId(MimeMessage message) throws MessagingException {
        return firstHeader(message, MailConstant.HEADER_MESSAGE_ID);
    }

    public static List<String> references(MimeMessage message) throws MessagingException {
        String[] values = message.getHeader(MailConstant.HEADER_REFERENCES);
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String[] items = value.split(" ");
            for (String item : items) {
                if (item != null && item.trim().length() > 0) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }
}
