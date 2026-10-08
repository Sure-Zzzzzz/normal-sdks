package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.SmartRedisLimiterPolicyPortalController;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.SmartRedisLimiterTypedPolicyPortalController;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 从受管契约解析实际端点和引用，不以文档存在替代一致性断言。
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterManagementTestApplication.class)
public class SmartRedisLimiterManagementOpenApiTest {
    /**
     * 注解形态的 DATA 动作必须与契约 x-data-action 逐端点一致，capabilities 无数据动作。
     */
    private static void assertDataActionConsistent(Method method, JsonNode operation) {
        io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation data =
                method.getAnnotation(io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation.class);
        if (data == null) {
            assertTrue(operation.path("x-data-action").isMissingNode(),
                    "无 DATA 注解的端点契约不得声明数据动作");
            return;
        }
        assertEquals(data.action(), operation.path("x-data-action").asText(),
                "DATA 动作注解与契约必须一致");
    }

    @Test
    void testPortalPathsMethodsAndPermissionsMatchOpenApi() throws Exception {
        JsonNode document = document();
        int endpoints = 0;
        Set<String> identities = new HashSet<>();
        for (Method method : SmartRedisLimiterPolicyPortalController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            RequireApiPermission permission = method.getAnnotation(RequireApiPermission.class);
            assertNotNull(permission);
            String path = mapping.path()[0];
            String verb = mapping.method()[0].name().toLowerCase(Locale.ROOT);
            log.info("验收 Portal 契约端点 method={}, path={}", verb, path);
            JsonNode operation = document.path("paths").path(path).path(verb);
            assertFalse(operation.isMissingNode());
            assertEquals(permission.value(), operation.path("x-api-permission").asText());
            assertDataActionConsistent(method, operation);
            assertTrue(operation.path("responses").has("401"));
            assertTrue(operation.path("responses").has("403"));
            assertTrue(identities.add(operation.path("operationId").asText()));
            endpoints++;
        }
        assertEquals(8, endpoints);
        assertEquals("2.0.0", document.path("info").path("version").asText());
    }

    @Test
    void testTypedPathsMethodsAndPermissionsMatchOpenApi() throws Exception {
        JsonNode document = document();
        int endpoints = 0;
        Set<String> identities = new HashSet<>();
        for (Method method : SmartRedisLimiterTypedPolicyPortalController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            RequireApiPermission permission = method.getAnnotation(RequireApiPermission.class);
            assertNotNull(permission);
            String path = mapping.path()[0];
            String verb = mapping.method()[0].name().toLowerCase(Locale.ROOT);
            log.info("验收类型化契约端点 method={}, path={}", verb, path);
            JsonNode operation = document.path("paths").path(path).path(verb);
            assertFalse(operation.isMissingNode());
            assertEquals(permission.value(), operation.path("x-api-permission").asText());
            assertDataActionConsistent(method, operation);
            assertTrue(operation.path("responses").has("401"));
            assertTrue(operation.path("responses").has("403"));
            assertTrue(identities.add(operation.path("operationId").asText()));
            endpoints++;
        }
        assertEquals(11, endpoints, "类型化契约必须覆盖目录三接口、规则 CRUD 与快照");
    }

    @Test
    void testOpenApiReferencesAndDomainLimitsAreConsistent() throws Exception {
        JsonNode document = document();
        log.info("验收 OpenAPI 引用、窗口与服务字段边界");
        validateReferences(document, document);
        JsonNode schemas = document.path("components").path("schemas");
        assertEquals(SmartRedisLimiterConstant.MAX_SERVICE_CODE_LENGTH, schemas.path("StableCode").path("maxLength").asInt());
        assertEquals(SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY, schemas.path("Limits").path("maxItems").asInt());
        JsonNode units = schemas.path("Limit").path("properties").path("unit").path("enum");
        assertEquals(SmartRedisLimiterTimeUnit.values().length, units.size());
        for (int i = 0; i < units.size(); i++) {
            assertEquals(SmartRedisLimiterTimeUnit.values()[i].getCode(), units.get(i).asText());
        }
        assertTrue(schemas.path("UpdatePolicy").path("properties").has("expectedRowVersion"));
        assertFalse(schemas.path("UpdatePolicy").path("properties").has("key"));
        assertEquals("1", schemas.path("Snapshot").path("properties").path("schemaVersion").path("enum").get(0).asText());
    }

    private JsonNode document() throws Exception {
        Path path = Paths.get("docs/openapi.yaml");
        if (!Files.exists(path)) {
            path = Paths.get("sdk/limiter/redis/smart-redis-limiter-management-starter/docs/openapi.yaml");
        }
        try (InputStream input = Files.newInputStream(path)) {
            return new ObjectMapper().valueToTree(new Yaml().load(input));
        }
    }

    private void validateReferences(JsonNode document, JsonNode value) {
        if (value.has("$ref")) {
            String reference = value.path("$ref").asText();
            assertTrue(reference.startsWith("#/"), reference);
            assertFalse(document.at(reference.substring(1)).isMissingNode(), reference);
        }
        if (value.isContainerNode()) {
            value.elements().forEachRemaining(child -> validateReferences(document, child));
        }
    }
}
