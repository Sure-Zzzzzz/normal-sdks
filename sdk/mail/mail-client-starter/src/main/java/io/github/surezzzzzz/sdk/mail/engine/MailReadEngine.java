package io.github.surezzzzzz.sdk.mail.engine;

import io.github.surezzzzzz.sdk.mail.model.request.MailMoveRequest;
import io.github.surezzzzzz.sdk.mail.model.request.MailSearchRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailOperationResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailPageResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;

/**
 * Mail 读取引擎
 *
 * @author surezzzzzz
 */
public interface MailReadEngine {

    MailPageResult search(MailSearchRequest request);

    MailReadResult readByMessageId(String messageId);

    MailReadResult readByCustomHeader(String headerName, String headerValue);

    MailOperationResult markAsRead(String folder, String messageId);

    MailOperationResult markAsUnread(String folder, String messageId);

    MailOperationResult move(MailMoveRequest request);
}
