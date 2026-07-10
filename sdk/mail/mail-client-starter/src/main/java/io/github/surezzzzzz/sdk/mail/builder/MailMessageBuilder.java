package io.github.surezzzzzz.sdk.mail.builder;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.exception.MailValidationException;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.value.MailAttachment;
import io.github.surezzzzzz.sdk.mail.support.MailAddressHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Mail 消息构建器
 *
 * @author surezzzzzz
 */
@MailComponent
public class MailMessageBuilder {

    public void build(MimeMessage message, MailSendRequest request, String from) throws MessagingException {
        validate(request);
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(from);
        helper.setSubject(request.getSubject());
        helper.setText(request.getContent().getText(), MailContentType.HTML == request.getContent().getType());
        MailAddressHelper.setRecipients(message, MimeMessage.RecipientType.TO, request.getTo());
        MailAddressHelper.setRecipients(message, MimeMessage.RecipientType.CC, request.getCc());
        MailAddressHelper.setRecipients(message, MimeMessage.RecipientType.BCC, request.getBcc());
        addHeaders(message, request.getHeaders());
        addAttachments(helper, request.getAttachments());
    }

    public void validate(MailSendRequest request) {
        if (request == null) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.REQUEST_REQUIRED);
        }
        if (request.getTo() == null || request.getTo().isEmpty()) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.RECIPIENT_REQUIRED);
        }
        if (StringUtils.isBlank(request.getSubject())) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.SUBJECT_REQUIRED);
        }
        if (request.getContent() == null || StringUtils.isBlank(request.getContent().getText())) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.CONTENT_REQUIRED);
        }
        if (request.getContent().getType() == null) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.CONTENT_TYPE_REQUIRED);
        }
    }

    private void addHeaders(MimeMessage message, Map<String, String> headers) throws MessagingException {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.HEADER_REQUIRED);
            }
            message.setHeader(entry.getKey(), entry.getValue());
        }
    }

    private void addAttachments(MimeMessageHelper helper, List<MailAttachment> attachments) throws MessagingException {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (MailAttachment attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String fileName = resolveFileName(attachment);
            if (StringUtils.isNotBlank(attachment.getPath())) {
                helper.addAttachment(fileName, new FileSystemResource(new File(attachment.getPath())));
            } else if (attachment.getContent() != null) {
                helper.addAttachment(fileName, new ByteArrayResource(attachment.getContent()));
            }
        }
    }

    private String resolveFileName(MailAttachment attachment) {
        if (StringUtils.isNotBlank(attachment.getFileName())) {
            return attachment.getFileName();
        }
        if (StringUtils.isNotBlank(attachment.getPath())) {
            return new File(attachment.getPath()).getName();
        }
        throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.ATTACHMENT_FAILED);
    }
}
