package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantController;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 真实 MySQL 凭据列表回归：SQL 筛选先于分页，DATA、授权、激活三条件求交。
 * 使用已有持久化测试上下文，数据只清理本用例拥有的任务主键。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CredentialListPersistenceTest {

    @Autowired
    private SigningOperationRepository operations;

    private LicenseGrantController controller;
    private final List<String> ownedIds = new ArrayList<>();
    private String prefix;

    @BeforeEach
    void setUp() {
        prefix = "credential-list-" + UUID.randomUUID();
        controller = new LicenseGrantController(mock(GrantRepository.class), mock(ProductRepository.class),
                mock(SigningProfileRepository.class), operations, null, null, null, null);
    }

    @AfterEach
    void cleanupOwnedOperations() {
        operations.deleteAllById(ownedIds);
    }

    @Test
    void activationFilterReturnsReadyRowsAcrossPagesWithRealTotals() throws Exception {
        String grantId = prefix + "-grant";
        String activationId = prefix + "-activation";
        for (int i = 0; i < 25; i++) {
            save("match-" + String.format("%02d", i), grantId, activationId);
        }
        // 排序在匹配行之前的其他激活占满原始首屏；分页前过滤应仍返回匹配页。
        for (int i = 0; i < 30; i++) {
            save("zother-" + String.format("%02d", i), grantId, prefix + "-other");
        }
        JsonNode first = list(TestPlans.all("read"), 1, 20, null, activationId);
        JsonNode second = list(TestPlans.all("read"), 2, 20, null, activationId);
        assertThat(first.path("total").asLong()).isEqualTo(25);
        assertThat(first.path("data").size()).isEqualTo(20);
        assertThat(second.path("total").asLong()).isEqualTo(25);
        assertThat(second.path("page").asInt()).isEqualTo(2);
        assertThat(second.path("size").asInt()).isEqualTo(20);
        assertThat(second.path("totalPages").asInt()).isEqualTo(2);
        assertThat(second.path("data").size()).isEqualTo(5);
        for (JsonNode credential : second.path("data")) {
            assertThat(credential.path("activationId").asText()).isEqualTo(activationId);
            assertThat(credential.path("state").asText()).isEqualTo("READY");
        }
        String credentialId = second.path("data").get(0).path("credentialId").asText();
        assertThat(controller.deliverContent(credentialId, TestPlans.all("deliver"),
                LicenseIdentityHttpTest.TestRequests.authenticated()).getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void restrictedDataExplicitGrantAndActivationFiltersIntersect() throws Exception {
        String grantOne = prefix + "-grant-1";
        String grantTwo = prefix + "-grant-2";
        String foreignGrant = prefix + "-foreign";
        String activationOne = prefix + "-activation-1";
        save("one", grantOne, activationOne);
        save("two", grantTwo, activationOne);
        save("foreign", foreignGrant, activationOne);
        save("other-activation", grantOne, prefix + "-activation-2");

        JsonNode scoped = list(TestPlans.restricted("read", grantOne, grantTwo),
                1, 20, null, activationOne);
        assertThat(scoped.path("total").asLong()).isEqualTo(2);
        JsonNode intersected = list(TestPlans.restricted("read", grantOne, grantTwo),
                1, 20, grantOne, activationOne);
        assertThat(intersected.path("total").asLong()).isEqualTo(1);
        assertThat(intersected.path("data").get(0).path("grantId").asText()).isEqualTo(grantOne);

        JsonNode excluded = list(TestPlans.restricted("read", grantOne, grantTwo),
                1, 20, foreignGrant, activationOne);
        assertThat(excluded.path("total").asLong()).isZero();
        assertThat(excluded.path("data")).isEmpty();
        assertThat(operations.findCredentials(Collections.<String>emptySet(), null, activationOne,
                PageRequest.of(0, 20, Sort.by("operationId"))).getTotalElements()).isZero();
    }

    @Test
    void unknownActivationKeepsRequestedPageMetadata() throws Exception {
        save("one", prefix + "-grant", prefix + "-activation");
        JsonNode empty = list(TestPlans.all("read"), 3, 20, null, prefix + "-missing");
        assertThat(empty.path("data").size()).isZero();
        assertThat(empty.path("total").asLong()).isZero();
        assertThat(empty.path("page").asInt()).isEqualTo(3);
        assertThat(empty.path("size").asInt()).isEqualTo(20);
        assertThat(empty.path("totalPages").asInt()).isZero();
    }

    private JsonNode list(io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan plan,
                          int page, int size, String grantId, String activationId) throws Exception {
        return StrictJson.mapper().readTree(controller.listCredentials(
                LicenseIdentityHttpTest.TestRequests.authenticated(), plan, page, size,
                grantId, activationId).getBody());
    }

    private void save(String suffix, String grantId, String activationId) {
        String operationId = prefix + "-" + suffix;
        String payload = CompactJws.encode(("{\"subjectBinding\":{\"kind\":\"DEPLOYMENT\","
                + "\"activationId\":\"" + activationId + "\",\"activationGeneration\":1}}")
                .getBytes(StandardCharsets.UTF_8));
        SigningOperationEntity entity = new SigningOperationEntity(operationId, operationId + "-cred",
                grantId, "profile-1", "header", payload, operationId + "-idem");
        entity.setState(OperationState.READY.getCode());
        entity.setCommittedSignature("signature");
        ownedIds.add(operationId);
        operations.saveAndFlush(entity);
    }
}
