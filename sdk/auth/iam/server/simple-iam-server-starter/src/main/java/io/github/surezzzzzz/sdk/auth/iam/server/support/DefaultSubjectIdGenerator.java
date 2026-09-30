package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.SubjectIdGenerator;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;

import java.security.SecureRandom;

/**
 * 默认主体 ID 生成器：16 位全随机数字（谷歌式）。
 * 首位可为 0——纯字符串主体，无数值语义；业务方注册同接口 bean 即覆盖本默认实现。
 *
 * @author surezzzzzz
 */
public class DefaultSubjectIdGenerator implements SubjectIdGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder builder = new StringBuilder(SimpleIamServerConstant.DEFAULT_SUBJECT_ID_LENGTH);
        for (int i = 0; i < SimpleIamServerConstant.DEFAULT_SUBJECT_ID_LENGTH; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }
}
