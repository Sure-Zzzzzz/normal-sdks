package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 组合式 Resource Server 认证桥。
 *
 * <p>公共资源层认证成功后把 {@link VerifiedResourceContext} 放入
 * {@code SecurityContextHolder} 的 {@code Authentication.principal}；本桥只做翻译，
 * 不验证凭据、不解析 token、不调用 introspection、不持有任何密钥材料——认证责任全部在
 * 公共层与 Provider 适配器。任一步无法派生唯一主体或 scope 时返回 {@code null}，
 * 走既有 401 链，不猜测、不回退。</p>
 *
 * <p>日志红线：只输出脱敏原因码与审计白名单同构字段（合成 principalId、ownerPrincipalId、
 * requestId、subjectType、scope 权限码、计数与耗时），不输出 token、claim、
 * introspection 响应、授权快照原文或单独的 subjectId 裸值。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class KmsResourceServerPrincipalResolver implements KmsPrincipalResolver {

    private static final String REASON_CONTEXT_SHAPE_MISMATCH = "resource-context-shape-mismatch";
    /**
     * IAM 人员主体在公共资源层的认证来源标识（inherited AKU 验证后同样归一到该前缀）。
     */
    private static final String IAM_HUMAN_SOURCE_ID = "iam";
    private static final String REASON_PRINCIPAL_ID_TOO_LONG = "principal-id-too-long";
    /**
     * KMS 全部 scope 权限码集合。
     */
    private static final Set<String> KMS_SCOPES = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList(SmartKmsServerConstant.API_PERMISSION_ME_READ,
                    SmartKmsServerConstant.API_PERMISSION_KEY_READ,
                    SmartKmsServerConstant.SCOPE_MANAGE,
                    SmartKmsServerConstant.API_PERMISSION_KEY_POLICY,
                    SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY,
                    SmartKmsServerConstant.SCOPE_SIGN,
                    SmartKmsServerConstant.SCOPE_VERIFY,
                    SmartKmsServerConstant.SCOPE_ENCRYPT,
                    SmartKmsServerConstant.SCOPE_DECRYPT,
                    SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY)));

    @Override
    public KmsRequestContext resolve(HttpServletRequest request) {
        long startNanos = System.nanoTime();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof VerifiedResourceContext)) {
            log.debug("KMS 资源层解析：认证主体形态不符，实际类型={}",
                    principal == null ? "null" : principal.getClass().getName());
            log.info("KMS 资源层主体解析拒绝 原因码={}", REASON_CONTEXT_SHAPE_MISMATCH);
            return null;
        }
        VerifiedResourceContext context = (VerifiedResourceContext) principal;

        String subjectId = context.getPrincipal().getSubjectId();
        // 两类 owner（2026-10-02 定稿）：HUMAN（IAM 人员令牌与 inherited AKU）统一 iam: 前缀，
        // 同一人双通道同一 owner；SERVICE 凭证（AKP/静态 AKU）保持 aksk:{clientId} 独立身份——
        // clientId 是稳定凭证标识（例行轮换=resetSecret 不改 clientId），其密钥治理面=kms-admin。
        String sourceId = context.getPrincipal().getSubjectType() == ResourceSubjectType.HUMAN
                ? IAM_HUMAN_SOURCE_ID : context.getPrincipal().getSourceId().getValue();
        String kmsPrincipalId = sourceId + SmartKmsServerConstant.PRINCIPAL_ID_SOURCE_SEPARATOR + subjectId;
        if (kmsPrincipalId.codePointCount(0, kmsPrincipalId.length()) > SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH) {
            log.debug("KMS 资源层解析：合成主体 ID 超限，sourceId 码点数={} subjectId 码点数={}",
                    sourceId.codePointCount(0, sourceId.length()),
                    subjectId.codePointCount(0, subjectId.length()));
            log.info("KMS 资源层主体解析拒绝 requestId={} 原因码={}", context.getRequestId(),
                    REASON_PRINCIPAL_ID_TOO_LONG);
            return null;
        }

        Set<String> scopes = new LinkedHashSet<String>(context.getApplicationAuthorization().getApiPermissions());
        scopes.retainAll(KMS_SCOPES);
        log.debug("KMS 资源层解析：apiPermissions 数量={} KMS scope 交集={} 已耗时微秒={}",
                context.getApplicationAuthorization().getApiPermissions().size(), scopes.size(),
                (System.nanoTime() - startNanos) / 1000L);

        KmsPrincipal kmsPrincipal = new KmsPrincipal(kmsPrincipalId, scopes);
        KmsRequestContext requestContext = context.getPrincipal().getSubjectType() == ResourceSubjectType.HUMAN
                ? KmsRequestContext.forVerifiedHuman(kmsPrincipal, context.getRequestId())
                : new KmsRequestContext(kmsPrincipal, context.getRequestId());
        log.info("KMS 资源层主体解析成功 requestId={} principalId={} subjectType={} scopes={}",
                requestContext.getRequestId(), kmsPrincipalId,
                context.getPrincipal().getSubjectType(), scopes);
        log.debug("KMS 资源层解析：完成，耗时微秒={}", (System.nanoTime() - startNanos) / 1000L);
        return requestContext;
    }

}
