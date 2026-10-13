package io.github.surezzzzzz.sdk.feedback.server.support;

import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * SPI 降级实现：提交人联系提示（散形态，零远程面）。
 *
 * <p>恒返回 null（邮箱预填=空输入框）；聚形态由 adaptor 模块的实现覆盖。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class FallbackSubmitterProfileProvider implements SubmitterProfileProvider {

    @Override
    public SubmissionContactHint contactHint(String subjectId) {
        log.debug("hint 取数降级：SubmitterProfileProvider 为散形态默认实现，返回 null（空预填）");
        return null;
    }
}
