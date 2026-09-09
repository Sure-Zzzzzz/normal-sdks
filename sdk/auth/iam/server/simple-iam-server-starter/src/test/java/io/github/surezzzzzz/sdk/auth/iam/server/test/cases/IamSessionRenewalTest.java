package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamSessionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamAuthorizationRevocationSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会话双时钟（滑动续期 + 绝对上限）测试
 *
 * <p>真库直验 touchIfNeeded 的节流、续期、到顶零写与撤销守卫；
 * 配置校验走独立构造的非法配置实例。filter 触发链路由全量回归的
 * 既有 MockMvc 用例兜底（秒级流程均在节流窗内不触发写）。</p>
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamSessionRenewalTest {

    private final String sessionIdHolder = UUID.randomUUID().toString();
    @Autowired
    private SessionService sessionService;
    @Autowired
    private IamSessionRepository sessionRepository;

    @AfterEach
    void cleanup() {
        sessionRepository.findById(sessionIdHolder).ifPresent(sessionRepository::delete);
    }

    @Test
    @DisplayName("节流窗内的活跃请求零写：expiresAt 不变")
    void touchInsideThrottleWindowSkipsWrite() {
        IamSessionEntity session = createSession(null, null, null);
        Instant expiresAtBefore = reload(session.getId()).getExpiresAt();
        sessionService.touchIfNeeded(session);
        assertEquals(expiresAtBefore, reload(session.getId()).getExpiresAt());
    }

    @Test
    @DisplayName("节流窗外的活跃请求滑动续期：expiresAt 前移至约 now+expiresIn")
    void touchOutsideThrottleWindowRenews() {
        Instant now = Instant.now();
        IamSessionEntity session = createSession(now, now.minusSeconds(600), now.minusSeconds(600).plusSeconds(1800));
        sessionService.touchIfNeeded(session);

        IamSessionEntity reloaded = reload(session.getId());
        assertTrue(reloaded.getExpiresAt().isAfter(now.plusSeconds(1500)));
        assertTrue(reloaded.getExpiresAt().isBefore(now.plusSeconds(1900)));
        // 库列 TIMESTAMP(0) 秒级，写入舍入至多损 1 秒
        assertTrue(reloaded.getLastActiveAt().isAfter(now.minusSeconds(1)));
    }

    @Test
    @DisplayName("到天花板后续期被 min 截断：expiresAt 等于 issuedAt+absoluteExpiresIn")
    void touchIsCappedByAbsoluteLimit() {
        Instant now = Instant.now();
        // 截秒对齐库列 TIMESTAMP(0) 精度，保精确断言成立
        Instant issuedAt = now.minusSeconds(35940).truncatedTo(ChronoUnit.SECONDS);
        IamSessionEntity session = createSession(
                issuedAt, now.minusSeconds(600), issuedAt.plusSeconds(1800));
        sessionService.touchIfNeeded(session);

        IamSessionEntity reloaded = reload(session.getId());
        assertEquals(issuedAt.plusSeconds(36000), reloaded.getExpiresAt());
    }

    @Test
    @DisplayName("已续到天花板后零写：再次活跃不改变 expiresAt")
    void touchAtCeilingSkipsWrite() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant ceiling = now.minusSeconds(36000).plusSeconds(36000);
        IamSessionEntity session = createSession(
                now.minusSeconds(36000), now.minusSeconds(600), ceiling);
        sessionService.touchIfNeeded(session);
        assertEquals(ceiling, reload(session.getId()).getExpiresAt());
    }

    @Test
    @DisplayName("复活守卫：已撤销行的续期 UPDATE 命中 0 行，数据不变")
    void touchDoesNotReviveRevokedSession() {
        Instant now = Instant.now();
        IamSessionEntity session = createSession(now.minusSeconds(600), now.minusSeconds(600),
                now.minusSeconds(600).plusSeconds(1800));
        session.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        sessionRepository.save(session);

        IamSessionEntity snapshot = reload(session.getId());
        sessionService.touchIfNeeded(snapshot);
        assertEquals(snapshot.getExpiresAt(), reload(session.getId()).getExpiresAt());
        assertEquals(snapshot.getLastActiveAt(), reload(session.getId()).getLastActiveAt());
    }

    @Test
    @DisplayName("绝对上限过期后 findActiveById 判死")
    void expiredBeyondAbsoluteLimitIsInactive() {
        Instant now = Instant.now();
        IamSessionEntity session = createSession(now.minusSeconds(40000), now.minusSeconds(40000),
                now.minusSeconds(40000).plusSeconds(36000));
        assertNull(sessionService.findActiveById(session.getId()));
    }

    @Test
    @DisplayName("配置校验：absoluteExpiresIn 不大于 expiresIn 或 renewThreshold 非正时启动失败")
    void invalidClockConfigFailsFast() {
        assertThrows(ConfigurationException.class,
                () -> validateWithConfig(1800, 1800, 300));
        assertThrows(ConfigurationException.class,
                () -> validateWithConfig(36000, 1800, 0));
        validateWithConfig(36000, 1800, 300);
    }

    private void validateWithConfig(int absoluteExpiresIn, int expiresIn, int renewThreshold) {
        SimpleIamServerProperties properties = new SimpleIamServerProperties();
        properties.getSession().setAbsoluteExpiresIn(absoluteExpiresIn);
        properties.getSession().setExpiresIn(expiresIn);
        properties.getSession().setRenewThreshold(renewThreshold);
        SessionService service = new SessionService(
                Mockito.mock(IamSessionRepository.class),
                Mockito.mock(RedisTokenRepository.class),
                Mockito.mock(IamAuthorizationRevocationSupport.class),
                properties,
                Mockito.mock(IamAuditEventPublisher.class),
                Mockito.mock(ObjectProvider.class));
        service.validateSessionClock();
    }

    /**
     * 造一条活跃会话行；三个回拨参数任一非空则覆盖对应时间列（模拟历史时刻）
     */
    private IamSessionEntity createSession(Instant issuedAt, Instant lastActiveAt, Instant expiresAt) {
        IamSessionEntity session = new IamSessionEntity();
        Instant now = Instant.now();
        session.setId(sessionIdHolder);
        session.setUserId(1000000L + ThreadLocalRandom.current().nextLong(100000));
        session.setUsername("renewal-user");
        session.setIssuedAt(issuedAt != null ? issuedAt : now);
        session.setLastActiveAt(lastActiveAt != null ? lastActiveAt : now);
        session.setExpiresAt(expiresAt != null ? expiresAt : now.plusSeconds(1800));
        session.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        return sessionRepository.save(session);
    }

    private IamSessionEntity reload(String id) {
        return sessionRepository.findById(id).orElseThrow(
                () -> new AssertionError("测试会话行不存在：" + id));
    }
}
