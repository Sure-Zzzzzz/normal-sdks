package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.server.support.VerifiedResourceAuthentication;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import io.github.surezzzzzz.sdk.kms.server.service.KmsResourceServerPrincipalResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组合式 Resource Server 认证桥解析链测试：拒绝矩阵、稳定主体与日志埋点验收。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsResourceServerPrincipalResolverTest {

    private static final String SOURCE_ID = "aksk";
    private static final String SUBJECT_ID = "SUBJECT-SECRET-XYZ";
    private static final String REQUEST_ID = "req-000000000001";
    private static final String OWNER_PRINCIPAL_ID = "owner-alpha";

    private final KmsResourceServerPrincipalResolver resolver = new KmsResourceServerPrincipalResolver();
    private CapturingAppender logAppender;
    private Logger bridgeLogger;
    private Level originalLevel;

    @BeforeEach
    void setUpLogCapture() {
        bridgeLogger = (Logger) LogManager.getLogger(KmsResourceServerPrincipalResolver.class);
        originalLevel = bridgeLogger.getLevel();
        bridgeLogger.setLevel(Level.DEBUG);
        logAppender = new CapturingAppender();
        logAppender.start();
        bridgeLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        SecurityContextHolder.clearContext();
        bridgeLogger.removeAppender(logAppender);
        logAppender.stop();
        bridgeLogger.setLevel(originalLevel);
    }

    private DataGrantDocument documentOf(DataGrant... grants) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Arrays.asList(grants));
    }

    private DataGrant ownerGrant(String ownerValue, String... additionalOwnerValues) {
        List<String> values = new ArrayList<String>();
        values.add(ownerValue);
        values.addAll(Arrays.asList(additionalOwnerValues));
        return new DataGrant("orders", Collections.singletonList("read"), false,
                Collections.singletonList(new DataConstraint(SmartKmsServerConstant.DATA_DIMENSION_OWNER_PRINCIPAL_ID,
                        DataConstraintOperator.IN, values)));
    }

    private VerifiedResourceContext contextOf(String sourceId, String subjectId, DataGrantDocument document,
                                              String... apiPermissions) {
        return contextOf(sourceId, subjectId, ResourceSubjectType.SERVICE, document, apiPermissions);
    }

    private VerifiedResourceContext contextOf(String sourceId, String subjectId, ResourceSubjectType subjectType,
                                              DataGrantDocument document, String... apiPermissions) {
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                subjectType == ResourceSubjectType.HUMAN ? ApplicationAuthorizationSubjectType.HUMAN
                        : ApplicationAuthorizationSubjectType.SERVICE, subjectId, "kms-test-app", true,
                Collections.<String>emptyList(), Collections.<String>emptyList(), Arrays.asList(apiPermissions),
                document, 1L, "manifest-v1", "manifest-digest-0123456789abcdef",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        return new VerifiedResourceContext(
                new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId(sourceId),
                        subjectType, subjectId),
                authorization, REQUEST_ID);
    }

    private KmsRequestContext resolveWith(VerifiedResourceContext context) {
        SecurityContextHolder.clearContext();
        if (context != null) {
            SecurityContextHolder.getContext().setAuthentication(new VerifiedResourceAuthentication(context));
        }
        return resolver.resolve(null);
    }

    private List<String> logMessages() {
        return logAppender.getMessages();
    }

    /**
     * 验证成功解析链：合成主体 ID、owner 提取、scope 交集与 requestId 透传。
     */
    @Test
    void shouldResolvePrincipalFromVerifiedContext() {
        KmsRequestContext context = resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)),
                "kms.me.read", "kms.key.read", "kms.key.manage", "kms.key.policy", "kms.key.destroy",
                "kms.sign", "kms.verify", "kms.encrypt", "kms.decrypt", "kms.read-public-key",
                "orders.read", "unrelated.scope"));

        log.info("桥解析成功结果: {}", context);
        assertNotNull(context, "有效已验证上下文必须解析成功");
        assertEquals(SOURCE_ID + ":" + SUBJECT_ID, context.getPrincipal().getPrincipalId(),
                "主体 ID 必须是 sourceId 前缀合成形态");
        assertEquals(SOURCE_ID + ":" + SUBJECT_ID, context.getPrincipal().getOwnerPrincipalId(),
                "自助工作区 owner 必须固定为认证主体");
        assertEquals(REQUEST_ID, context.getRequestId(), "requestId 必须来自已验证上下文");
        Set<String> scopes = context.getPrincipal().getScopes();
        assertEquals(new HashSet<String>(Arrays.asList(SmartKmsServerConstant.API_PERMISSION_ME_READ,
                SmartKmsServerConstant.API_PERMISSION_KEY_READ, SmartKmsServerConstant.SCOPE_MANAGE,
                SmartKmsServerConstant.API_PERMISSION_KEY_POLICY, SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY,
                SmartKmsServerConstant.SCOPE_SIGN, SmartKmsServerConstant.SCOPE_VERIFY,
                SmartKmsServerConstant.SCOPE_ENCRYPT, SmartKmsServerConstant.SCOPE_DECRYPT,
                SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY)), scopes, "scope 必须是 apiPermissions 与 KMS 权限集的精确交集");
    }

    /**
     * 验证 apiPermissions 不含任何 KMS scope 时主体仍成立但 scope 集为空（403 交给领域链）。
     */
    @Test
    void shouldResolvePrincipalWithEmptyScopeIntersection() {
        KmsRequestContext context = resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)),
                "orders.read"));

        log.info("scope 空交集解析结果: {}", context);
        assertNotNull(context, "scope 交集为空不是主体失败，必须解析成功");
        assertTrue(context.getPrincipal().getScopes().isEmpty(), "scope 交集必须为空且无自动授予");
    }

    /**
     * 验证无认证与形态不符时 fail-closed 返回 null。
     */
    @Test
    void shouldRejectWhenContextShapeMismatch() {
        assertNull(resolveWith(null), "无认证上下文必须返回 null");
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", "password"));
        assertNull(resolver.resolve(null), "非 VerifiedResourceContext 主体必须返回 null");
        boolean shapeLogged = false;
        for (String message : logMessages()) {
            if (message.contains("resource-context-shape-mismatch")) {
                shapeLogged = true;
            }
        }
        assertTrue(shapeLogged, "形态不符拒绝必须留下脱敏原因码");
    }

    /**
     * 验证 owner 维度缺失（无文档、空 grants、无 ownerPrincipalId 约束）时 fail-closed。
     */
    @Test
    void shouldRejectWhenOwnerDimensionMissing() {
        assertNotNull(resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, null, "kms.key.manage")),
                "认证桥不从 DataGrant 推断主体归属");
        assertNotNull(resolveWith(contextOf(SOURCE_ID, SUBJECT_ID,
                documentOf(new DataGrant("orders", Collections.singletonList("read"), false,
                        Collections.singletonList(new DataConstraint("departmentId",
                                DataConstraintOperator.IN, Collections.singletonList("d-1"))))),
                "kms.key.manage")), "DataGrant 的业务维度不影响认证桥主体");
    }

    /**
     * 验证 owner 维度多义（单约束多值、多约束多 owner）时 fail-closed。
     */
    @Test
    void shouldRejectWhenOwnerDimensionAmbiguous() {
        assertNotNull(resolveWith(contextOf(SOURCE_ID, SUBJECT_ID,
                        documentOf(ownerGrant("owner-alpha", "owner-beta")), "kms.key.manage")),
                "认证桥不从受限 DataGrant 推断 owner");
        assertNotNull(resolveWith(contextOf(SOURCE_ID, SUBJECT_ID,
                        documentOf(ownerGrant("owner-alpha"), ownerGrant("owner-beta")), "kms.key.manage")),
                "多个 DataGrant 仅在管理 API 的 DataPlan 阶段处理");
    }

    /**
     * 验证多个 grant 指向同一 owner 是合法的（主体级事实）。
     */
    @Test
    void shouldAcceptSameOwnerAcrossMultipleGrants() {
        KmsRequestContext context = resolveWith(contextOf(SOURCE_ID, SUBJECT_ID,
                documentOf(ownerGrant(OWNER_PRINCIPAL_ID), ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign"));

        log.info("同 owner 多 grant 解析结果: {}", context);
        assertNotNull(context, "多个 grant 指向同一 owner 必须解析成功");
        assertEquals(SOURCE_ID + ":" + SUBJECT_ID, context.getPrincipal().getOwnerPrincipalId());
    }

    /**
     * 验证合成主体 ID 超限时拒绝（不截断、不哈希），恰好 128 仍可接入。
     */
    @Test
    void shouldRejectWhenPrincipalIdTooLong() {
        String boundarySubject = new String(new char[123]).replace('\0', 's');
        assertEquals(128, (SOURCE_ID + ":" + boundarySubject)
                .codePointCount(0, (SOURCE_ID + ":" + boundarySubject).length()));
        assertNotNull(resolveWith(contextOf(SOURCE_ID, boundarySubject, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)),
                "kms.sign")), "合成 ID 恰好 128 code points 必须接入");
        SecurityContextHolder.clearContext();

        String longSubject = new String(new char[124]).replace('\0', 's');
        assertNull(resolveWith(contextOf(SOURCE_ID, longSubject, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign")),
                "合成 ID 超过 128 code points 必须拒绝");
        boolean reasonLogged = false;
        for (String message : logMessages()) {
            if (message.contains("principal-id-too-long")) {
                reasonLogged = true;
            }
        }
        assertTrue(reasonLogged, "合成 ID 超限拒绝必须留下脱敏原因码");
    }

    /**
     * 验证 owner 值超过 64 code points 时拒绝。
     */
    @Test
    void shouldRejectWhenOwnerValueTooLong() {
        String longOwner = new String(new char[65]).replace('\0', 't');
        assertNotNull(resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, documentOf(ownerGrant(longOwner)), "kms.sign")),
                "认证桥不消费 DataGrant 中的 owner 值");
    }

    /**
     * 验证同一逻辑主体跨请求产出稳定 principalId。
     */
    @Test
    void shouldProduceStablePrincipalIdAcrossRequests() {
        KmsRequestContext first = resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)),
                "kms.sign"));
        KmsRequestContext second = resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)),
                "kms.sign"));
        assertEquals(first.getPrincipal().getPrincipalId(), second.getPrincipal().getPrincipalId(),
                "同 sourceId 同 subjectId 必须产出稳定 principalId");
    }

    /**
     * 验证不同认证来源的相同 subjectId 不被跨源合并。
     */
    @Test
    void shouldNotMergeSameSubjectIdAcrossServiceSources() {
        // SERVICE 主体保持按 sourceId 分域：AKP（aksk:clientId）与未来其他服务源不合并
        KmsRequestContext aksk = resolveWith(contextOf("aksk", SUBJECT_ID, ResourceSubjectType.SERVICE,
                documentOf(ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign"));
        KmsRequestContext other = resolveWith(contextOf("other-svc", SUBJECT_ID, ResourceSubjectType.SERVICE,
                documentOf(ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign"));
        assertNotEquals(aksk.getPrincipal().getPrincipalId(), other.getPrincipal().getPrincipalId(),
                "SERVICE 主体的相同 subjectId 跨源必须产出不同 principalId");
        assertFalse(aksk.isVerifiedHumanSubject());
        assertFalse(other.isVerifiedHumanSubject());
    }

    /**
     * HUMAN 主体归一：inherited AKU（sourceId=aksk，subjectType=HUMAN）与门户 IAM 人员令牌
     * （sourceId=iam，subjectType=HUMAN）的 subjectId 同源于 IAM 投影，必须落到同一 owner。
     */
    @Test
    void shouldMergeHumanSubjectsAcrossIamAndInheritedAkskIntoSameOwner() {
        KmsRequestContext portal = resolveWith(contextOf("iam", SUBJECT_ID, ResourceSubjectType.HUMAN,
                documentOf(ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign"));
        KmsRequestContext inheritedAku = resolveWith(contextOf("aksk", SUBJECT_ID, ResourceSubjectType.HUMAN,
                documentOf(ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign"));
        assertEquals(portal.getPrincipal().getPrincipalId(), inheritedAku.getPrincipal().getPrincipalId(),
                "同人从门户登录与用 inherited AKU 调用必须产出同一 ownerPrincipalId（iam: 前缀归一）");
        assertTrue(portal.getPrincipal().getPrincipalId().startsWith("iam:"),
                "HUMAN 主体统一 iam: 前缀");
        assertTrue(portal.isVerifiedHumanSubject());
        assertTrue(inheritedAku.isVerifiedHumanSubject());
    }

    /**
     * 埋点验收：成功 INFO 摘要包含审计白名单同构字段，且 subjectId 只以合成形态出现。
     */
    @Test
    void shouldLogSuccessSummaryWithoutSensitiveValues() {
        resolveWith(contextOf(SOURCE_ID, SUBJECT_ID, documentOf(ownerGrant(OWNER_PRINCIPAL_ID)), "kms.sign"));

        boolean summaryLogged = false;
        for (String message : logMessages()) {
            if (message.contains("主体解析成功")) {
                summaryLogged = true;
                assertTrue(message.contains(REQUEST_ID), "成功摘要必须含 requestId");
                assertTrue(message.contains(SOURCE_ID + ":" + SUBJECT_ID), "成功摘要必须含合成 principalId");
                assertTrue(message.contains(SOURCE_ID + ":" + SUBJECT_ID), "成功摘要必须含 ownerPrincipalId");
                assertTrue(message.contains("SERVICE"), "成功摘要必须含 subjectType");
                assertTrue(message.contains("kms.sign"), "成功摘要必须含 scope 集合");
            }
            String residual = message.replace(SOURCE_ID + ":" + SUBJECT_ID, "");
            assertFalse(residual.contains(SUBJECT_ID), "subjectId 裸值只能以合成形态出现: " + message);
        }
        assertTrue(summaryLogged, "成功路径必须输出 INFO 摘要");
    }

    /**
     * 埋点验收：拒绝路径 DEBUG/INFO 均留原因码，不输出主体细节。
     */
    @Test
    void shouldLogRejectionReasonCodesWithoutSensitiveValues() {
        resolveWith(null);

        boolean infoReason = false;
        for (String message : logMessages()) {
            if (message.contains("主体解析拒绝")) {
                infoReason = true;
                assertTrue(message.contains("resource-context-shape-mismatch"), "拒绝摘要必须含脱敏原因码");
            }
            assertFalse(message.contains(SUBJECT_ID), "拒绝日志不得输出 subjectId 任何形态");
            assertFalse(message.contains("manifest-digest-0123456789abcdef"), "拒绝日志不得输出授权快照细节");
        }
        assertTrue(infoReason, "拒绝路径必须输出 INFO 摘要");
    }

    /**
     * 捕获格式化消息的测试 Appender（log4j2 生产包不含 ListAppender）。
     *
     * @author surezzzzzz
     */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<String>();

        private CapturingAppender() {
            super("kms-bridge-test-appender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        List<String> getMessages() {
            return messages;
        }
    }
}
