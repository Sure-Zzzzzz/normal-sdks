package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketIdempotencyConflictException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketNotAccessibleException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketStateException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketType;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketCommentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketEvaluationRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketGroupRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketNoSequenceRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTransitionRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository;
import io.github.surezzzzzz.sdk.ticket.server.service.NotificationDispatcher;
import io.github.surezzzzzz.sdk.ticket.server.service.SlaScanService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketCommandService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketTemplateValidator;
import io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider;
import io.github.surezzzzzz.sdk.ticket.server.spi.NotificationProvider;
import io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketNoHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * service 层真库测试（feedback 先例：手工装配真件直测；环境未备好如实失败不跳过）。
 *
 * <p>前置：本地 MySQL；地址和用户名由受管 application.yml 提供，密码由被忽略的
 * application-local.yml 提供；每次运行重建测试库表（simple_ticket_test）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketCommandServiceIntegrationTest {

    private static final Properties MANAGED_CONFIG = loadYaml("application.yml");

    private static final Properties LOCAL_CONFIG = localConfig();

    private static final String JDBC_URL = required(MANAGED_CONFIG, "ticket.test.jdbc.url");

    private static final String JDBC_USER = required(MANAGED_CONFIG, "ticket.test.jdbc.username");

    private static final String JDBC_PASSWORD = required(LOCAL_CONFIG, "ticket.test.jdbc.password");

    private static TicketCommandService commandService;

    private static TicketQueryService queryService;

    private static SlaScanService scanService;

    private static TicketTypeRepository typeRepository;

    private static TicketGroupRepository groupRepository;

    private static TicketRepository ticketRepository;

    private static JdbcTemplate jdbcTemplate;

    private static final TicketRequestContext OWNER =
            new TicketRequestContext("person-owner", "提单人", false, false);

    private static final TicketRequestContext AGENT =
            new TicketRequestContext("person-agent", "处理人", false, false);

    private static final TicketRequestContext OUTSIDER =
            new TicketRequestContext("person-outside", "路人", false, false);

    private static final TicketRequestContext MACHINE =
            new TicketRequestContext("akp-cred-1", "akp-cred-1", false, true);

    @BeforeAll
    static void setUpRealDatabase() throws Exception {
        log.debug("验证工单集成测试使用独立隔离库");
        DataSource dataSource = simpleDataSource();
        try (Connection scriptConnection = dataSource.getConnection()) {
            assertEquals("simple_ticket_test", scriptConnection.getCatalog(),
                    "破坏性测试只允许独立测试库");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    scriptConnection,
                    new org.springframework.core.io.FileSystemResource("src/test/resources/schema-test.sql"));
        }
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbcTemplate = jdbc;
        org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate named =
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(dataSource);
        cleanTables(jdbc);
        TicketTypeRepository typeRepo = new TicketTypeRepository(named);
        TicketGroupRepository groupRepo = new TicketGroupRepository(named);
        TicketRepository ticketRepo = new TicketRepository(named);
        ticketRepository = ticketRepo;
        TicketTransitionRepository transitionRepo = new TicketTransitionRepository(named);
        TicketCommentRepository commentRepo = new TicketCommentRepository(named);
        TicketEvaluationRepository evaluationRepo = new TicketEvaluationRepository(named);
        TicketAttachmentRepository attachmentRepo = new TicketAttachmentRepository(named);
        TicketNoSequenceRepository sequenceRepo = new TicketNoSequenceRepository(jdbc);
        typeRepository = typeRepo;
        groupRepository = groupRepo;
        io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties properties =
                new io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties();
        NotificationProvider noopNotification = (subjectIds, title, content) -> {
        };
        SubmitterProfileProvider noopProfile = subjectId -> subjectId;
        FileProvider noopFile = new FileProvider() {
            @Override
            public String issueUploadKey(String ticketNo, String fileName, String contentType) {
                throw new TicketAttachmentRejectException();
            }

            @Override
            public String presignUpload(String objectKey, long size, String contentType,
                                        java.time.Duration ttl) {
                throw new TicketAttachmentRejectException();
            }

            @Override
            public String presignDownload(String objectKey, java.time.Duration ttl) {
                throw new TicketAttachmentRejectException();
            }

            @Override
            public boolean exists(String objectKey) {
                return false;
            }

            @Override
            public void delete(String objectKey) {
            }
        };
        TicketPrincipalResolver resolver = request -> null;
        TicketTemplateValidator validator = new TicketTemplateValidator();
        NotificationDispatcher dispatcher = new NotificationDispatcher(noopNotification, properties);
        TicketCommandService target = new TicketCommandService(ticketRepo, typeRepo, groupRepo, transitionRepo,
                commentRepo, evaluationRepo, attachmentRepo, noopFile, sequenceRepo, validator,
                dispatcher, resolver, noopProfile, properties);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        commandService = (TicketCommandService) proxy.getProxy();
        queryService = new TicketQueryService(ticketRepo, typeRepo, commentRepo, transitionRepo,
                attachmentRepo, groupRepo);
        scanService = new SlaScanService(ticketRepo, groupRepo, commandService, dispatcher, properties);
        seedTypeAndGroup(typeRepo, groupRepo);
    }

    @Test
    @Order(1)
    void portalClosedLoopSixSteps() {
        Ticket created = commandService.createPortal(OWNER, "fault", "打印机坏了", "三楼打印机卡纸",
                "HIGH", sampleAnswers(), null, null);
        assertTrue(TicketNoHelper.isValid(created.getTicketNo()), "单号形态");
        assertEquals(TicketStatus.SUBMITTED, created.getStatus());
        assertEquals(TicketPriority.HIGH, created.getPriority());
        assertNotNull(created.getResponseDeadline(), "SLA 时限固化");
        assertEquals("person-owner", created.getOwnerSubjectId());

        Ticket claimed = commandService.claim(AGENT, created.getTicketNo());
        assertEquals(TicketStatus.PROCESSING, claimed.getStatus());
        assertEquals("grp-ops", claimed.getAssigneeGroup());
        assertEquals("person-agent", claimed.getAssigneeSubjectId());

        assertNotNull(commandService.comment(AGENT, created.getTicketNo(), "已定位卡纸位置", true));

        Ticket pending = commandService.requestInfo(AGENT, created.getTicketNo(),
                RequestInfoReason.AWAIT_MATERIAL);
        assertEquals(TicketStatus.PENDING_INFO, pending.getStatus());
        assertNotNull(pending.getPauseStartedAt());

        Ticket supplied = commandService.supply(OWNER, created.getTicketNo(), "已换新纸");
        assertEquals(TicketStatus.PROCESSING, supplied.getStatus());
        assertTrue(supplied.getPendingAccumulatedMs() > 0, "暂停账结算");

        Ticket resolved = commandService.resolve(AGENT, created.getTicketNo());
        assertEquals(TicketStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getConfirmDeadline());

        Ticket closed = commandService.confirm(OWNER, created.getTicketNo());
        assertEquals(TicketStatus.CLOSED, closed.getStatus());
        commandService.evaluate(OWNER, created.getTicketNo(), 5, "处理很快");
        assertThrows(TicketStateException.class,
                () -> commandService.evaluate(OWNER, created.getTicketNo(), 4, "重复评价"));
    }

    @Test
    @Order(2)
    void visibilityAndRoleBoundaries() {
        Ticket ticket = commandService.createPortal(OWNER, "consult", "外人不该看见", "详情", null, null, null, null);
        assertThrows(TicketNotAccessibleException.class,
                () -> queryService.detail(OUTSIDER, ticket.getTicketNo()));
        assertThrows(TicketForbiddenActionException.class,
                () -> commandService.withdraw(AGENT, ticket.getTicketNo()));
        assertThrows(TicketForbiddenActionException.class,
                () -> commandService.assign(AGENT, ticket.getTicketNo(), "grp-ops", "person-agent"));
        assertEquals(TicketStatus.SUBMITTED, ticket.getStatus(), "上述动作均未生效");
    }

    @Test
    @Order(3)
    void openApiIdempotencyAndCredentialBoundary() {
        Map<String, Object> answers = sampleAnswers();
        TicketCommandService.OpenApiCreateResult firstResult = commandService.createOpenApiResult(
                "akp-cred-1", "idem-001", "fault", "机器开单一", "监控告警自动开单",
                "URGENT", "person-owner", answers);
        Ticket first = firstResult.getTicket();
        assertTrue(firstResult.isCreated(), "首次提交为新建");
        assertEquals(TicketSource.OPENAPI, first.getSource());
        assertEquals(64, first.getRequestFingerprint().length(), "只保存请求摘要");
        TicketCommandService.OpenApiCreateResult replayResult = commandService.createOpenApiResult(
                "akp-cred-1", "idem-001", "fault", "机器开单一", "监控告警自动开单",
                "URGENT", "person-owner", answers);
        Ticket replay = replayResult.getTicket();
        assertTrue(!replayResult.isCreated(), "重放不再返回 201");
        assertEquals(first.getTicketNo(), replay.getTicketNo(), "幂等重放同单");
        assertThrows(TicketIdempotencyConflictException.class,
                () -> commandService.createOpenApi("akp-cred-1", "idem-001", "fault", "标题不同",
                        "异体", "URGENT", "person-owner", answers));
        Ticket otherCredential = commandService.createOpenApi("akp-cred-2", "idem-001", "consult",
                "凭据二的单", "d", null, "person-owner", null);
        assertNotEquals(first.getTicketNo(), otherCredential.getTicketNo(), "不同凭据可复用幂等键");
        assertThrows(TicketNotAccessibleException.class,
                () -> queryService.detail(MACHINE, otherCredential.getTicketNo()).getCreatorCredentialId());
    }

    @Test
    @Order(4)
    void stateConflictMapping() {
        Ticket ticket = commandService.createPortal(OWNER, "consult", "冲突检测", "d", null, null, null, null);
        commandService.claim(AGENT, ticket.getTicketNo());
        assertThrows(TicketStateException.class,
                () -> commandService.claim(AGENT, ticket.getTicketNo()), "重复受理应 409");
        assertThrows(TicketStateException.class,
                () -> commandService.supply(OWNER, ticket.getTicketNo(), "尚未待补充"), "非待补充态补充应 409");
    }

    @Test
    @Order(5)
    void slaScanFlagsOverdueAndAutoCloses() {
        Ticket ticket = commandService.createPortal(OWNER, "consult", "超时检测", "d", null, null, null, null);
        // 测试类型 SLA=1 分钟；直接回写历史时限触发超时（不 sleep）
        rewindDeadlines(ticket.getTicketNo());
        scanService.scanOnce();
        Ticket reloaded = queryService.detail(OWNER, ticket.getTicketNo());
        assertTrue(reloaded.isOverdueResponse(), "响应超时置位");

        Ticket claimed = commandService.claim(AGENT, ticket.getTicketNo());
        rewindResolution(claimed.getTicketNo());
        scanService.scanOnce();
        assertTrue(queryService.detail(OWNER, ticket.getTicketNo()).isOverdueResolution(), "办结超时置位");

        Ticket resolved = commandService.resolve(AGENT, ticket.getTicketNo());
        rewindConfirm(resolved.getTicketNo());
        scanService.scanOnce();
        assertEquals(TicketStatus.CLOSED, queryService.detail(OWNER, resolved.getTicketNo()).getStatus(),
                "确认超时自动关闭");
    }

    @Test
    @Order(6)
    void queueOrderingByPriorityThenCreated() {
        commandService.createPortal(OWNER, "consult", "普通单", "d", "NORMAL", null, null, null);
        commandService.createPortal(OWNER, "consult", "紧急单", "d", "URGENT", null, null, null);
        java.util.List<Ticket> queue = queryService.queue("person-agent", null, 1, 10);
        assertTrue(queue.size() >= 2);
        assertEquals("URGENT", queue.get(0).getPriority().name(), "紧急在前");
    }

    @Test
    @Order(7)
    void openApiReplayRemainsStableAfterTemplateChangesAndChecksEveryField() {
        typeRepository.insert(TicketType.builder().code("mutable").name("可变模板")
                .formTemplate("[]").defaultGroup("grp-ops").slaResponseMinutes(60L)
                .slaResolutionMinutes(480L).enabled(true).build());
        Map<String, Object> answers = new HashMap<>();
        answers.put("future", "原始答案");
        Ticket first = commandService.createOpenApi("akp-cred-1", "idem-template", "mutable",
                "模板变化", "原描述", "NORMAL", "person-owner", answers);
        TicketType changed = typeRepository.findByCode("mutable").get();
        changed.setFormTemplate("[{\"key\":\"newRequired\",\"type\":\"text\",\"required\":true}]");
        typeRepository.update(changed);
        TicketCommandService.OpenApiCreateResult replay = commandService.createOpenApiResult(
                "akp-cred-1", "idem-template", "mutable", "模板变化", "原描述", "NORMAL",
                "person-owner", answers);
        assertEquals(first.getTicketNo(), replay.getTicket().getTicketNo());
        assertTrue(!replay.isCreated());
        assertThrows(TicketIdempotencyConflictException.class,
                () -> commandService.createOpenApi("akp-cred-1", "idem-template", "mutable",
                        "模板变化", "新描述", "NORMAL", "person-owner", answers));
        assertThrows(TicketIdempotencyConflictException.class,
                () -> commandService.createOpenApi("akp-cred-1", "idem-template", "mutable",
                        "模板变化", "原描述", "HIGH", "person-owner", answers));
        assertThrows(TicketIdempotencyConflictException.class,
                () -> commandService.createOpenApi("akp-cred-1", "idem-template", "mutable",
                        "模板变化", "原描述", "NORMAL", "person-outside", answers));
        Map<String, Object> otherAnswers = new HashMap<>(answers);
        otherAnswers.put("future", "不同答案");
        assertThrows(TicketIdempotencyConflictException.class,
                () -> commandService.createOpenApi("akp-cred-1", "idem-template", "mutable",
                        "模板变化", "原描述", "NORMAL", "person-owner", otherAnswers));
    }

    @Test
    @Order(8)
    void missingOrDisabledLeaderGroupFailsClosed() {
        TicketRequestContext leaderOwner = new TicketRequestContext("person-leader", "组长", false, false);
        typeRepository.insert(TicketType.builder().code("orphan").name("无组")
                .formTemplate("[]").defaultGroup("missing-group").slaResponseMinutes(60L)
                .slaResolutionMinutes(480L).enabled(true).build());
        Ticket orphan = commandService.createPortal(leaderOwner, "orphan", "无组分派", null,
                null, null, null, null);
        assertThrows(TicketForbiddenActionException.class,
                () -> commandService.assign(leaderOwner, orphan.getTicketNo(), "grp-ops", "person-agent"));
        groupRepository.insert(TicketGroup.builder().code("disabled-group").name("停用组")
                .memberSubjectIds(Collections.singletonList("person-leader"))
                .leaderSubjectId("person-leader").enabled(false).build());
        typeRepository.insert(TicketType.builder().code("disabled-type").name("停用组类型")
                .formTemplate("[]").defaultGroup("disabled-group").slaResponseMinutes(60L)
                .slaResolutionMinutes(480L).enabled(true).build());
        Ticket disabled = commandService.createPortal(leaderOwner, "disabled-type", "停用组分派", null,
                null, null, null, null);
        assertThrows(TicketForbiddenActionException.class,
                () -> commandService.assign(leaderOwner, disabled.getTicketNo(), "grp-ops", "person-agent"));
    }

    @Test
    @Order(9)
    void slaSqlUsesMillisecondsAndFailedAttachmentRollsBackTicket() {
        Ticket ticket = commandService.createPortal(OWNER, "consult", "暂停累计单位", null,
                null, null, null, null);
        commandService.claim(AGENT, ticket.getTicketNo());
        jdbcTemplate.update("UPDATE ticket SET resolution_deadline = DATE_SUB(NOW(), INTERVAL 1 HOUR), "
                + "pending_accumulated_ms = 7200000 WHERE ticket_no = ?", ticket.getTicketNo());
        boolean selected = ticketRepository.findScanCandidates(Instant.now(), 200).stream()
                .anyMatch(candidate -> ticket.getTicketNo().equals(candidate.getTicketNo()));
        assertTrue(!selected, "两小时暂停累计不得误按微秒计算");

        Map<String, Object> attachment = new HashMap<>();
        attachment.put("objectKey", "ticket/pending/missing");
        attachment.put("fileName", "missing.pdf");
        attachment.put("contentType", "application/pdf");
        attachment.put("size", 100L);
        assertThrows(TicketAttachmentRejectException.class,
                () -> commandService.createPortal(OWNER, "consult", "附件失败回滚", null,
                        null, null, null, Collections.singletonList(attachment)));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE subject = ?", Integer.class, "附件失败回滚"));
    }

    @Test
    @Order(10)
    void openApiCloseUsesSystemAuditRole() {
        Ticket ticket = commandService.createOpenApi(MACHINE.getSubjectId(), "idem-close-role", "consult",
                "机器关闭审计", "机器确认关闭角色", null, "person-owner", null);
        jdbcTemplate.update("UPDATE ticket SET status = 'RESOLVED', confirm_deadline = NOW() "
                + "WHERE ticket_no = ?", ticket.getTicketNo());

        Ticket closed = commandService.confirm(MACHINE, ticket.getTicketNo());

        assertEquals(TicketStatus.CLOSED, closed.getStatus());
        assertEquals("SYSTEM", jdbcTemplate.queryForObject(
                "SELECT actor_role FROM ticket_transition WHERE ticket_id = ? AND action = 'CONFIRM' "
                        + "ORDER BY id DESC LIMIT 1", String.class, ticket.getId()));
    }

    @Test
    @Order(11)
    void priorityAcceptsNormalizedValueAndRejectsUnknownValue() {
        Ticket created = commandService.createPortal(OWNER, "consult", "优先级规范化", null,
                " high ", null, null, null);
        assertEquals(TicketPriority.HIGH, created.getPriority());
        TicketValidationException invalid = assertThrows(TicketValidationException.class,
                () -> commandService.createPortal(OWNER, "consult", "优先级非法", null,
                        "not-a-priority", null, null, null));
        assertEquals("priority", invalid.getField());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE subject = ?", Integer.class, "优先级非法"));
    }

    // ==================== 辅助 ====================

    private static void seedTypeAndGroup(TicketTypeRepository typeRepo, TicketGroupRepository groupRepo) {
        String faultTemplate = "[{\"key\":\"scope\",\"label\":\"影响范围\",\"type\":\"select\","
                + "\"required\":true,\"options\":[\"单系统\",\"多系统\",\"全域\"]}]";
        typeRepo.insert(TicketType.builder().code("fault").name("故障报修")
                .formTemplate(faultTemplate)
                .defaultGroup("grp-ops").slaResponseMinutes(1L).slaResolutionMinutes(1L).enabled(true)
                .build());
        typeRepo.insert(TicketType.builder().code("consult").name("咨询").formTemplate("[]")
                .defaultGroup("grp-ops").slaResponseMinutes(60L).slaResolutionMinutes(480L)
                .enabled(true).build());
        groupRepo.insert(TicketGroup.builder().code("grp-ops").name("运维组")
                .memberSubjectIds(Arrays.asList("person-agent", "person-leader"))
                .leaderSubjectId("person-leader").enabled(true).build());
    }

    private static Map<String, Object> sampleAnswers() {
        Map<String, Object> answers = new HashMap<>();
        answers.put("scope", "单系统");
        return answers;
    }

    private static void rewindDeadlines(String ticketNo) {
        rewind(ticketNo, "UPDATE ticket SET response_deadline = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR), "
                + "resolution_deadline = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR) WHERE ticket_no = ?");
    }

    private static void rewindResolution(String ticketNo) {
        rewind(ticketNo, "UPDATE ticket SET resolution_deadline = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR) "
                + "WHERE ticket_no = ?");
    }

    private static void rewindConfirm(String ticketNo) {
        rewind(ticketNo, "UPDATE ticket SET confirm_deadline = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR) "
                + "WHERE ticket_no = ?");
    }

    private static void rewind(String ticketNo, String sql) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, ticketNo);
                statement.executeUpdate();
            }
        } catch (Exception e) {
            throw new AssertionError("测试时限回写失败", e);
        }
    }

    private static void cleanTables(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : new String[]{"ticket", "ticket_transition", "ticket_comment",
                "ticket_attachment", "ticket_evaluation", "ticket_type", "ticket_group",
                "ticket_no_sequence"}) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private static DataSource simpleDataSource() {
        org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                new org.springframework.jdbc.datasource.SimpleDriverDataSource();
        try {
            dataSource.setDriverClass((Class<? extends java.sql.Driver>) Class.forName("com.mysql.cj.jdbc.Driver"));
        } catch (ClassNotFoundException e) {
            throw new AssertionError("测试库驱动缺失", e);
        }
        dataSource.setUrl(JDBC_URL);
        dataSource.setUsername(JDBC_USER);
        dataSource.setPassword(JDBC_PASSWORD);
        return dataSource;
    }

    private static Properties localConfig() {
        assertEquals("local", MANAGED_CONFIG.getProperty("spring.profiles.active"),
                "真库测试只能加载 local 私有凭据文件");
        return loadYaml("application-local.yml");
    }

    private static Properties loadYaml(String name) {
        Resource resource = new ClassPathResource(name);
        assertTrue(resource.exists(), "缺少测试配置：" + name);
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        Properties properties = factory.getObject();
        assertNotNull(properties, "测试配置为空：" + name);
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        assertTrue(value != null && !value.trim().isEmpty(), "缺少测试配置项：" + key);
        return value;
    }
}
