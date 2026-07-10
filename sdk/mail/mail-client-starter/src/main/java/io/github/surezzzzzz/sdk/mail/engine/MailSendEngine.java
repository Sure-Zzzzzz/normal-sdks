package io.github.surezzzzzz.sdk.mail.engine;

import io.github.surezzzzzz.sdk.mail.constant.MailProviderType;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailSendResult;

/**
 * Mail 发送引擎
 *
 * @author surezzzzzz
 */
public interface MailSendEngine {

    MailSendResult send(MailSendRequest request);

    MailSendResult send(String subject, String text, String... to);

    MailSendResult sendHtml(String subject, String html, String... to);

    MailSendResult send(MailProviderType providerType, MailSendRequest request);
}
