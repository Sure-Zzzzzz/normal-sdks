package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaBalanceRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.OperationRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 授权与签发链 HTTP 语义断言：创建围栏（对象种类/模式）、状态版本围栏、
 * 签发档案匹配围栏、凭据 READY 交付 no-store。
 *
 * @author surezzzzzz
 */
class LicenseGrantHttpTest {

    private GrantRepository grants;
    private ProductRepository products;
    private SigningProfileRepository profiles;
    private SigningOperationRepository operations;
    private CredentialIssuanceService issuance;
    private io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository models;
    private io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService quotaLedger;
    private java.util.concurrent.ConcurrentHashMap<String, io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity> quotaStore;
    private LicenseGrantController controller;

    private final java.util.Map<String, SigningOperationEntity> operationStore =
            new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        grants = Mockito.mock(GrantRepository.class);
        products = Mockito.mock(ProductRepository.class);
        profiles = Mockito.mock(SigningProfileRepository.class);
        operations = Mockito.mock(SigningOperationRepository.class);
        // CredentialIssuanceService 是 final：用内存仓储构造真实实例（状态机真实流转，不 mock）
        OperationRepository memoryRepository =
                new OperationRepository() {
                    @Override
                    public void save(SigningOperationEntity entity) {
                        operationStore.put(entity.getOperationId(), entity);
                    }

                    @Override
                    public SigningOperationEntity find(String operationId) {
                        return operationStore.get(operationId);
                    }

                    @Override
                    public void update(SigningOperationEntity entity) {
                        operationStore.put(entity.getOperationId(), entity);
                    }
                };
        issuance = new CredentialIssuanceService(memoryRepository, null, profiles);
        models = Mockito.mock(io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository.class);
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity baseModel =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-base", "mdl-base", "prod-1", 1L, LicenseTestModels.publishedSnapshot());
        baseModel.publish();
        when(models.findById("mv-base")).thenReturn(Optional.of(baseModel));
        // JPA 额度台账的内存仓储形态（余额行真实语义，锁经单线程语义简化）
        QuotaBalanceRepository balanceRepo = Mockito.mock(QuotaBalanceRepository.class);
        quotaStore = new java.util.concurrent.ConcurrentHashMap<String, io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity>();
        Mockito.when(balanceRepo.findByGrantIdAndMetric(any(), any())).thenAnswer(call ->
                java.util.Optional.ofNullable(quotaStore.get(call.getArgument(0) + ":" + call.getArgument(1))));
        Mockito.when(balanceRepo.save(any())).thenAnswer(call -> {
            io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity entity = call.getArgument(0);
            quotaStore.put(entity.getGrantId() + ":" + entity.getMetric(), entity);
            return entity;
        });
        Mockito.when(balanceRepo.saveAndFlush(any())).thenAnswer(call -> {
            io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity entity = call.getArgument(0);
            quotaStore.put(entity.getGrantId() + ":" + entity.getMetric(), entity);
            return entity;
        });
        Mockito.when(balanceRepo.findWithLockByGrantIdAndMetric(any(), any())).thenAnswer(call ->
                java.util.Optional.ofNullable(quotaStore.get(call.getArgument(0) + ":" + call.getArgument(1))));
        quotaLedger = new io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService(balanceRepo);
        controller = new LicenseGrantController(grants, products, profiles, operations, issuance,
                models, quotaLedger, null);
    }

    @Test
    void createGrantRejectsUnknownSubjectKind() {
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        assertThatThrownBy(() -> controller.createGrant(
                grantBody("DEPLOYMENTX", "ALLOCATED_DEPLOYMENTS", "OFFLINE"), "idem-1",
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void createGrantRejectsUnknownMode() {
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        assertThatThrownBy(() -> controller.createGrant(
                grantBody("DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", "OFFLINEX"), "idem-2",
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void createGrantRejectsMissingProduct() {
        when(products.findById("prod-x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.createGrant(
                grantBody("DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", "OFFLINE").replace("prod-1", "prod-x"),
                "idem-3", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void patchStateRejectsStaleRowVersion() {
        GrantRevisionEntity entity = grant();
        when(grants.findById("gr-1")).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> controller.patchState("gr-1",
                "{\"state\":\"ENABLED\",\"expectedRowVersion\":99}",
                TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(409);
    }

    @Test
    void patchStateBumpsGrantStateVersion() {
        GrantRevisionEntity entity = grant();
        when(grants.findById("gr-1")).thenReturn(Optional.of(entity));
        when(grants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResponseEntity<String> response = controller.patchState("gr-1",
                "{\"state\":\"ENABLED\",\"expectedRowVersion\":0}",
                TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(response.getBody()).contains("\"state\":\"ENABLED\"")
                .contains("\"grantStateVersion\":2");
    }

    @Test
    void patchStateRejectsTerminalStateResurrection() {
        GrantRevisionEntity entity = grant();
        entity.setState("REVOKED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> controller.patchState("gr-1",
                "{\"state\":\"ENABLED\",\"expectedRowVersion\":0}",
                TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(409);
        assertThat(entity.getState()).isEqualTo("REVOKED");
        Mockito.verify(grants, Mockito.never()).save(any());
    }

    @Test
    void patchStateRejectsCoercedRowVersion() {
        GrantRevisionEntity entity = grant();
        when(grants.findById("gr-1")).thenReturn(Optional.of(entity));
        for (String version : new String[]{"0.5", "\"0\"", "null", "-1"}) {
            assertThatThrownBy(() -> controller.patchState("gr-1",
                    "{\"state\":\"ENABLED\",\"expectedRowVersion\":" + version + "}",
                    TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()))
                    .isInstanceOf(LicenseHttpException.class)
                    .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(422);
        }
        Mockito.verify(grants, Mockito.never()).save(any());
    }

    @Test
    void issueCredentialRejectsKidMismatch() {
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant()));
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        when(profiles.findByProductIdAndPurposeAndStatus("prod-1", "CREDENTIAL", "ACTIVE"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.requestCredential("gr-1",
                "{\"subjectRef\":\"{\\\"kind\\\":\\\"DEPLOYMENT\\\",\\\"sourceId\\\":\\\"x\\\",\\\"subjectId\\\":\\\"y\\\"}\","
                        + "\"kid\":\"unknown-kid\"}", "idem-4",
                TestPlans.all("issue"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void deliverContentRequiresReadyState() {
        SigningOperationEntity entity = new SigningOperationEntity("op-1", "cred-1", "gr-1",
                "sp-1", "h", "p", "digest");
        when(operations.findByCredentialId("cred-1")).thenReturn(entity);
        assertThatThrownBy(() -> controller.deliverContent("cred-1",
                TestPlans.all("deliver"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void listGrantsViewShapeMatchesContract() {
        when(grants.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(
                java.util.Collections.singletonList(grant()), PageRequest.of(0, 20), 1));
        ResponseEntity<String> response = controller.listGrants(
                LicenseIdentityHttpTest.TestRequests.authenticated(), TestPlans.all("read"), 1, 20, null);
        assertThat(response.getBody()).contains("\"grantId\":\"gr-1\"").contains("\"total\":1");
    }

    @Test
    void restrictedScopeFiltersListAndRejectsForeignGrant() {
        // 受限列表：走 IN 过滤查询（不接触全量数据）
        when(grants.findByGrantIdIn(any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.Collections.singletonList(grant()),
                        PageRequest.of(0, 20), 1));
        ResponseEntity<String> response = controller.listGrants(
                LicenseIdentityHttpTest.TestRequests.authenticated(), TestPlans.restricted("read", "gr-1"), 1, 20, null);
        assertThat(response.getBody()).contains("\"grantId\":\"gr-1\"");
        org.mockito.Mockito.verify(grants).findByGrantIdIn(any(), any(org.springframework.data.domain.Pageable.class));
        org.mockito.Mockito.verify(grants, org.mockito.Mockito.never())
                .findAll(any(org.springframework.data.domain.Pageable.class));

        // 详情越界与不存在统一隐藏为404；API权限拒绝仍由 require 独立返回403。
        when(grants.findById("gr-2")).thenReturn(Optional.of(grant()));
        assertThatThrownBy(() -> controller.getGrant("gr-2",
                TestPlans.restricted("read", "gr-1"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
        assertThatThrownBy(() -> controller.getGrant("gr-missing",
                TestPlans.restricted("read", "gr-1"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);

        assertThatThrownBy(() -> controller.patchState("gr-2",
                "{\"state\":\"SUSPENDED\",\"expectedRowVersion\":0}",
                TestPlans.restricted("manage", "gr-1"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void restrictedCredentialDeliveryRequiresGrantInScope() {
        SigningOperationEntity entity = new SigningOperationEntity("op-1", "cred-1", "gr-9",
                "sp-1", "h", "p", "digest");
        when(operations.findByCredentialId("cred-1")).thenReturn(entity);
        // 凭据挂在范围外授权上：与不存在的凭据统一隐藏为404。
        assertThatThrownBy(() -> controller.deliverContent("cred-1",
                TestPlans.restricted("deliver", "gr-1"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
        assertThatThrownBy(() -> controller.deliverContent("cred-missing",
                TestPlans.restricted("deliver", "gr-1"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
    }

    private static String grantBody(String subjectKind, String eligibility, String mode) {
        return "{\"customerId\":\"cu-1\",\"productId\":\"prod-1\",\"subjectKind\":\"" + subjectKind
                + "\",\"eligibilityKind\":\"" + eligibility + "\",\"mode\":\"" + mode
                + "\",\"modelVersionId\":\"mv-base\"}";
    }

    /** 模型关联形态的建授权体；2.0 授权必须冻结已发布模型。 */
    private static String modelGrantBody(String modelVersionId, String rightsJson) {
        StringBuilder body = new StringBuilder("{\"customerId\":\"cu-1\",\"productId\":\"prod-1\""
                + ",\"subjectKind\":\"PERSON\",\"eligibilityKind\":\"ASSIGNED_SUBJECTS\",\"mode\":\"ONLINE\"");
        if (modelVersionId != null) {
            body.append(",\"modelVersionId\":\"").append(modelVersionId).append("\"");
        }
        body.append(",\"rights\":").append(rightsJson).append("}");
        return body.toString();
    }

    private static ProductEntity product() {
        return new ProductEntity("prod-1", "iss-1", "channel-sys", "渠道管理系统",
                "[\"channel.create\"]", "[\"license-condition/1\"]");
    }

    private static GrantRevisionEntity grant() {
        return new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "DEPLOYMENT",
                "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null, "mv-base", "{}");
    }

    /** 含 quota 段的已发布模型（rights 声明 channelLimit INTEGER 0..100000）。 */
    private static io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity publishedModel(
            String snapshot) {
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity entity =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-g1", "mdl-g1", "prod-1", 1L, snapshot);
        entity.publish();
        return entity;
    }

    private static final String MODEL_SNAPSHOT_WITH_QUOTA = LicenseTestModels.publishedNamedSeatSnapshot();

    @Test
    void createGrantWithModelValidatesRightsAgainstDeclaration() throws Exception {
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        when(models.findById("mv-g1")).thenReturn(Optional.of(publishedModel(MODEL_SNAPSHOT_WITH_QUOTA)));
        org.mockito.Mockito.doAnswer(call -> {
            GrantRevisionEntity saved = call.getArgument(0);
            saved.setGrantRevision(1L);
            return saved;
        }).when(grants).save(any(GrantRevisionEntity.class));

        // 未声明权益字段：拒绝
        assertThatThrownBy(() -> controller.createGrant(
                modelGrantBody("mv-g1", "{\"unknownRight\":1}"),
                "idem-q1", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("未在模型声明");

        // 超声明最大值：拒绝
        assertThatThrownBy(() -> controller.createGrant(
                modelGrantBody("mv-g1", "{\"channelLimit\":999999}"),
                "idem-q2", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("超过声明最大值");

        // 合法实名席位权益落库；席位由固定对象分配治理，不建立在线扣减余额。
        ResponseEntity<String> created = controller.createGrant(
                modelGrantBody("mv-g1", "{\"channelLimit\":100}"),
                "idem-q3", LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(created.getStatusCodeValue()).isEqualTo(201);
        assertThat(created.getBody()).contains("\"modelVersionId\":\"mv-g1\"")
                .contains("\"channelLimit\":100");
        org.mockito.ArgumentCaptor<GrantRevisionEntity> saved =
                org.mockito.ArgumentCaptor.forClass(GrantRevisionEntity.class);
        org.mockito.Mockito.verify(grants).save(saved.capture());
        assertThat(quotaStore).doesNotContainKey(saved.getValue().getGrantId() + ":named_seats");
    }

    @Test
    void createGrantWithoutModelRejectsStrayRights() {
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        assertThatThrownBy(() -> controller.createGrant(
                modelGrantBody(null, "{\"channelLimit\":1}"),
                "idem-q4", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("modelVersionId");
    }

    @Test
    void createGrantRejectsDraftOrForeignModel() {
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        // 草稿模型
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity draftEntity =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-draft", "mdl-d", "prod-1", 1L, MODEL_SNAPSHOT_WITH_QUOTA);
        when(models.findById("mv-draft")).thenReturn(Optional.of(draftEntity));
        assertThatThrownBy(() -> controller.createGrant(
                modelGrantBody("mv-draft", "{}"),
                "idem-q5", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("已发布");

        // 他产品模型
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity foreign =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-foreign", "mdl-f", "prod-other", 1L, MODEL_SNAPSHOT_WITH_QUOTA);
        when(models.findById("mv-foreign")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> controller.createGrant(
                modelGrantBody("mv-foreign", "{}"),
                "idem-q6", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("不属于该产品");
    }
}
