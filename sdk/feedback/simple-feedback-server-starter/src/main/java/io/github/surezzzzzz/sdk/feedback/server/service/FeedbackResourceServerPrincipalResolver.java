package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.feedback.server.support.OwnerHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletRequest;

/**
 * 组合式 Resource Server 认证桥（KmsResourceServerPrincipalResolver 同型定式）。
 *
 * <p>公共资源层认证成功后把 {@link VerifiedResourceContext} 放入
 * {@code SecurityContextHolder} 的 {@code Authentication.principal}；本桥只做翻译，
 * 不验证凭据、不解析 token、不调用 introspection、不持有任何密钥材料——认证责任全部在
 * 公共层与 Provider 适配器。反馈首发仅收人员主体（HUMAN），机器主体一律返回 null
 * 走 401 链，不猜测、不回退。</p>
 *
 * <p>日志红线：只输出脱敏原因码，不输出 token、claim、subjectId 裸值。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class FeedbackResourceServerPrincipalResolver implements FeedbackPrincipalResolver {

    private static final String REASON_CONTEXT_SHAPE_MISMATCH = "resource-context-shape-mismatch";
    private static final String REASON_NOT_HUMAN = "subject-not-human";

    @Override
    public FeedbackRequestContext resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof VerifiedResourceContext)) {
            log.debug("反馈资源层解析：认证主体形态不符，实际类型={}",
                    principal == null ? "null" : principal.getClass().getName());
            log.info("反馈资源层主体解析拒绝 原因码={}", REASON_CONTEXT_SHAPE_MISMATCH);
            return null;
        }
        VerifiedResourceContext context = (VerifiedResourceContext) principal;
        if (context.getPrincipal() == null || context.getPrincipal().getSubjectType() != ResourceSubjectType.HUMAN) {
            log.info("反馈资源层主体解析拒绝 原因码={}", REASON_NOT_HUMAN);
            return null;
        }
        String subjectId = context.getPrincipal().getSubjectId();
        if (subjectId == null || subjectId.trim().isEmpty()) {
            log.info("反馈资源层主体解析拒绝 原因码={}", REASON_CONTEXT_SHAPE_MISMATCH);
            return null;
        }
        return new FeedbackRequestContext(subjectId.trim(), OwnerHelper.buildIamOwner(subjectId.trim()));
    }
}
