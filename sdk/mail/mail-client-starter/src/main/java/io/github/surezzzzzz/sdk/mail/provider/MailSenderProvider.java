package io.github.surezzzzzz.sdk.mail.provider;

import io.github.surezzzzzz.sdk.mail.constant.MailProviderType;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailSendResult;

/**
 * Mail 发送 Provider
 *
 * @author surezzzzzz
 */
public interface MailSenderProvider {

    MailProviderType providerType();

    boolean supports(MailSendRequest request);

    MailSendResult send(MailSendRequest request);
}
