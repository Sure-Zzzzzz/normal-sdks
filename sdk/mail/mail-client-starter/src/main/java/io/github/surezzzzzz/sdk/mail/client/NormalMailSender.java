package io.github.surezzzzzz.sdk.mail.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.mail.MailSender;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant.TextType;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendMimeMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendSimpleMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.configuration.MailComponent;
import io.github.surezzzzzz.sdk.mail.configuration.NormalMailSenderProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.Map;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/8/16 12:24
 */
@MailComponent
@Slf4j
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.mail.send.normal", name = "username")
public class NormalMailSender extends MailSender {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JavaMailSender javaMailSender;
    @Autowired
    private NormalMailSenderProperties normalMailSenderProperties;

    /**
     * 发送简单邮件
     *
     * @param request
     * @throws Exception
     */
    public void sendSimpleMail(SendSimpleMailMessageRequest request) throws Exception {
        String from = StringUtils.isEmpty(request.getFrom()) ? normalMailSenderProperties.getUsername() : request.getFrom();
        request.setFrom(from);
        log.debug("request:{}", objectMapper.writeValueAsString(request));
        javaMailSender.send(request);
    }

    /**
     * 发送带附件的邮件
     *
     * @param request
     * @throws MessagingException
     */
    public Message sendMimeMail(SendMimeMailMessageRequest request) throws Exception {
        String from = StringUtils.isEmpty(request.getFrom()) ? normalMailSenderProperties.getUsername() : request.getFrom();
        log.debug("request:{}", objectMapper.writeValueAsString(request));
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true); // `true` 代表支持附件
        helper.setFrom(from);
        helper.setTo(request.getTo());
        if (request.getCc() != null) {
            helper.setCc(request.getCc());
        }
        helper.setSubject(request.getSubject());
        boolean textType = StringUtils.equals(request.getTextType(), TextType.HTML.getType());
        helper.setText(request.getText(), textType);

        //允许不传附件
        if (request.getFilePaths() != null && request.getFilePaths().length > 0) {
            // 添加多个附件
            for (String filePath : request.getFilePaths()) {
                FileSystemResource file = new FileSystemResource(new File(filePath));
                helper.addAttachment(file.getFilename(), file);
            }
        }
        // 设置自定义的邮件头
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                message.setHeader(entry.getKey(), entry.getValue());
            }
        }
        message.setHeader(normalMailSenderProperties.getCustomMessageIdHeader(), generateMessageId(normalMailSenderProperties.getIdDomain()));
        // 发送邮件
        javaMailSender.send(message);
        return message;
    }

}