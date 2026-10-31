package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 真实 MySQL 吊销 DATA 回归：按目标种类真实关联授权、SQL过滤先于分页与同目标唯一去重。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RevocationPersistenceTest {

    @Autowired
    private RevocationRepository revocations;
    @Autowired
    private SigningOperationRepository operations;
    @Autowired
    private ActivationRepository activations;
    @Autowired
    private SubjectAssignmentRepository assignments;
    private final List<String> revocationIds = new ArrayList<>();
    private final List<String> operationIds = new ArrayList<>();
    private final List<String> activationIds = new ArrayList<>();
    private final List<String> assignmentIds = new ArrayList<>();
    private String prefix;

    @BeforeEach
    void setUp() {
        prefix = "rv-test-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanup() {
        revocations.deleteAllById(revocationIds);
        operations.deleteAllById(operationIds);
        activations.deleteAllById(activationIds);
        assignments.deleteAllById(assignmentIds);
    }

    @Test
    void credentialOwnershipFilterPrecedesPagingAndCountsAllMatchingRows() {
        String grantId = prefix + "-grant";
        for (int i = 0; i < 25; i++) {
            credential("match-" + String.format("%02d", i), grantId, prefix + "-cred-" + i);
        }
        for (int i = 0; i < 30; i++) {
            credential("zforeign-" + String.format("%02d", i), prefix + "-foreign", prefix + "-foreign-cred-" + i);
        }
        Page<RevocationEntity> first = revocations.findVisible(Collections.singleton(grantId), "CREDENTIAL", null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "revocationId")));
        Page<RevocationEntity> second = revocations.findVisible(Collections.singleton(grantId), "CREDENTIAL", null,
                PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "revocationId")));
        log.info("真实MySQL吊销分页：firstSize={}，secondSize={}，total={}",
                first.getNumberOfElements(), second.getNumberOfElements(), second.getTotalElements());
        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(5);
        assertThat(second.getTotalElements()).isEqualTo(25);
        assertThat(second.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).allSatisfy(row ->
                assertThat(row.getTargetId()).startsWith(prefix + "-cred-"));
    }

    @Test
    void equalNumberAcrossKindsDoesNotGrantOwnership() {
        String grantId = prefix + "-grant";
        credential("collision", prefix + "-foreign", grantId);
        log.info("凭据编号碰巧等于允许的grantId也不能放行");
        assertThat(revocations.findVisible(Collections.singleton(grantId), "CREDENTIAL", grantId,
                PageRequest.of(0, 20)).getTotalElements()).isZero();
        assertThat(revocations.findVisibleById(prefix + "-collision", Collections.singleton(grantId))).isEmpty();
    }

    @Test
    void activationAndAssignmentResolveRealGrantWhileGlobalKeyIsHiddenFromRestrictedData() {
        String grantId = prefix + "-grant";
        String activationId = prefix + "-activation";
        String assignmentId = prefix + "-assignment";
        activationIds.add(activationId);
        assignmentIds.add(assignmentId);
        activations.saveAndFlush(new ActivationEntity(activationId, grantId, "deployment-test", "{}"));
        assignments.saveAndFlush(new SubjectAssignmentEntity(assignmentId, grantId, 1L, "PERSON", "iam",
                "user-test", "[]"));
        revoke("activation", "ACTIVATION", activationId);
        revoke("assignment", "ASSIGNMENT", assignmentId);
        revoke("grant", "GRANT", grantId);
        revoke("key", "SIGNING_KEY", prefix + "-kid");
        log.info("子资源按grant关联，签名密钥台账仅ALL可见");
        assertThat(revocations.findVisible(Collections.singleton(grantId), null, null,
                PageRequest.of(0, 20)).getTotalElements()).isEqualTo(3);
        assertThat(revocations.findVisibleById(prefix + "-assignment", Collections.singleton(grantId))).isPresent();
        assertThat(revocations.findVisibleById(prefix + "-key", Collections.singleton(grantId))).isEmpty();
        assertThat(revocations.findVisibleById(prefix + "-key", null)).isPresent();
        assertThat(revocations.findVisible(Collections.emptySet(), null, null,
                PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    void uniqueTargetConstraintKeepsFirstIrreversibleFact() {
        String targetId = prefix + "-grant";
        revoke("first", "GRANT", targetId);
        String loserId = prefix + "-duplicate";
        log.info("同目标第二次登记由真实数据库唯一键拒绝");
        assertThatThrownBy(() -> revocations.saveAndFlush(new RevocationEntity(loserId, "GRANT", targetId,
                "OTHER", "iam:another"))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(revocations.findByTargetKindAndTargetId("GRANT", targetId))
                .isPresent().get().extracting(RevocationEntity::getRevocationId).isEqualTo(prefix + "-first");
    }

    private void credential(String suffix, String grantId, String credentialId) {
        String operationId = prefix + "-op-" + suffix;
        operationIds.add(operationId);
        operations.saveAndFlush(new SigningOperationEntity(operationId, credentialId, grantId, "sp-test",
                "header", "payload", prefix + "-idem-" + suffix));
        revoke(suffix, "CREDENTIAL", credentialId);
    }

    private void revoke(String suffix, String kind, String targetId) {
        String revocationId = prefix + "-" + suffix;
        revocationIds.add(revocationId);
        revocations.saveAndFlush(new RevocationEntity(revocationId, kind, targetId, "SECURITY", "iam:test"));
    }
}
