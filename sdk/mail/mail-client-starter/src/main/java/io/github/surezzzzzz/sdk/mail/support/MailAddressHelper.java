package io.github.surezzzzzz.sdk.mail.support;

import org.apache.commons.lang3.StringUtils;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;

/**
 * Mail 地址 Helper
 *
 * @author surezzzzzz
 */
public final class MailAddressHelper {

    private MailAddressHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static InternetAddress[] toInternetAddresses(List<String> addresses) throws MessagingException {
        if (addresses == null || addresses.isEmpty()) {
            return new InternetAddress[0];
        }
        InternetAddress[] result = new InternetAddress[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            result[i] = new InternetAddress(addresses.get(i));
        }
        return result;
    }

    public static void setRecipients(MimeMessage message, Message.RecipientType type, List<String> addresses) throws MessagingException {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        message.setRecipients(type, toInternetAddresses(addresses));
    }

    public static int count(List<String> addresses) {
        return addresses == null ? 0 : addresses.size();
    }

    public static String resolveFrom(String requestFrom, String defaultFrom) {
        return StringUtils.isBlank(requestFrom) ? defaultFrom : requestFrom;
    }
}
