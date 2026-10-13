package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackKind;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackStatus;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackType;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationRegistryService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionCreateService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionHandlingService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionQueryService;
import io.github.surezzzzzz.sdk.feedback.server.support.OwnerHelper;
import io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication;
import io.github.surezzzzzz.sdk.feedback.server.test.support.FeedbackTestSchemaHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反馈主链集成测试（真 MySQL 经 route 接管；幂等先于频控/注册表校验/处理 CAS/DATA 遮蔽全链断言）。
 *
 * <p>schema 重置由本类 @BeforeAll 作为唯一受控入口执行一次（规范 §12.9）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleFeedbackServerTestApplication.class)
class SubmissionFlowIntegrationTest {

    private static final String SUBJECT = "subject-flow";
    @Autowired
    private ApplicationRegistryService registryService;
    @Autowired
    private SubmissionCreateService createService;
    @Autowired
    private SubmissionQueryService queryService;
    @Autowired
    private SubmissionHandlingService handlingService;

    /**
     * 受控 schema 重置（全类一次）。
     *
     * @param dataSource 测试数据源
     */
    @BeforeAll
    static void resetSchema(@Autowired DataSource dataSource) {
        FeedbackTestSchemaHelper.reset(dataSource);
    }

    private static FeedbackSubmission buildSubmission(String applicationCode, String idempotencyKey,
                                                      String digestSuffix) {
        return buildSubmission(applicationCode, idempotencyKey, digestSuffix, SUBJECT);
    }

    private static FeedbackSubmission buildSubmission(String applicationCode, String idempotencyKey,
                                                      String digestSuffix, String subject) {
        return FeedbackSubmission.builder()
                .id(UUID.randomUUID().toString())
                .applicationCode(applicationCode)
                .kind(FeedbackKind.PROACTIVE.getCode())
                .type(FeedbackType.BUG.getCode())
                .score(7)
                .content("集成测试反馈-" + digestSuffix)
                .contactEmail("user@example.com")
                .submitterSubjectId(subject)
                .submitterDisplayName(null)
                .owner(OwnerHelper.buildIamOwner(subject))
                .status(FeedbackStatus.SUBMITTED.getCode())
                .rowVersion(0)
                .idempotencyKey(idempotencyKey)
                .idempotencyDigest("digest-" + digestSuffix)
                .createdTime(Instant.now())
                .updatedTime(Instant.now())
                .build();
    }

    private static FeedbackApplication buildApplication(String code, boolean enabled) {
        return FeedbackApplication.builder()
                .applicationCode(code)
                .displayName("应用-" + code)
                .enabled(enabled)
                .createdBy("registry-admin")
                .build();
    }

    @Test
    @DisplayName("主链：登记→提交幂等→同键重放→异摘要409→频控429→处理CAS→遮蔽")
    void testMainFlow() {
        registryService.create(buildApplication("demo-app", true), "registry-admin");
        registryService.create(buildApplication("other-app", true), "registry-admin");
        log.info("输入: 已登记 demo-app/other-app");

        // 提交首成功
        FeedbackSubmission first = createService.create(buildSubmission("demo-app", "idem-1", "a"), null)
                .getSubmission();
        assertNotNull(first.getId());
        log.info("输出: 首成功 id={}", first.getId());

        // 同键同摘要重放：返回首结果不占频控窗口（幂等先于频控）
        SubmissionCreateService.CreateResult replay = createService.create(
                buildSubmission("demo-app", "idem-1", "a"), null);
        assertTrue(replay.isReplay());
        assertEquals(first.getId(), replay.getSubmission().getId());

        // 同键异摘要 409
        SimpleFeedbackServerException digestConflict = assertThrows(SimpleFeedbackServerException.class,
                () -> createService.create(buildSubmission("demo-app", "idem-1", "b"), null));
        assertEquals(ErrorCode.IDEMPOTENCY_DIGEST_MISMATCH, digestConflict.getErrorCode());
        log.info("输出: 同键异摘要返回 {}", digestConflict.getErrorCode());

        // 未注册应用拒收（独立 owner 绕开频控占用，专测注册表校验分支；频控在注册表之前）
        SimpleFeedbackServerException unregistered = assertThrows(SimpleFeedbackServerException.class,
                () -> createService.create(buildSubmission("ghost-app", "idem-ghost", "g", "subject-ghost"),
                        null));
        assertEquals(ErrorCode.APPLICATION_NOT_AVAILABLE, unregistered.getErrorCode());

        // 频控短窗：新键第二条即拒（短窗 60s/1 条）
        SimpleFeedbackServerException rateLimited = assertThrows(SimpleFeedbackServerException.class,
                () -> createService.create(buildSubmission("demo-app", "idem-2", "c"), null));
        assertEquals(ErrorCode.RATE_LIMITED, rateLimited.getErrorCode());
        log.info("输出: 短窗频控返回 {}", rateLimited.getErrorCode());

        // 处理：SUBMITTED→RESOLVED（带 reply）
        FeedbackSubmission handled = handlingService.handle(first.getId(), FeedbackStatus.RESOLVED, "handler-1",
                "已修复", "感谢反馈", first.getRowVersion(), FeedbackApplicationAccessScope.all());
        assertEquals(FeedbackStatus.RESOLVED.getCode(), handled.getStatus());
        assertEquals("感谢反馈", handled.getReply());

        // 版本冲突：旧版本重开（RESOLVED→SUBMITTED 合法迁移）触发 CAS 不匹配 409
        SimpleFeedbackServerException conflict = assertThrows(SimpleFeedbackServerException.class,
                () -> handlingService.handle(first.getId(), FeedbackStatus.SUBMITTED, "handler-1", null, null,
                        first.getRowVersion(), FeedbackApplicationAccessScope.all()));
        assertEquals(ErrorCode.ROW_VERSION_CONFLICT, conflict.getErrorCode());

        // DATA 遮蔽：受限范围外的应用详情=安全 404；列表不含
        Set<String> codes = new LinkedHashSet<>(Arrays.asList("other-app"));
        FeedbackApplicationAccessScope otherOnly = FeedbackApplicationAccessScope.restricted(codes);
        SimpleFeedbackServerException invisible = assertThrows(SimpleFeedbackServerException.class,
                () -> queryService.adminGet(first.getId(), otherOnly));
        assertEquals(ErrorCode.SUBMISSION_NOT_FOUND, invisible.getErrorCode());
        SubmissionQueryService.PageResult<FeedbackSubmission> scopedList =
                queryService.adminList(new io.github.surezzzzzz.sdk.feedback.server.repository
                        .FeedbackSubmissionRepository.Filter(null, null, null, null, null), otherOnly, 1, 20);
        assertEquals(0L, scopedList.getTotal());

        // 全量范围可见
        assertNotNull(queryService.adminGet(first.getId(), FeedbackApplicationAccessScope.all()));
        assertTrue(queryService.adminList(new io.github.surezzzzzz.sdk.feedback.server.repository
                        .FeedbackSubmissionRepository.Filter("demo-app", null, null, null, null),
                FeedbackApplicationAccessScope.all(), 1, 20).getTotal() >= 1L);
        log.info("输出: DATA 遮蔽与全量可见断言通过");

        // mine：本人可见且非本人 404
        assertEquals(first.getId(), queryService.mineGet(first.getId(), OwnerHelper.buildIamOwner(SUBJECT)).getId());
        assertThrows(SimpleFeedbackServerException.class,
                () -> queryService.mineGet(first.getId(), OwnerHelper.buildIamOwner("subject-other")));

        // 受限空集=空列表 200 语义（非 403）；DENY 单独断言
        assertEquals(0L, queryService.adminList(new io.github.surezzzzzz.sdk.feedback.server.repository
                        .FeedbackSubmissionRepository.Filter(null, null, null, null, null),
                FeedbackApplicationAccessScope.restricted(new LinkedHashSet<String>()), 1, 20).getTotal());
        org.springframework.security.access.AccessDeniedException denied = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> queryService.adminGet(first.getId(), FeedbackApplicationAccessScope.deny()));
        assertNotNull(denied);
        log.info("输出: 空集=空列表、DENY=403 两分支语义分清");
    }
}
