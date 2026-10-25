package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackKind;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackStatus;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackType;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.fixture.ServiceTestResourceAdapterConfiguration;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationRegistryService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionCreateService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionHandlingService;
import io.github.surezzzzzz.sdk.feedback.server.service.SubmissionQueryService;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackPreControllerExceptionResolver;
import io.github.surezzzzzz.sdk.feedback.server.support.OwnerHelper;
import io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication;
import io.github.surezzzzzz.sdk.feedback.server.test.support.FeedbackTestSchemaHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 反馈主链集成测试（真 MySQL 经 route 接管；幂等先于频控/注册表校验/处理 CAS/DATA 遮蔽全链断言）。
 *
 * <p>schema 重置由本类 @BeforeAll 作为唯一受控入口执行一次（规范 §12.9）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleFeedbackServerTestApplication.class,
        ServiceTestResourceAdapterConfiguration.class})
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
    @Autowired
    private io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository submissionRepository;
    @Autowired
    private io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackAttachmentRepository attachmentRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * 受控 schema 重置（全类一次）。
     *
     * @param dataSource 测试数据源
     */
    @BeforeAll
    static void resetSchema(@Autowired DataSource dataSource) {
        FeedbackTestSchemaHelper.reset(dataSource);
    }

    @Test
    @DisplayName("反馈前置异常解析器唯一注册并优先于 MVC 默认解析器")
    void testPreControllerExceptionResolverRegistration() throws Exception {
        Map<String, FeedbackPreControllerExceptionResolver> feedbackResolvers =
                webApplicationContext.getBeansOfType(FeedbackPreControllerExceptionResolver.class);
        assertEquals(1, feedbackResolvers.size());
        List<HandlerExceptionResolver> ordered = new ArrayList<HandlerExceptionResolver>(
                webApplicationContext.getBeansOfType(HandlerExceptionResolver.class).values());
        AnnotationAwareOrderComparator.sort(ordered);
        int feedbackIndex = ordered.indexOf(feedbackResolvers.values().iterator().next());
        int mvcIndex = -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index) instanceof HandlerExceptionResolverComposite) {
                mvcIndex = index;
                break;
            }
        }
        assertTrue(feedbackIndex >= 0 && mvcIndex > feedbackIndex);

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mvc.perform(post("/api/feedback/submissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(result -> assertEquals("application/json", result.getResponse().getContentType().split(";")[0]))
                .andExpect(result -> assertNotNull(result.getResponse().getHeader("X-Correlation-Id")));
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
                        .FeedbackSubmissionRepository.Filter(null, null, null, null, null, null), otherOnly, 1, 20);
        assertEquals(0L, scopedList.getTotal());

        // 全量范围可见
        assertNotNull(queryService.adminGet(first.getId(), FeedbackApplicationAccessScope.all()));
        assertTrue(queryService.adminList(new io.github.surezzzzzz.sdk.feedback.server.repository
                        .FeedbackSubmissionRepository.Filter("demo-app", null, null, null, null, null),
                FeedbackApplicationAccessScope.all(), 1, 20).getTotal() >= 1L);
        log.info("输出: DATA 遮蔽与全量可见断言通过");

        // mine：本人可见且非本人 404
        assertEquals(first.getId(), queryService.mineGet(first.getId(), OwnerHelper.buildIamOwner(SUBJECT)).getId());
        assertThrows(SimpleFeedbackServerException.class,
                () -> queryService.mineGet(first.getId(), OwnerHelper.buildIamOwner("subject-other")));

        // 受限空集=空列表 200 语义（非 403）；DENY 单独断言
        assertEquals(0L, queryService.adminList(new io.github.surezzzzzz.sdk.feedback.server.repository
                        .FeedbackSubmissionRepository.Filter(null, null, null, null, null, null),
                FeedbackApplicationAccessScope.restricted(new LinkedHashSet<String>()), 1, 20).getTotal());
        org.springframework.security.access.AccessDeniedException denied = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> queryService.adminGet(first.getId(), FeedbackApplicationAccessScope.deny()));
        assertNotNull(denied);
        log.info("输出: 空集=空列表、DENY=403 两分支语义分清");
    }

    @Test
    @DisplayName("管理列表关键词：命中内容/命中提交人/通配符字面匹配/空集")
    void testAdminListKeyword() {
        registryService.create(buildApplication("kw-app", true), "registry-admin");
        // 三条独立 owner 数据（独立 owner 绕开频控窗口串扰）：内容命中/展示名命中/含通配符字面量
        FeedbackSubmission contentHit = createService.create(FeedbackSubmission.builder()
                .id(UUID.randomUUID().toString()).applicationCode("kw-app")
                .kind(FeedbackKind.PROACTIVE.getCode()).type(FeedbackType.BUG.getCode()).score(7)
                .content("登录后闪退无法使用").contactEmail("kw-a@example.com")
                .submitterSubjectId("subject-kw-a").submitterDisplayName(null)
                .owner(OwnerHelper.buildIamOwner("subject-kw-a"))
                .status(FeedbackStatus.SUBMITTED.getCode()).rowVersion(0)
                .idempotencyKey("idem-kw-a").idempotencyDigest("digest-kw-a")
                .createdTime(Instant.now()).updatedTime(Instant.now()).build(), null).getSubmission();
        FeedbackSubmission nameHit = createService.create(FeedbackSubmission.builder()
                .id(UUID.randomUUID().toString()).applicationCode("kw-app")
                .kind(FeedbackKind.PROACTIVE.getCode()).type(FeedbackType.FEATURE.getCode()).score(8)
                .content("界面显示正常").contactEmail("kw-b@example.com")
                .submitterSubjectId("subject-kw-b").submitterDisplayName("张三丰")
                .owner(OwnerHelper.buildIamOwner("subject-kw-b"))
                .status(FeedbackStatus.SUBMITTED.getCode()).rowVersion(0)
                .idempotencyKey("idem-kw-b").idempotencyDigest("digest-kw-b")
                .createdTime(Instant.now()).updatedTime(Instant.now()).build(), null).getSubmission();
        createService.create(FeedbackSubmission.builder()
                .id(UUID.randomUUID().toString()).applicationCode("kw-app")
                .kind(FeedbackKind.PROACTIVE.getCode()).type(FeedbackType.OTHER.getCode()).score(5)
                .content("折扣显示100%错误").contactEmail("kw-c@example.com")
                .submitterSubjectId("subject-kw-c").submitterDisplayName(null)
                .owner(OwnerHelper.buildIamOwner("subject-kw-c"))
                .status(FeedbackStatus.SUBMITTED.getCode()).rowVersion(0)
                .idempotencyKey("idem-kw-c").idempotencyDigest("digest-kw-c")
                .createdTime(Instant.now()).updatedTime(Instant.now()).build(), null);

        // 命中反馈内容
        SubmissionQueryService.PageResult<FeedbackSubmission> contentResult = queryService.adminList(
                new io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository.Filter(
                        null, null, null, null, null, "闪退"), FeedbackApplicationAccessScope.all(), 1, 20);
        assertEquals(1L, contentResult.getTotal());
        assertEquals(contentHit.getId(), contentResult.getItems().get(0).getId());

        // 命中提交人展示名
        SubmissionQueryService.PageResult<FeedbackSubmission> nameResult = queryService.adminList(
                new io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository.Filter(
                        null, null, null, null, null, "张三"), FeedbackApplicationAccessScope.all(), 1, 20);
        assertEquals(1L, nameResult.getTotal());
        assertEquals(nameHit.getId(), nameResult.getItems().get(0).getId());

        // 通配符字面匹配：未转义的 "100%" 会命中全部三行，转义后只命中含字面量的一行
        SubmissionQueryService.PageResult<FeedbackSubmission> literalResult = queryService.adminList(
                new io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository.Filter(
                        null, null, null, null, null, "100%"), FeedbackApplicationAccessScope.all(), 1, 20);
        assertEquals(1L, literalResult.getTotal());
        assertTrue(literalResult.getItems().get(0).getContent().contains("100%"));

        // 无命中=空集
        assertEquals(0L, queryService.adminList(
                new io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository.Filter(
                        null, null, null, null, null, "不存在的关键词xyz"), FeedbackApplicationAccessScope.all(),
                1, 20).getTotal());

        // 命中提交人 subjectId（展示名快照随行，人名改了仍可按 ID 全量寻址；该行展示名为 NULL）
        SubmissionQueryService.PageResult<FeedbackSubmission> idResult = queryService.adminList(
                new io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository.Filter(
                        null, null, null, null, null, "subject-kw-c"), FeedbackApplicationAccessScope.all(), 1, 20);
        assertEquals(1L, idResult.getTotal());
        assertTrue(idResult.getItems().get(0).getContent().contains("100%"));

        // 我的反馈关键词：owner 等值 ∩ 内容命中；无命中=空
        SubmissionQueryService.PageResult<FeedbackSubmission> mineHit = queryService.mineList(
                OwnerHelper.buildIamOwner("subject-kw-a"), "闪退", 1, 20);
        assertEquals(1L, mineHit.getTotal());
        assertEquals(contentHit.getId(), mineHit.getItems().get(0).getId());
        assertEquals(0L, queryService.mineList(OwnerHelper.buildIamOwner("subject-kw-a"), "张三丰", 1, 20)
                .getTotal());
        log.info("输出: 关键词命中内容/提交人/subjectId/通配符字面/空集+mine 内容命中 六分支断言通过");
    }

    @Test
    @DisplayName("列表投影与附件批量：精简列不取 first_response；一页一次 IN 分组")
    void testListSlimColumnsAndAttachmentBatch() {
        registryService.create(buildApplication("proj-app", true), "registry-admin");
        FeedbackSubmission row = createService.create(buildSubmission("proj-app", "idem-proj", "p",
                "subject-proj"), null).getSubmission();

        // 精简列断言：列表实体响应消费字段在场，first_response（TEXT 重放快照）未取=null
        SubmissionQueryService.PageResult<FeedbackSubmission> list = queryService.adminList(
                new io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository.Filter(
                        "proj-app", null, null, null, null, null), FeedbackApplicationAccessScope.all(), 1, 20);
        assertEquals(1L, list.getTotal());
        FeedbackSubmission slim = list.getItems().get(0);
        assertNotNull(slim.getContent());
        assertNotNull(slim.getCreatedTime());
        assertNull(slim.getFirstResponse());
        // 全列路径不受影响：幂等查找（重放依据）仍带 first_response
        assertNotNull(submissionRepository.findByOwnerAndIdempotencyKey(
                OwnerHelper.buildIamOwner("subject-proj"), "idem-proj").getFirstResponse());

        // 附件批量：直插两条（组内时间有序）+ 空集短路
        attachmentRepository.insert(FeedbackAttachment.builder().id(UUID.randomUUID().toString())
                .submissionId(row.getId()).storageKey("feedback/" + row.getId() + "/a1.png")
                .originalFilename("a1.png").contentType("image/png").sizeBytes(10L)
                .createdTime(Instant.now().minusSeconds(10)).build());
        attachmentRepository.insert(FeedbackAttachment.builder().id(UUID.randomUUID().toString())
                .submissionId(row.getId()).storageKey("feedback/" + row.getId() + "/a2.png")
                .originalFilename("a2.png").contentType("image/png").sizeBytes(20L)
                .createdTime(Instant.now()).build());
        java.util.Map<String, java.util.List<FeedbackAttachment>> grouped =
                queryService.attachmentsBySubmissionIds(java.util.Collections.singletonList(row.getId()));
        assertEquals(1, grouped.size());
        assertEquals(2, grouped.get(row.getId()).size());
        assertEquals("a1.png", grouped.get(row.getId()).get(0).getOriginalFilename());
        assertEquals("a2.png", grouped.get(row.getId()).get(1).getOriginalFilename());
        assertTrue(queryService.attachmentsBySubmissionIds(java.util.Collections.<String>emptyList()).isEmpty());
        log.info("输出: 精简列投影（first_response 不取）与附件一页一次批量分组断言通过");
    }
}
