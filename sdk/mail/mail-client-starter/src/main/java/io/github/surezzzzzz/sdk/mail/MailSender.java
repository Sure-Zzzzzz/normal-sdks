package io.github.surezzzzzz.sdk.mail;

import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendMimeMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendSimpleMailMessageRequest;

import javax.mail.Message;
import java.util.UUID;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/3/10 10:32
 */
public abstract class MailSender {
    public abstract void sendSimpleMail(SendSimpleMailMessageRequest request) throws Exception;

    public abstract Message sendMimeMail(SendMimeMailMessageRequest request) throws Exception;

    public String generateMessageId(String domain) {
        String uniquePart = UUID.randomUUID().toString();
        return "<" + uniquePart + "@" + domain + ">";
    }
}