package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseRevocationController;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.LimiterPolicyProjectionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 吊销HTTP契约：分页结果透传、目标类型校验、越界不可见与唯一冲突审计只记赢家。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseRevocationControllerTest {

    private final RevocationRepository repository = mock(RevocationRepository.class);
    private final LicenseRevocationGuard guard = mock(LicenseRevocationGuard.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private LicenseRevocationController controller;

    @BeforeEach
    void setUp() {
        controller = new LicenseRevocationController(repository, guard);
        ReflectionTestUtils.setField(controller, "eventPublisher", publisher);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPassesRestrictedGrantIdsToSqlAndPreservesTruePageTotals() {
        when(repository.findVisible(eq(Collections.singleton("gr-1")), eq("CREDENTIAL"), eq(null),
                any(Pageable.class))).thenReturn(new PageImpl<>(Collections.nCopies(5, row()),
                PageRequest.of(1, 20), 25L));
        String result = controller.list(auth(), "CREDENTIAL", null,
                TestPlans.restricted("read", "gr-1"), 2, 20).getBody();
        log.info("SQL范围求交后分页，Controller透传total与totalPages");
        assertThat(result).contains("\"total\":25", "\"page\":2", "\"totalPages\":2", "\"size\":20");
        verify(repository).findVisible(eq(Collections.singleton("gr-1")), eq("CREDENTIAL"), eq(null),
                any(Pageable.class));
    }

    @Test
    void malformedPaginationAndUnknownKindRejectBeforeDatabaseQuery() {
        log.info("吊销非法分页与目标种类分别400/422");
        assertStatus(() -> controller.list(auth(), null, null, TestPlans.all("read"), 0, 20), 400);
        assertStatus(() -> controller.list(auth(), null, null, TestPlans.all("read"), 1, 101), 400);
        assertStatus(() -> controller.list(auth(), "UNKNOWN", null, TestPlans.all("read"), 1, 20), 422);
        verify(repository, never()).findVisible(any(), any(), any(), any());
    }

    @Test
    void nonexistentTypedTargetRejectsBeforeSaveAndAudit() {
        doThrow(LicenseHttpException.notFound()).when(guard)
                .requireTargetExists(RevocationTargetKind.CREDENTIAL, "cred-1");
        log.info("目标类型先校验真实实体，不存在则不落吊销台账");
        assertStatus(() -> controller.create(body(), "idem-1", auth()), 404);
        verify(repository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void existingTargetReturnsConflictWithoutChangingOriginalOrPublishingAgain() {
        when(repository.findByTargetKindAndTargetId("CREDENTIAL", "cred-1"))
                .thenReturn(Optional.of(row()));
        log.info("重复目标维持409，首次不可撤回事实保持");
        assertStatus(() -> controller.create(body(), "idem-repeat", auth()), 409);
        verify(repository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void uniqueRaceReportsConflictAndDoesNotPublishLoserAudit() {
        when(repository.findByTargetKindAndTargetId("CREDENTIAL", "cred-1"))
                .thenReturn(Optional.empty(), Optional.of(row()));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fixture collision"));
        log.info("唯一竞争输家409，不重复发送成功审计");
        assertStatus(() -> controller.create(body(), "idem-race", auth()), 409);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unrelatedIntegrityFailureDoesNotPretendTargetAlreadyRevoked() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("fixture storage failure");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        log.info("非同目标唯一冲突原样透传，不伪装409");
        assertThatThrownBy(() -> controller.create(body(), "idem-error", auth())).isSameAs(failure);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void successfulCreationPublishesExactlyOneEvent() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        log.info("吊销创建201，成功审计只发布一次");
        assertThat(controller.create(body(), "idem-create", auth()).getStatusCodeValue()).isEqualTo(201);
        verify(publisher).publishEvent(any(Object.class));
        verify(guard).requireTargetExists(RevocationTargetKind.CREDENTIAL, "cred-1");
    }

    @Test
    void subordinateRevocationDoesNotDisableGrantLevelLimiterPolicy() {
        LimiterPolicyProjectionService projection = mock(LimiterPolicyProjectionService.class);
        ReflectionTestUtils.setField(controller, "limiterPolicyProjectionService", projection);
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        log.info("单个凭据吊销不改变整份授权的客户级限频策略");

        controller.create(body(), "idem-subordinate", auth());

        verify(projection, never()).disable(any());
        verify(guard, never()).findGrantForTarget(any(), any());
    }

    @Test
    void detailOutsideSqlDataScopeIsNotFound() {
        when(repository.findVisibleById("rv-1", Collections.singleton("gr-other"))).thenReturn(Optional.empty());
        log.info("详情同SQL DATA范围，越界404");
        assertStatus(() -> controller.get("rv-1", TestPlans.restricted("read", "gr-other"), auth()), 404);
    }

    private RevocationEntity row() {
        return new RevocationEntity("rv-1", "CREDENTIAL", "cred-1", "SECURITY", "iam:admin-test");
    }

    private String body() {
        return "{\"targetKind\":\"CREDENTIAL\",\"targetId\":\"cred-1\",\"reasonCategory\":\"SECURITY\"}";
    }

    private javax.servlet.http.HttpServletRequest auth() {
        return LicenseIdentityHttpTest.TestRequests.authenticated();
    }

    private void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }
}
