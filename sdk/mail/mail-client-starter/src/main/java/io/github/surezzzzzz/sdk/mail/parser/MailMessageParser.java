package io.github.surezzzzzz.sdk.mail.parser;

import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;

import javax.mail.Message;

/**
 * Mail 消息解析器
 *
 * @author surezzzzzz
 */
public interface MailMessageParser {

    MailReadResult parse(Message message);
}
