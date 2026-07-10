package io.github.surezzzzzz.sdk.mail.engine;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.constant.MailProviderType;
import io.github.surezzzzzz.sdk.mail.exception.MailSendException;
import io.github.surezzzzzz.sdk.mail.exception.MailValidationException;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailSendResult;
import io.github.surezzzzzz.sdk.mail.model.value.MailContent;
import io.github.surezzzzzz.sdk.mail.provider.MailSenderProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.Arrays;
import java.util.List;

/**
 * 默认 Mail 发送引擎
 *
 * @author surezzzzzz
 */
@MailComponent
@ConditionalOnMissingBean(MailSendEngine.class)
public class DefaultMailSendEngine implements MailSendEngine {

    private final List<MailSenderProvider> providers;
    private final MailProperties properties;

    public DefaultMailSendEngine(List<MailSenderProvider> providers, MailProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    @Override
    public MailSendResult send(MailSendRequest request) {
        String provider = request != null && StringUtils.isNotBlank(request.getProvider())
                ? request.getProvider() : properties.getSend().getDefaultProvider();
        return send(resolveProviderType(provider), request);
    }

    @Override
    public MailSendResult send(String subject, String text, String... to) {
        MailSendRequest request = MailSendRequest.builder()
                .subject(subject)
                .to(Arrays.asList(to))
                .content(MailContent.builder().type(MailContentType.TEXT).text(text).build())
                .build();
        return send(request);
    }

    @Override
    public MailSendResult sendHtml(String subject, String html, String... to) {
        MailSendRequest request = MailSendRequest.builder()
                .subject(subject)
                .to(Arrays.asList(to))
                .content(MailContent.builder().type(MailContentType.HTML).text(html).build())
                .build();
        return send(request);
    }

    @Override
    public MailSendResult send(MailProviderType providerType, MailSendRequest request) {
        if (providerType == null) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.PROVIDER_REQUIRED);
        }
        for (MailSenderProvider provider : providers) {
            if (provider.providerType() == providerType && provider.supports(request)) {
                return provider.send(request);
            }
        }
        throw new MailSendException(ErrorCode.PROVIDER_NOT_SUPPORTED,
                String.format(ErrorMessage.PROVIDER_NOT_SUPPORTED, providerType.getCode()));
    }

    private MailProviderType resolveProviderType(String provider) {
        MailProviderType providerType = MailProviderType.fromCode(provider);
        if (providerType == null) {
            throw new MailSendException(ErrorCode.PROVIDER_NOT_SUPPORTED,
                    String.format(ErrorMessage.PROVIDER_NOT_SUPPORTED, provider));
        }
        return providerType;
    }
}
