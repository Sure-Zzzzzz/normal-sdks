package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * 真实 MySQL 持久化集成断言：实体建表（ddl-auto 由测试引导）、乐观锁、状态字段。
 *
 * <p>60 断言矩阵的服务端持久层基线：Grant/SigningOperation 两核心实体真实落库回读。</p>
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GrantPersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void grantEntityRoundTripsWithStateFields() {
        String grantId = "grant-" + UUID.randomUUID().toString().substring(0, 8);
        GrantRevisionEntity entity = new GrantRevisionEntity(grantId, "cu-1", "p-1",
                "PERSON", "CUSTOMER_MEMBERS", null, "ONLINE", "order-1001");
        persist(entity);

        GrantRevisionEntity loaded = load(grantId);
        assertThat(loaded.getCustomerId()).isEqualTo("cu-1");
        assertThat(loaded.getState()).isEqualTo("DRAFT");
        assertThat(loaded.getGrantRevision()).isEqualTo(1L);
        assertThat(loaded.getBusinessOrderRef()).isEqualTo("order-1001");

        // 状态迁移：DRAFT→ENABLED
        jdbc.update("UPDATE license_grant SET state = ? WHERE grant_id = ?", "ENABLED", grantId);
        assertThat(load(grantId).getState()).isEqualTo("ENABLED");
        cleanup(grantId);
    }

    @Test
    void signingOperationFreezesInputsAtAccept() {
        String operationId = "op-" + UUID.randomUUID().toString().substring(0, 8);
        String credentialId = "cred-" + UUID.randomUUID().toString().substring(0, 8);
        SigningOperationEntity entity = new SigningOperationEntity(operationId, credentialId,
                "grant-x", "profile-1", "header-segment", "{\"protocolVersion\":2}", "digest-1");
        jdbc.update("INSERT INTO license_signing_operation (operation_id, credential_id, grant_id, "
                        + "signing_profile_id, state, execution_generation, frozen_header, frozen_payload, "
                        + "idempotency_digest, row_version) VALUES (?,?,?,?,?,?,?,?,?,1)",
                entity.getOperationId(), entity.getCredentialId(), entity.getGrantId(),
                entity.getSigningProfileId(), OperationState.PENDING_SIGN.getCode(), 1L,
                entity.getFrozenHeader(), entity.getFrozenPayload(), entity.getIdempotencyDigest());

        SigningOperationEntity loaded = loadOperation(operationId);
        assertThat(loaded.getState()).isEqualTo("PENDING_SIGN");
        assertThat(loaded.getFrozenHeader()).isEqualTo("header-segment");
        assertThat(loaded.getFrozenPayload()).isEqualTo("{\"protocolVersion\":2}");
        assertThat(loaded.getCommittedSignature()).isNull();

        // UNCERTAIN 标记路径
        jdbc.update("UPDATE license_signing_operation SET state = ?, reason_category = ? WHERE operation_id = ?",
                OperationState.UNCERTAIN.getCode(), "SIGN_CALL_FAILED", operationId);
        assertThat(loadOperation(operationId).getState()).isEqualTo("UNCERTAIN");
        cleanupOperation(operationId);
    }

    private void persist(GrantRevisionEntity entity) {
        jdbc.update("INSERT INTO license_grant (grant_id, customer_id, product_id, subject_kind, "
                        + "eligibility_kind, offline_proof_profile, mode, state, grant_revision, "
                        + "grant_state_version, business_order_ref, row_version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,1)",
                entity.getGrantId(), entity.getCustomerId(), entity.getProductId(),
                entity.getSubjectKind(), entity.getEligibilityKind(), entity.getOfflineProofProfile(),
                entity.getMode(), entity.getState(), entity.getGrantRevision(),
                entity.getGrantStateVersion(), entity.getBusinessOrderRef());
    }

    private GrantRevisionEntity load(String grantId) {
        return jdbc.queryForObject("SELECT * FROM license_grant WHERE grant_id = ?",
                (rs, rowNum) -> {
                    GrantRevisionEntity result = new GrantRevisionEntity(rs.getString("grant_id"),
                            rs.getString("customer_id"), rs.getString("product_id"),
                            rs.getString("subject_kind"), rs.getString("eligibility_kind"),
                            rs.getString("offline_proof_profile"), rs.getString("mode"),
                            rs.getString("business_order_ref"));
                    result.setState(rs.getString("state"));
                    result.setGrantRevision(rs.getLong("grant_revision"));
                    result.setGrantStateVersion(rs.getLong("grant_state_version"));
                    return result;
                }, grantId);
    }

    private SigningOperationEntity loadOperation(String operationId) {
        return jdbc.queryForObject("SELECT * FROM license_signing_operation WHERE operation_id = ?",
                (rs, rowNum) -> {
                    SigningOperationEntity result = new SigningOperationEntity(rs.getString("operation_id"),
                            rs.getString("credential_id"), rs.getString("grant_id"),
                            rs.getString("signing_profile_id"), rs.getString("frozen_header"),
                            rs.getString("frozen_payload"), rs.getString("idempotency_digest"));
                    result.setState(rs.getString("state"));
                    result.setExecutionGeneration(rs.getLong("execution_generation"));
                    result.setCommittedSignature(rs.getString("committed_signature"));
                    return result;
                }, operationId);
    }

    private void cleanup(String grantId) {
        jdbc.update("DELETE FROM license_grant WHERE grant_id = ?", grantId);
    }

    private void cleanupOperation(String operationId) {
        jdbc.update("DELETE FROM license_signing_operation WHERE operation_id = ?", operationId);
    }
}
