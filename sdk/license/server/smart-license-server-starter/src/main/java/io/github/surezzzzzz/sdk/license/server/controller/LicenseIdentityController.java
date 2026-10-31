package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.IssuerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.IssuerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;

/**
 * 身份与产品族 HTTP 面：/me、/issuers、/customers、/products。
 *
 * <p>语义对齐 DESIGN.HTTP.1.md §4：列表 page 从 1 起、size 1..100、稳定排序 createdAt/id；
 * 创建走 DATA manage 准入（ALLOW_ALL）；修订携带请求体 expectedRowVersion（缺省/失配 409）；
 * 创建要求 Idempotency-Key 请求标识；基础台账依赖业务编码去重，不承诺同键请求重放。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseIdentityController {

    private static final String BASE = "/api/license/v1";

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final IssuerRepository issuers;
    private final CustomerRepository customers;
    private final ProductRepository products;

    public LicenseIdentityController(DefaultLicensePrincipalResolver resolverIgnored,
                                     IssuerRepository issuers, CustomerRepository customers,
                                     ProductRepository products) {
        this.issuers = issuers;
        this.customers = customers;
        this.products = products;
    }

    // ===== me =====

    @RequireApiPermission("license.me.read")
    @GetMapping("/me")
    public ResponseEntity<String> me(HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.ME_READ);
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("principalId", context.getPrincipalId());
        body.put("subjectType", context.getSubjectType());
        ArrayNode apis = body.putArray("apiPermissions");
        context.getApiPermissions().forEach(apis::add);
        ArrayNode pages = body.putArray("pagePermissions");
        context.getPagePermissions().forEach(pages::add);
        return ok(body);
    }

    // ===== issuers =====

    @RequireApiPermission("license.issuer.read")
    @GetMapping("/issuers")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_ISSUER, action = "read")
    public ResponseEntity<String> listIssuers(HttpServletRequest request,
                                              @CurrentDataAccessPlan
                                                      DataAccessPlan plan,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        require(request, LicenseApi.ISSUER_READ);
        LicenseAccessScope scope =
                LicenseAccessScope.from(plan, "issuerId");
        Pageable pageable = pageable(page, size);
        Page<IssuerEntity> result = scope.resolve(
                () -> issuers.findAll(pageable),
                ids -> issuers.findByIssuerIdIn(ids, pageable));
        return pageOf(result, LicenseIdentityController::issuerView);
    }

    @RequireApiPermission("license.issuer.manage")
    @PostMapping("/issuers")
    public ResponseEntity<String> createIssuer(@RequestBody String body,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.ISSUER_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_ISSUER, "manage");
        ObjectNode input = support.object(body, "code", "displayName");
        String code = support.text(input, "code", true);
        if (issuers.findByCode(code).isPresent()) {
            throw LicenseHttpException.conflict("签发方编码已存在");
        }
        IssuerEntity created = issuers.save(new IssuerEntity("iss-" + UUID.randomUUID(), code,
                support.text(input, "displayName", true)));
        return created(issuerView(created), BASE + "/issuers/" + created.getIssuerId());
    }

    @RequireApiPermission("license.issuer.read")
    @GetMapping("/issuers/{issuerId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_ISSUER, action = "read")
    public ResponseEntity<String> getIssuer(@PathVariable String issuerId,
                                            @CurrentDataAccessPlan
                                                    DataAccessPlan plan,
                                            HttpServletRequest request) {
        require(request, LicenseApi.ISSUER_READ);
        LicenseAccessScope.from(plan, "issuerId").requireVisible(issuerId);
        IssuerEntity entity = issuers.findById(issuerId).orElseThrow(LicenseHttpException::notFound);
        return ok(issuerView(entity));
    }

    @RequireApiPermission("license.issuer.manage")
    @PatchMapping("/issuers/{issuerId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_ISSUER, action = "manage")
    public ResponseEntity<String> patchIssuer(@PathVariable String issuerId, @RequestBody String body,
                                              @CurrentDataAccessPlan
                                                      DataAccessPlan plan,
                                              HttpServletRequest request) {
        require(request, LicenseApi.ISSUER_MANAGE);
        LicenseAccessScope.from(plan, "issuerId").requireAllowed(issuerId);
        ObjectNode input = support.object(body, "displayName", "status", "expectedRowVersion");
        IssuerEntity entity = issuers.findById(issuerId).orElseThrow(LicenseHttpException::notFound);
        requireRowVersion(input, entity.getRowVersion());
        if (input.has("displayName")) {
            entity.setDisplayName(support.text(input, "displayName", true));
        }
        Integer status = support.integer(input, "status", false);
        if (status != null) {
            if (status != 0 && status != 1) {
                throw LicenseHttpException.unprocessable("状态仅支持 0/1");
            }
            entity.setStatus(status);
        }
        return ok(issuerView(issuers.save(entity)));
    }

    // ===== customers =====

    @RequireApiPermission("license.customer.read")
    @GetMapping("/customers")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_CUSTOMER, action = "read")
    public ResponseEntity<String> listCustomers(HttpServletRequest request,
                                                @CurrentDataAccessPlan
                                                        DataAccessPlan plan,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        require(request, LicenseApi.CUSTOMER_READ);
        LicenseAccessScope scope =
                LicenseAccessScope.from(plan, "customerId");
        Page<CustomerEntity> result = scope.resolve(
                () -> customers.findAll(pageable(page, size)),
                ids -> customers.findByCustomerIdIn(ids, pageable(page, size)));
        return pageOf(result, LicenseIdentityController::customerView);
    }

    @RequireApiPermission("license.customer.manage")
    @PostMapping("/customers")
    public ResponseEntity<String> createCustomer(@RequestBody String body,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.CUSTOMER_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_CUSTOMER, "manage");
        ObjectNode input = support.object(body, "code", "displayName");
        String code = support.text(input, "code", true);
        if (customers.findByCode(code).isPresent()) {
            throw LicenseHttpException.conflict("客户编码已存在");
        }
        CustomerEntity created = customers.save(new CustomerEntity("cu-" + UUID.randomUUID(), code,
                support.text(input, "displayName", true)));
        return created(customerView(created), BASE + "/customers/" + created.getCustomerId());
    }

    @RequireApiPermission("license.customer.read")
    @GetMapping("/customers/{customerId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_CUSTOMER, action = "read")
    public ResponseEntity<String> getCustomer(@PathVariable String customerId,
                                              @CurrentDataAccessPlan
                                                      DataAccessPlan plan,
                                              HttpServletRequest request) {
        require(request, LicenseApi.CUSTOMER_READ);
        LicenseAccessScope.from(plan, "customerId").requireVisible(customerId);
        CustomerEntity entity = customers.findById(customerId).orElseThrow(LicenseHttpException::notFound);
        return ok(customerView(entity));
    }

    // ===== products =====

    @RequireApiPermission("license.product.read")
    @GetMapping("/products")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> listProducts(HttpServletRequest request,
                                               @CurrentDataAccessPlan
                                                       DataAccessPlan plan,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(value = "issuerId", required = false) String issuerId) {
        require(request, LicenseApi.PRODUCT_READ);
        LicenseAccessScope scope =
                LicenseAccessScope.from(plan, "productId");
        Pageable pageable = pageable(page, size);
        Page<ProductEntity> result = scope.resolve(
                () -> issuerId == null || issuerId.isEmpty()
                        ? products.findAll(pageable)
                        : products.findByIssuerIdOrderByCreatedAtAsc(pageable, issuerId),
                ids -> products.findByProductIdIn(ids, pageable));
        return pageOf(result, LicenseIdentityController::productView);
    }

    @RequireApiPermission("license.product.manage")
    @PostMapping("/products")
    public ResponseEntity<String> createProduct(@RequestBody String body,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.PRODUCT_MANAGE);
        // 新产品可使用 issuerId 范围（设计 §4）；当前网关角色为 ALLOW_ALL 准入
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "manage");
        ObjectNode input = support.object(body, "issuerId", "code", "displayName",
                "declaredActions", "supportedCapabilities");
        String issuerId = support.text(input, "issuerId", true);
        issuers.findById(issuerId).orElseThrow(LicenseHttpException::notFound);
        String code = support.text(input, "code", true);
        if (products.findByIssuerIdAndCode(issuerId, code).isPresent()) {
            throw LicenseHttpException.conflict(LicenseValidationConstant.PRODUCT_CODE_CONFLICT);
        }
        ProductEntity created;
        try {
            // 先查只用于明确提示；数据库组合唯一键承担并发创建的最终屏障。
            created = products.saveAndFlush(new ProductEntity("prod-" + UUID.randomUUID(), issuerId,
                    code, support.text(input, "displayName", true),
                    actionsJson(input, "declaredActions"), actionsJson(input, "supportedCapabilities")));
        } catch (DataIntegrityViolationException race) {
            // 保存事务已结束后再回读；其他完整性故障不冒充重复产品编码。
            if (products.findByIssuerIdAndCode(issuerId, code).isPresent()) {
                throw LicenseHttpException.conflict(LicenseValidationConstant.PRODUCT_CODE_CONFLICT);
            }
            throw race;
        }
        return created(productView(created), BASE + "/products/" + created.getProductId());
    }

    @RequireApiPermission("license.product.read")
    @GetMapping("/products/{productId}")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> getProduct(@PathVariable String productId,
                                             @CurrentDataAccessPlan
                                                     DataAccessPlan plan,
                                             HttpServletRequest request) {
        require(request, LicenseApi.PRODUCT_READ);
        LicenseAccessScope.from(plan, "productId").requireVisible(productId);
        ProductEntity entity = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        return ok(productView(entity));
    }

    /** 产品能力清单只允许追加，避免已发布模型和历史凭据失去原有能力声明。 */
    @RequireApiPermission("license.product.manage")
    @PatchMapping("/products/{productId}")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_PRODUCT, action = "manage")
    public ResponseEntity<String> patchProduct(@PathVariable String productId,
                                               @RequestBody String body,
                                               @CurrentDataAccessPlan
                                                       DataAccessPlan plan,
                                               HttpServletRequest request) {
        require(request, LicenseApi.PRODUCT_MANAGE);
        LicenseAccessScope.from(plan, "productId").requireAllowed(productId);
        ProductEntity entity = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        ObjectNode input = support.object(body, "supportedCapabilities", "expectedRowVersion");
        requireRowVersion(input, entity.getRowVersion());
        entity.setSupportedCapabilities(extendedCapabilitiesJson(input, entity.getSupportedCapabilities()));
        return ok(productView(products.save(entity)));
    }

    /** 产品门户应用绑定（JSON 数组存产品行；expectedRowVersion=产品行版本围栏）。 */
    @RequireApiPermission("license.product.read")
    @GetMapping("/products/{productId}/application-bindings")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> getApplicationBindings(@PathVariable String productId,
            @CurrentDataAccessPlan DataAccessPlan plan,
            HttpServletRequest request) {
        require(request, LicenseApi.PRODUCT_READ);
        LicenseAccessScope.from(plan, "productId").requireVisible(productId);
        ProductEntity product = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("rowVersion", product.getRowVersion());
        try {
            body.set("items", product.getApplicationBindings() == null
                    ? StrictJson.mapper().createArrayNode()
                    : (com.fasterxml.jackson.databind.node.ArrayNode) StrictJson.mapper().readTree(product.getApplicationBindings()));
        } catch (java.io.IOException parseFailure) {
            body.putArray("items");
        }
        return ok(body);
    }

    /** 整体替换应用绑定（PUT 全量；产品行版本围栏防并发覆盖）。 */
    @RequireApiPermission("license.product.manage")
    @PutMapping("/products/{productId}/application-bindings")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_PRODUCT, action = "manage")
    public ResponseEntity<String> putApplicationBindings(@PathVariable String productId, @RequestBody String bodyJson,
            @CurrentDataAccessPlan DataAccessPlan plan,
            HttpServletRequest request) {
        require(request, LicenseApi.PRODUCT_MANAGE);
        LicenseAccessScope.from(plan, "productId").requireAllowed(productId);
        ProductEntity product = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        ObjectNode input = support.object(bodyJson, "items", "expectedRowVersion");
        requireRowVersion(input, product.getRowVersion());
        com.fasterxml.jackson.databind.JsonNode items = input.get("items");
        if (items == null || !items.isArray()) {
            throw LicenseHttpException.unprocessable("items 必须为数组");
        }
        for (com.fasterxml.jackson.databind.JsonNode item : items) {
            if (!item.path("applicationId").canConvertToLong()
                    || !item.path("basePermissionSnapshotVersion").canConvertToLong()) {
                throw LicenseHttpException.unprocessable("绑定项须含 applicationId 与 basePermissionSnapshotVersion（数值）");
            }
        }
        product.setApplicationBindings(items.toString());
        ProductEntity saved = products.save(product);
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("rowVersion", saved.getRowVersion());
        try {
            body.set("items", StrictJson.mapper().readTree(saved.getApplicationBindings() == null ? "[]" : saved.getApplicationBindings()));
        } catch (java.io.IOException parseFailure) {
            body.putArray("items");
        }
        return ok(body);
    }

    // ===== helpers =====

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    private static Pageable pageable(int page, int size) {
        if (page < 1) {
            throw LicenseHttpException.badRequest("page 从 1 起");
        }
        if (size < 1 || size > 100) {
            throw LicenseHttpException.badRequest("size 取值 1..100");
        }
        return PageRequest.of(page - 1, size);
    }

    private static void requireRowVersion(ObjectNode input, long current) {
        if (!input.has("expectedRowVersion")
                || !input.get("expectedRowVersion").isIntegralNumber()
                || !input.get("expectedRowVersion").canConvertToLong()) {
            throw LicenseHttpException.badRequest("修订必须携带 expectedRowVersion");
        }
        if (input.get("expectedRowVersion").asLong() != current) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
    }

    private String actionsJson(ObjectNode input, String field) {
        com.fasterxml.jackson.databind.JsonNode value = input.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw LicenseHttpException.unprocessable("字段必须为非空数组：" + field);
        }
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (com.fasterxml.jackson.databind.JsonNode item : value) {
            String text = item.asText();
            if (!item.isTextual() || text.isEmpty() || text.length() > 128
                    || !isControlledAscii(text) || !values.add(text)) {
                throw LicenseHttpException.unprocessable("数组元素含非法、重复或超长文本：" + field);
            }
        }
        return StrictJson.mapper().valueToTree(values).toString();
    }

    private String extendedCapabilitiesJson(ObjectNode input, String existingJson) {
        com.fasterxml.jackson.databind.JsonNode value = input.get("supportedCapabilities");
        if (value == null || !value.isArray()) {
            throw LicenseHttpException.unprocessable("字段必须为数组：supportedCapabilities");
        }
        java.util.LinkedHashSet<String> requested = new java.util.LinkedHashSet<>();
        for (com.fasterxml.jackson.databind.JsonNode item : value) {
            if (!item.isTextual() || item.asText().isEmpty() || item.asText().length() > 128
                    || !isControlledAscii(item.asText()) || !requested.add(item.asText())) {
                throw LicenseHttpException.unprocessable("能力清单含非法或重复项");
            }
        }
        for (String existing : parseArray(existingJson)) {
            if (!requested.contains(existing)) {
                throw LicenseHttpException.unprocessable("能力清单只能扩展，不能移除已有能力");
            }
        }
        return StrictJson.mapper().valueToTree(requested).toString();
    }

    private static boolean isControlledAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x21 || ch > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private static ObjectNode issuerView(IssuerEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("issuerId", entity.getIssuerId());
        view.put("code", entity.getCode());
        view.put("displayName", entity.getDisplayName());
        view.put("status", entity.getStatus() == 1 ? "ACTIVE" : "SUSPENDED");
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    private static ObjectNode customerView(CustomerEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("customerId", entity.getCustomerId());
        view.put("code", entity.getCode());
        view.put("displayName", entity.getDisplayName());
        view.put("status", entity.getStatus() == 1 ? "ACTIVE" : "SUSPENDED");
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    private static ObjectNode productView(ProductEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("productId", entity.getProductId());
        view.put("issuerId", entity.getIssuerId());
        view.put("code", entity.getCode());
        view.put("displayName", entity.getDisplayName());
        view.set("declaredActions", StrictJson.mapper().valueToTree(
                parseArray(entity.getDeclaredActions())));
        view.set("supportedCapabilities", StrictJson.mapper().valueToTree(
                parseArray(entity.getSupportedCapabilities())));
        view.put("status", entity.getStatus() == 1 ? "ACTIVE" : "SUSPENDED");
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    private static java.util.List<String> parseArray(String json) {
        try {
            java.util.List<String> values = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : StrictJson.mapper().readTree(json)) {
                values.add(item.asText());
            }
            return values;
        } catch (Exception failure) {
            throw LicenseHttpException.dependencyUnavailable("服务暂不可用");
        }
    }

    private static <T> ResponseEntity<String> pageOf(Page<T> page,
            java.util.function.Function<T, ObjectNode> viewer) {
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        page.getContent().forEach(item -> data.add(viewer.apply(item)));
        body.put("total", page.getTotalElements());
        body.put("page", page.getNumber() + 1);
        body.put("size", page.getSize());
        body.put("totalPages", page.getTotalPages());
        return ResponseEntity.ok(body.toString());
    }

    private static ResponseEntity<String> ok(ObjectNode body) {
        return ResponseEntity.ok(body.toString());
    }

    private static ResponseEntity<String> created(ObjectNode body, String location) {
        return ResponseEntity.status(201).header("Location", location).body(body.toString());
    }

    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
}
