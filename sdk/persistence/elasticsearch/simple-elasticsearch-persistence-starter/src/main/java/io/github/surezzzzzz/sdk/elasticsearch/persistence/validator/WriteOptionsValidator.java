package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.WriteOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.util.StringUtils;

/**
 * Write Options Validator
 *
 * <p>WriteOptions 公共校验，供各请求校验器复用，非注册型校验器。
 *
 * @author surezzzzzz
 */
public final class WriteOptionsValidator {

    private WriteOptionsValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验 refreshPolicy 只允许 true/false/wait_for。
     */
    public static void validate(WriteOptions options) {
        if (options == null) {
            return;
        }
        String refreshPolicy = options.getRefreshPolicy();
        if (!StringUtils.hasText(refreshPolicy)) {
            return;
        }
        if (!SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_TRUE.equals(refreshPolicy)
                && !SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_FALSE.equals(refreshPolicy)
                && !SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_WAIT_FOR.equals(refreshPolicy)) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "refreshPolicy 只允许 true/false/wait_for"));
        }
    }
}
