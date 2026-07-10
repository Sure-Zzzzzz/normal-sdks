package io.github.surezzzzzz.sdk.mail.generator;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.UUID;

/**
 * 默认 Mail Message-ID 生成器
 *
 * @author surezzzzzz
 */
@MailComponent
@ConditionalOnMissingBean(MailMessageIdGenerator.class)
public class DefaultMailMessageIdGenerator implements MailMessageIdGenerator {

    @Override
    public String generate(String domain) {
        return "<" + UUID.randomUUID().toString() + "@" + domain + ">";
    }
}
