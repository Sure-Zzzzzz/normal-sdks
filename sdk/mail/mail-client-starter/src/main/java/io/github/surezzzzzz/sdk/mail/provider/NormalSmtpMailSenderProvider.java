package io.github.surezzzzzz.sdk.mail.provider;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.builder.MailMessageBuilder;
import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import io.github.surezzzzzz.sdk.mail.constant.MailProviderType;
import io.github.surezzzzzz.sdk.mail.exception.MailSendException;
import io.github.surezzzzzz.sdk.mail.factory.JavaMailSenderFactory;
import io.github.surezzzzzz.sdk.mail.generator.MailMessageIdGenerator;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailSendResult;
import io.github.surezzzzzz.sdk.mail.support.MailAddressHelper;
import io.github.surezzzzzz.sdk.mail.support.MailHeaderHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.internet.MimeMessage;

/**
 * 普通 SMTP Mail 发送 Provider
 *
 * @author surezzzzzz
 */
@Slf4j
@MailComponent
@ConditionalOnProperty(prefix = MailConstant.SEND_NORMAL_CONFIG_PREFIX, name = MailConstant.PROPERTY_ENABLE, havingValue = MailConstant.PROPERTY_TRUE)
public class NormalSmtpMailSenderProvider implements MailSenderProvider {

    private final MailProperties properties;
    private final MailMessageBuilder messageBuilder;
    private final MailMessageIdGenerator messageIdGenerator;
    private final JavaMailSender javaMailSender;

    public NormalSmtpMailSenderProvider(MailProperties properties,
                                        MailMessageBuilder messageBuilder,
                                        MailMessageIdGenerator messageIdGenerator) {
        this.properties = properties;
        this.messageBuilder = messageBuilder;
        this.messageIdGenerator = messageIdGenerator;
        this.javaMailSender = new JavaMailSenderFactory().create(properties.getSend().getNormal());
    }

    @Override
    public MailProviderType providerType() {
        return MailProviderType.NORMAL;
    }

    @Override
    public boolean supports(MailSendRequest request) {
        return properties.getSend().getNormal().isEnable();
    }

    @Override
    public MailSendResult send(MailSendRequest request) {
        long start = System.currentTimeMillis();
        try {
            MailProperties.Normal normal = properties.getSend().getNormal();
            String from = MailAddressHelper.resolveFrom(request.getFrom(), normal.getUsername());
            MimeMessage message = javaMailSender.createMimeMessage();
            messageBuilder.build(message, request, from);
            String messageId = messageIdGenerator.generate(normal.getIdDomain());
            message.setHeader(normal.getCustomMessageIdHeader(), messageId);
            javaMailSender.send(message);
            log.info("Mail sent by provider={}, toCount={}, ccCount={}, bccCount={}, attachmentCount={}",
                    providerType().getCode(), MailAddressHelper.count(request.getTo()), MailAddressHelper.count(request.getCc()),
                    MailAddressHelper.count(request.getBcc()), request.getAttachments() == null ? 0 : request.getAttachments().size());
            String realMessageId = MailHeaderHelper.messageId(message);
            return buildResult(request, from, realMessageId != null ? realMessageId : messageId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new MailSendException(ErrorCode.SEND_FAILED, String.format(ErrorMessage.SEND_FAILED, e.getMessage()), e);
        }
    }

    private MailSendResult buildResult(MailSendRequest request, String from, String messageId, long tookMs) {
        return MailSendResult.builder()
                .success(true)
                .provider(providerType().getCode())
                .messageId(messageId)
                .from(from)
                .toCount(MailAddressHelper.count(request.getTo()))
                .ccCount(MailAddressHelper.count(request.getCc()))
                .bccCount(MailAddressHelper.count(request.getBcc()))
                .attachmentCount(request.getAttachments() == null ? 0 : request.getAttachments().size())
                .tookMs(tookMs)
                .build();
    }
}
