package io.github.surezzzzzz.sdk.mail.parser;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.exception.MailParseException;
import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;
import io.github.surezzzzzz.sdk.mail.model.value.MailAttachment;
import io.github.surezzzzzz.sdk.mail.model.value.MailContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 Mail 消息解析器
 *
 * @author surezzzzzz
 */
@MailComponent
@ConditionalOnMissingBean(MailMessageParser.class)
public class DefaultMailMessageParser implements MailMessageParser {

    @Override
    public MailReadResult parse(Message message) {
        try {
            ParsedContent parsedContent = parseContent(message);
            Date receivedDate = message.getReceivedDate();
            return MailReadResult.builder()
                    .messageId(message instanceof MimeMessage ? ((MimeMessage) message).getMessageID() : null)
                    .subject(message.getSubject())
                    .from(firstAddress(message.getFrom()))
                    .to(addressList(message.getRecipients(Message.RecipientType.TO)))
                    .cc(addressList(message.getRecipients(Message.RecipientType.CC)))
                    .bcc(addressList(message.getRecipients(Message.RecipientType.BCC)))
                    .content(parsedContent.content)
                    .attachments(parsedContent.attachments)
                    .headers(headers(message))
                    .seen(message.isSet(Flags.Flag.SEEN))
                    .receivedTime(receivedDate == null ? null : receivedDate.getTime())
                    .build();
        } catch (Exception e) {
            throw new MailParseException(ErrorCode.PARSE_FAILED, String.format(ErrorMessage.PARSE_FAILED, e.getMessage()), e);
        }
    }

    private ParsedContent parseContent(Part part) throws Exception {
        ParsedContent parsedContent = new ParsedContent();
        Object content = part.getContent();
        if (content instanceof String) {
            parsedContent.content = MailContent.builder()
                    .type(resolveContentType(part.getContentType()))
                    .text((String) content)
                    .build();
            return parsedContent;
        }
        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            StringBuilder textBuilder = new StringBuilder();
            MailContentType contentType = MailContentType.TEXT;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (isAttachment(bodyPart)) {
                    parsedContent.attachments.add(MailAttachment.builder()
                            .fileName(bodyPart.getFileName())
                            .contentType(bodyPart.getContentType())
                            .build());
                } else {
                    ParsedContent nested = parseContent(bodyPart);
                    if (nested.content != null && StringUtils.isNotBlank(nested.content.getText())) {
                        textBuilder.append(nested.content.getText());
                        if (MailContentType.HTML == nested.content.getType()) {
                            contentType = MailContentType.HTML;
                        }
                    }
                    parsedContent.attachments.addAll(nested.attachments);
                }
            }
            parsedContent.content = MailContent.builder().type(contentType).text(textBuilder.toString()).build();
        }
        return parsedContent;
    }

    private MailContentType resolveContentType(String contentType) {
        if (StringUtils.containsIgnoreCase(contentType, MailConstant.CONTENT_TYPE_HTML)) {
            return MailContentType.HTML;
        }
        return MailContentType.TEXT;
    }

    private boolean isAttachment(BodyPart bodyPart) throws Exception {
        return Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) || bodyPart.getFileName() != null;
    }

    private String firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return addresses[0].toString();
    }

    private List<String> addressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Address address : addresses) {
            result.add(address.toString());
        }
        return result;
    }

    private Map<String, List<String>> headers(Message message) throws Exception {
        if (!(message instanceof MimeMessage)) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new HashMap<>();
        Enumeration<?> allHeaders = ((MimeMessage) message).getAllHeaders();
        while (allHeaders.hasMoreElements()) {
            javax.mail.Header header = (javax.mail.Header) allHeaders.nextElement();
            List<String> values = result.get(header.getName());
            if (values == null) {
                values = new ArrayList<>();
                result.put(header.getName(), values);
            }
            values.add(header.getValue());
        }
        return result;
    }

    private static class ParsedContent {
        private MailContent content;
        private List<MailAttachment> attachments = new ArrayList<>();
    }
}
