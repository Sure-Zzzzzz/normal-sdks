package io.github.surezzzzzz.sdk.auth.iam.core.spi;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;

/**
 * 对外用户主体 ID 生成 SPI。
 * 接口契约仅两条：返回值非空、长度不超过 {@link SimpleIamCoreConstant#SUBJECT_ID_MAX_LENGTH}；
 * 定长、前缀、字符集、校验位等形态约束不进代码，由接入文档约定
 * （约定：与用户名/手机号/显示名/自增 ID 无推导关系；建议字符集 [0-9A-Za-z]；建议定长，便于索引与口述）。
 *
 * @author surezzzzzz
 */
public interface SubjectIdGenerator {

    /**
     * 生成新的对外主体 ID。
     *
     * @return 主体 ID（非空，长度受契约约束）
     */
    String generate();
}
