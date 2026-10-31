package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseModelController;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 模型管理面 HTTP 语义断言：创建（快照经发布级校验）、详情附带快照原文（草稿编辑回填源）、
 * 草稿编辑行版本围栏、发布固化、退役边界与受限试算。
 *
 * @author surezzzzzz
 */
class LicenseModelHttpTest {

    private ModelVersionRepository models;
    private ProductRepository products;
    private LicenseModelController controller;

    private final Map<String, ModelVersionEntity> store = new HashMap<>();
    private final AtomicLong rowVersions = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        models = Mockito.mock(ModelVersionRepository.class);
        products = Mockito.mock(ProductRepository.class);
        store.clear();
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        when(models.save(any(ModelVersionEntity.class))).thenAnswer(call -> {
            ModelVersionEntity entity = call.getArgument(0);
            store.put(entity.getModelVersionId(), entity);
            return entity;
        });
        when(models.findAll()).thenReturn(new java.util.ArrayList<>(store.values()));
        when(models.findByProductIdOrderByVersionDesc("prod-1"))
                .thenReturn(new java.util.ArrayList<>(store.values()));
        when(models.findById(any(String.class)))
                .thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0))));
        controller = new LicenseModelController(models, products);
    }

    /** channelLimit×channelCountAfter LTE 树——合法形态（facts 嵌于 fields 内，与真实演示链同内容）。 */
    private static String validSnapshot() {
        return LicenseTestModels.publishedSnapshot();
    }

    private static JsonNode parse(ResponseEntity<String> response) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
    }

    @Test
    void createModelStoresValidatedSnapshotAsDraft() throws Exception {
        ResponseEntity<String> created = controller.createModel(
                "{\"productId\":\"prod-1\",\"snapshot\":" + validSnapshot() + "}", "idem-m1",
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(created.getStatusCodeValue()).isEqualTo(201);
        JsonNode body = parse(created);
        assertThat(body.get("status").asText()).isEqualTo("DRAFT");
        assertThat(body.get("actionsSummary").asText()).isEqualTo("1 个动作");
    }

    @Test
    void createModelRejectsInvalidSnapshotShape() {
        // 未知节点键（既非组合也非叶子）：发布级校验拒绝，不落库
        assertThatThrownBy(() -> controller.createModel(
                "{\"productId\":\"prod-1\",\"snapshot\":{\"actions\":{\"a.create\":{\"xor\":[]}}}}",
                "idem-m2", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void getVersionDetailCarriesSnapshotForDraftEditBackfill() throws Exception {
        ModelVersionEntity entity = new ModelVersionEntity("mv-1", "mdl-1", "prod-1", 1L, validSnapshot());
        store.put("mv-1", entity);
        JsonNode detail = parse(controller.getVersion("mv-1", TestPlans.allFor("license-product", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated()));
        assertThat(detail.get("modelVersionId").asText()).isEqualTo("mv-1");
        assertThat(detail.get("snapshot").isObject()).isTrue();
        assertThat(detail.get("snapshot").path("fields").path("rights").has("channelLimit")).isTrue();
    }

    @Test
    void modelDetailHidesForeignAndMissingProductsWithTheSameStatus() {
        store.put("mv-hidden", new ModelVersionEntity("mv-hidden", "mdl-hidden", "prod-hidden", 1L, validSnapshot()));
        assertThatThrownBy(() -> controller.getVersion("mv-hidden",
                TestPlans.restrictedFor("license-product", "read", "productId", "prod-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(404);
        assertThatThrownBy(() -> controller.getVersion("mv-missing",
                TestPlans.restrictedFor("license-product", "read", "productId", "prod-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(404);
    }

    @Test
    void patchDraftRequiresFreshRowVersion() throws Exception {
        ModelVersionEntity entity = new ModelVersionEntity("mv-2", "mdl-2", "prod-1", 1L, validSnapshot());
        store.put("mv-2", entity);
        long stale = rowVersions.get();

        assertThatThrownBy(() -> controller.patchDraft("mv-2",
                "{\"snapshot\":" + validSnapshot() + ",\"expectedRowVersion\":" + (stale + 99) + "}",
                TestPlans.allFor("license-product", "manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("版本已变化");

        ResponseEntity<String> patched = controller.patchDraft("mv-2",
                "{\"snapshot\":" + validSnapshot() + ",\"expectedRowVersion\":" + entity.getRowVersion() + "}",
                TestPlans.allFor("license-product", "manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(patched.getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void publishFreezesThenRetireBoundary() throws Exception {
        ModelVersionEntity entity = new ModelVersionEntity("mv-3", "mdl-3", "prod-1", 1L, validSnapshot());
        store.put("mv-3", entity);

        ResponseEntity<String> published = controller.publish("mv-3",
                "{\"expectedRowVersion\":" + entity.getRowVersion() + "}",
                TestPlans.allFor("license-product", "publish"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(published.getStatusCodeValue()).isEqualTo(200);
        assertThat(parse(published).get("status").asText()).isEqualTo("PUBLISHED");

        // 已发布快照不可变：草稿编辑被拒
        assertThatThrownBy(() -> controller.patchDraft("mv-3",
                "{\"snapshot\":" + validSnapshot() + ",\"expectedRowVersion\":" + entity.getRowVersion() + "}",
                TestPlans.allFor("license-product", "manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("不可变");

        ResponseEntity<String> retired = controller.retire("mv-3",
                "{\"state\":\"RETIRED\",\"expectedRowVersion\":" + entity.getRowVersion() + "}",
                TestPlans.allFor("license-product", "publish"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(parse(retired).get("status").asText()).isEqualTo("RETIRED");
    }

    @Test
    void evaluateReportsDeclarationWithoutIssuing() throws Exception {
        ModelVersionEntity entity = new ModelVersionEntity("mv-4", "mdl-4", "prod-1", 1L, validSnapshot());
        store.put("mv-4", entity);
        JsonNode outcome = parse(controller.evaluate("mv-4",
                "{\"action\":\"channel.create\",\"rights\":{\"channelLimit\":10},"
                        + "\"facts\":{\"channelCountAfter\":3}}",
                TestPlans.allFor("license-product", "read"),
                LicenseIdentityHttpTest.TestRequests.authenticated()));
        assertThat(outcome.get("declared").asBoolean()).isTrue();
        assertThat(outcome.has("credential")).isFalse();
    }

    private static ProductEntity product() {
        return new ProductEntity("prod-1", "iss-1", "channel-suite", "渠道套件",
                "channel.create", "");
    }
}
