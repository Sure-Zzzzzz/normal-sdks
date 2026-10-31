package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseIdentityController;
import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.IssuerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.IssuerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 身份族 HTTP 语义断言：分页围栏、未知字段 422、重复编码 409、修订版本围栏、DATA 失败关闭。
 * 认证/权限经 Web 层测试（MockMvc）另行覆盖；此处聚焦业务语义。
 *
 * @author surezzzzzz
 */
class LicenseIdentityHttpTest {

    private IssuerRepository issuers;
    private CustomerRepository customers;
    private ProductRepository products;
    private LicenseIdentityController controller;

    @BeforeEach
    void setUp() {
        issuers = Mockito.mock(IssuerRepository.class);
        customers = Mockito.mock(CustomerRepository.class);
        products = Mockito.mock(ProductRepository.class);
        controller = new LicenseIdentityController(null, issuers, customers, products);
    }

    @Test
    void listRejectsPageBelowOne() {
        assertThatThrownBy(() -> controller.listIssuers(TestRequests.authenticated(), TestPlans.allFor("license-issuer", "read"), 0, 20))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(400);
    }

    @Test
    void listRejectsSizeOverHundred() {
        assertThatThrownBy(() -> controller.listIssuers(TestRequests.authenticated(), TestPlans.allFor("license-issuer", "read"), 1, 101))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(400);
    }

    @Test
    void listWithoutDataGrantFailsClosed() {
        // 无 DATA 投影的主体：即使持有 API 码也拒绝（资源级授权缺失不降级放行）
        assertThatThrownBy(() -> controller.listIssuers(TestRequests.noDataGrant(), TestPlans.deny(), 1, 20))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(403);
    }

    @Test
    void detailReadsOutsideCurrentDataScopeAreHiddenAsNotFound() {
        assertNotFound(() -> controller.getIssuer("issuer-hidden",
                TestPlans.restrictedFor("license-issuer", "read", "issuerId", "issuer-visible"),
                TestRequests.authenticated()));
        assertNotFound(() -> controller.getCustomer("customer-hidden",
                TestPlans.restrictedFor("license-customer", "read", "customerId", "customer-visible"),
                TestRequests.authenticated()));
        assertNotFound(() -> controller.getProduct("product-hidden",
                TestPlans.restrictedFor("license-product", "read", "productId", "product-visible"),
                TestRequests.authenticated()));
        assertNotFound(() -> controller.getApplicationBindings("product-hidden",
                TestPlans.restrictedFor("license-product", "read", "productId", "product-visible"),
                TestRequests.authenticated()));
        Mockito.verifyNoInteractions(issuers, customers, products);
    }

    @Test
    void createIssuerRejectsUnknownField() {
        assertThatThrownBy(() -> controller.createIssuer(
                "{\"code\":\"huaxun\",\"displayName\":\"华讯\",\"extra\":1}", "idem-1",
                TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void createIssuerRejectsDuplicateCode() {
        when(issuers.findByCode("huaxun")).thenReturn(Optional.of(
                new IssuerEntity("iss-1", "huaxun", "华讯")));
        assertThatThrownBy(() -> controller.createIssuer(
                "{\"code\":\"huaxun\",\"displayName\":\"华讯2\"}", "idem-2",
                TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(409);
    }

    @Test
    void patchIssuerRejectsStaleRowVersion() {
        IssuerEntity existing = new IssuerEntity("iss-1", "huaxun", "华讯");
        when(issuers.findById("iss-1")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> controller.patchIssuer("iss-1",
                "{\"displayName\":\"华讯新名\",\"expectedRowVersion\":99}",
                TestPlans.allFor("license-issuer", "manage"),
                TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(409);
    }

    @Test
    void createProductRequiresExistingIssuer() {
        when(issuers.findById("iss-none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.createProduct(
                "{\"issuerId\":\"iss-none\",\"code\":\"crm\",\"displayName\":\"CRM\","
                        + "\"declaredActions\":[\"a.read\"],\"supportedCapabilities\":[\"license-condition/1\"]}",
                "idem-3", TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void createProductRejectsDuplicateDeclaredActions() {
        when(issuers.findById("iss-example")).thenReturn(Optional.of(
                new IssuerEntity("iss-example", "example", "示例签发方")));
        assertThatThrownBy(() -> controller.createProduct(
                "{\"issuerId\":\"iss-example\",\"code\":\"example-product\",\"displayName\":\"示例产品\","
                        + "\"declaredActions\":[\"example.read\",\"example.read\"],"
                        + "\"supportedCapabilities\":[\"license-condition/1\"]}",
                "idem-product-actions", TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void createProductRejectsNonAsciiCapability() {
        when(issuers.findById("iss-example")).thenReturn(Optional.of(
                new IssuerEntity("iss-example", "example", "示例签发方")));
        assertThatThrownBy(() -> controller.createProduct(
                "{\"issuerId\":\"iss-example\",\"code\":\"example-product\",\"displayName\":\"示例产品\","
                        + "\"declaredActions\":[\"example.read\"],\"supportedCapabilities\":[\"条件/1\"]}",
                "idem-product-capability", TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(422);
    }

    @Test
    void concurrentProductCodeWinnerIsReportedAsConflict() {
        when(issuers.findById("iss-example")).thenReturn(Optional.of(
                new IssuerEntity("iss-example", "example", "示例签发方")));
        when(products.findByIssuerIdAndCode("iss-example", "example-product"))
                .thenReturn(Optional.empty(), Optional.of(new ProductEntity(
                        "prod-winner", "iss-example", "example-product", "示例产品", "[]", "[]")));
        when(products.saveAndFlush(any(ProductEntity.class)))
                .thenThrow(new DataIntegrityViolationException("隔离唯一键竞争"));
        assertThatThrownBy(() -> controller.createProduct(
                "{\"issuerId\":\"iss-example\",\"code\":\"example-product\",\"displayName\":\"示例产品\","
                        + "\"declaredActions\":[\"example.read\"],\"supportedCapabilities\":[\"license-condition/1\"]}",
                "idem-product", TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(409);
    }

    @Test
    void patchProductCapabilitiesOnlyExtendsExistingDeclaration() {
        ProductEntity existing = new ProductEntity("prod-1", "iss-example", "example-product",
                "示例产品", "[\"example.read\"]", "[\"license-condition/1\"]");
        when(products.findById("prod-1")).thenReturn(Optional.of(existing));
        when(products.save(any(ProductEntity.class))).thenReturn(existing);
        ResponseEntity<String> response = controller.patchProduct("prod-1",
                "{\"supportedCapabilities\":[\"license-condition/1\",\"subject-binding/1\"],\"expectedRowVersion\":0}",
                TestPlans.allFor("license-product", "manage"), TestRequests.authenticated());
        assertThat(response.getBody()).contains("subject-binding/1").contains("license-condition/1");
    }

    @Test
    void patchProductCapabilitiesRejectsRemoval() {
        ProductEntity existing = new ProductEntity("prod-1", "iss-example", "example-product",
                "示例产品", "[\"example.read\"]", "[\"license-condition/1\"]");
        when(products.findById("prod-1")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> controller.patchProduct("prod-1",
                "{\"supportedCapabilities\":[],\"expectedRowVersion\":0}",
                TestPlans.allFor("license-product", "manage"), TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(422);
    }

    @Test
    void unrelatedProductIntegrityFailureIsNotDisguisedAsDuplicateCode() {
        when(issuers.findById("iss-example")).thenReturn(Optional.of(
                new IssuerEntity("iss-example", "example", "示例签发方")));
        when(products.findByIssuerIdAndCode("iss-example", "example-product"))
                .thenReturn(Optional.empty());
        DataIntegrityViolationException failure = new DataIntegrityViolationException("隔离非编码故障");
        when(products.saveAndFlush(any(ProductEntity.class))).thenThrow(failure);
        assertThatThrownBy(() -> controller.createProduct(
                "{\"issuerId\":\"iss-example\",\"code\":\"example-product\",\"displayName\":\"示例产品\","
                        + "\"declaredActions\":[\"example.read\"],\"supportedCapabilities\":[\"license-condition/1\"]}",
                "idem-product", TestRequests.authenticated())).isSameAs(failure);
    }

    @Test
    void pageViewShapeMatchesContract() {
        when(issuers.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(
                java.util.Collections.singletonList(new IssuerEntity("iss-1", "huaxun", "华讯")),
                PageRequest.of(0, 20), 1));
        ResponseEntity<String> response = controller.listIssuers(TestRequests.authenticated(), TestPlans.allFor("license-issuer", "read"), 1, 20);
        assertThat(response.getBody()).contains("\"issuerId\":\"iss-1\"")
                .contains("\"total\":1").contains("\"page\":1").contains("\"totalPages\":1");
    }

    @Test
    void meReturnsPrincipalAndApiPermissions() {
        ResponseEntity<String> response = controller.me(TestRequests.authenticated());
        assertThat(response.getBody()).contains("\"principalId\":\"iam:zhangshuo\"")
                .contains("license.issuer.read").contains("\"pagePermissions\":[\"license.page.issuers\"]");
    }

    // ===== 测试支撑 =====

    private static void assertNotFound(Runnable request) {
        assertThatThrownBy(request::run).isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(404);
    }

    /** 构造已认证请求（SecurityContext 注入公共资源链结论，resolver 同生产路径）。 */
    static final class TestRequests {

        static javax.servlet.http.HttpServletRequest authenticated() {
            return withSubject(true);
        }

        static javax.servlet.http.HttpServletRequest noDataGrant() {
            return withSubject(false);
        }

        private static javax.servlet.http.HttpServletRequest withSubject(boolean withData) {
            javax.servlet.http.HttpServletRequest request =
                    new org.springframework.mock.web.MockHttpServletRequest();
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(
                            VerifiedContextFactory.create(withData), null));
            return request;
        }
    }
}
