package io.github.surezzzzzz.sdk.mail.generator;

/**
 * Mail Message-ID 生成器
 *
 * @author surezzzzzz
 */
public interface MailMessageIdGenerator {

    String generate(String domain);
}
