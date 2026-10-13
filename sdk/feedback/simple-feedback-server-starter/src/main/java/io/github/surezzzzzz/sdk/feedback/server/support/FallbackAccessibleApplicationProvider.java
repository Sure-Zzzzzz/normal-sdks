package io.github.surezzzzzz.sdk.feedback.server.support;

import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * SPI 降级实现：可访问应用集（散形态，零远程面）。
 *
 * <p>恒返回 null（该取数点不支持=下拉不过滤为注册表启用项全量）；聚形态由
 * adaptor 模块的实现覆盖（@ConditionalOnMissingBean 让位）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class FallbackAccessibleApplicationProvider implements AccessibleApplicationProvider {

    @Override
    public List<AccessibleApplication> accessibleApplications(String subjectId) {
        log.debug("hint 取数降级：AccessibleApplicationProvider 为散形态默认实现，返回 null（不过滤）");
        return null;
    }
}
