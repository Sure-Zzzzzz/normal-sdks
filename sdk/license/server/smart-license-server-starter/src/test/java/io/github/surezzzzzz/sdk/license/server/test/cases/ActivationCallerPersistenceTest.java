package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * 真实MySQL激活调用者台账：持久化来源/主体、存量不猜绑定、并发管理改绑遵守行版本。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ActivationCallerPersistenceTest {

    @Autowired
    private ActivationRepository activations;
    private final String activationId = "act-caller-" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        if (activations.existsById(activationId)) {
            activations.deleteById(activationId);
        }
    }

    @Test
    void managedCallerPersistsExactlyWithNamespaceAndStableServiceIdentifier() {
        activations.saveAndFlush(new ActivationEntity(activationId, "gr-caller-test", "deployment-test", "{}",
                "registered-source", "service-one"));
        ActivationEntity persisted = activations.findById(activationId).get();
        log.info("真实MySQL激活调用者绑定保存来源和编号，不折叠命名空间");
        assertThat(persisted.getCallerSourceId()).isEqualTo("registered-source");
        assertThat(persisted.getCallerSubjectId()).isEqualTo("service-one");
        assertThat(persisted.getComponentDigests()).isEqualTo("{}");
        assertThat(persisted.getState()).isEqualTo("ACTIVE");
    }

    @Test
    void legacyUnboundRecordRemainsUnboundUntilExplicitGovernance() {
        activations.saveAndFlush(new ActivationEntity(activationId, "gr-caller-test", "deployment-test", "{}"));
        ActivationEntity legacy = activations.findById(activationId).get();
        log.info("真实MySQL旧行不因读取自动猜绑定，经管理写入才有caller");
        assertThat(legacy.getCallerSourceId()).isNull();
        assertThat(legacy.getCallerSubjectId()).isNull();
        long version = legacy.getRowVersion();
        legacy.bindCaller("aksk", "approved-service");
        activations.saveAndFlush(legacy);
        ActivationEntity governed = activations.findById(activationId).get();
        assertThat(governed.getCallerSubjectId()).isEqualTo("approved-service");
        assertThat(governed.getRowVersion()).isEqualTo(version + 1);
    }

    @Test
    void concurrentStaleBindingCannotOverwriteTheCommittedApprovedCaller() {
        activations.saveAndFlush(new ActivationEntity(activationId, "gr-caller-test", "deployment-test", "{}"));
        ActivationEntity winner = activations.findById(activationId).get();
        ActivationEntity stale = activations.findById(activationId).get();
        winner.bindCaller("aksk", "approved-service");
        activations.saveAndFlush(winner);
        stale.bindCaller("another-source", "another-service");
        log.info("真实MySQL并发旧版本改绑拒绝，许可服务保持赢家事实");
        assertThatThrownBy(() -> activations.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        ActivationEntity result = activations.findById(activationId).get();
        assertThat(result.getCallerSourceId()).isEqualTo("aksk");
        assertThat(result.getCallerSubjectId()).isEqualTo("approved-service");
    }
}
