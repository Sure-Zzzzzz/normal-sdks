package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.controller.rest.IamOpenRoleRestController;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRolePageResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 正式契约的结构、引用、条件响应及本版 Controller/分页字段一致性。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamOpenRoleContractTest {
    @Test
    void departmentReadDeclaresNoStoreWithoutRoleEtag() throws Exception {
        Map<String, Object> contract = read("simple-iam-open-api.openapi.yaml");
        Map<String, Object> paths = map(contract.get("paths"));
        Map<String, Object> operation = map(map(paths.get("/iam/api/roles/{openRoleId}/departments")).get("get"));
        Map<String, Object> response = map(map(operation.get("responses")).get("200"));
        Map<String, Object> headers = map(response.get("headers"));
        assertFalse(headers.containsKey("ETag"), "角色版本不能作为组织与 DATA 分页响应的条件标记");
        assertEquals(Collections.singletonList("no-store"), map(map(headers.get("Cache-Control")).get("schema")).get("enum"));
    }

    @Test
    void openApiReferencesAndNewEndpointMappingsAreConsistent() throws Exception {
        Map<String, Object> contract = read("simple-iam-open-api.openapi.yaml");
        checkReferences(contract, contract);
        Map<String, Object> paths = map(contract.get("paths"));
        Set<String> declared = new HashSet<>();
        Set<String> implemented = new HashSet<>();
        for (Method method : IamOpenRoleRestController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) continue;
            for (String path : mapping.value()) {
                for (org.springframework.web.bind.annotation.RequestMethod verb : mapping.method()) {
                    String fullPath = "/iam/api" + path;
                    String operationKey = verb.name().toLowerCase(Locale.ROOT);
                    implemented.add(operationKey + " " + fullPath);
                    Map<String, Object> operation = map(map(paths.get(fullPath)).get(operationKey));
                    assertNotNull(operation.get("operationId"));
                    declared.add(operationKey + " " + fullPath);
                    if (verb == org.springframework.web.bind.annotation.RequestMethod.PUT || verb == org.springframework.web.bind.annotation.RequestMethod.DELETE) {
                        Map<String, Object> responses = map(operation.get("responses"));
                        assertTrue(responses.containsKey("412"));
                        assertTrue(responses.containsKey("428"));
                    }
                }
            }
        }
        assertEquals(13, implemented.size());
        assertEquals(implemented, declared);
        Map<String, Object> schemas = map(map(contract.get("components")).get("schemas"));
        Map<String, Object> properties = map(map(schemas.get("OpenRolePage")).get("properties"));
        Map<String, Object> serialized = new ObjectMapper().convertValue(new OpenRolePageResponse<>(Collections.emptyList(), 0L, 1, 20), Map.class);
        assertEquals(properties.keySet(), serialized.keySet(), "分页字段必须符合已定前端契约");
    }

    @Test
    void adminConditionalWritesDeclareTheirRejectionStatuses() throws Exception {
        Map<String, Object> contract = read("simple-iam-admin-web.openapi.yaml");
        checkReferences(contract, contract);
        Map<String, Object> paths = map(contract.get("paths"));
        for (String path : Arrays.asList("/iam/admin/roles/{roleId}",
                "/iam/admin/roles/{roleId}/authorization-rules/{applicationId}",
                "/iam/admin/departments/{departmentId}/roles/{roleId}")) {
            Map<String, Object> item = map(paths.get(path));
            for (String method : Arrays.asList("post", "put", "delete")) {
                if (!item.containsKey(method)) continue;
                Map<String, Object> responses = map(map(item.get(method)).get("responses"));
                assertTrue(responses.containsKey("412"), path + " 必须声明旧版本拒绝");
                assertTrue(responses.containsKey("428"), path + " 必须声明条件缺失拒绝");
            }
        }
    }

    private Map<String, Object> read(String name) throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream source = Files.newInputStream(Paths.get("../contract/openapi", name))) {
            return new Yaml(new SafeConstructor(options)).load(source);
        }
    }

    private void checkReferences(Object value, Map<String, Object> root) {
        if (value instanceof Map) {
            Map<String, Object> object = map(value);
            if (object.containsKey("$ref")) {
                String reference = object.get("$ref").toString();
                assertTrue(reference.startsWith("#/"), "契约只采用本文件引用");
                Object target = root;
                for (String key : reference.substring(2).split("/")) {
                    target = map(target).get(key.replace("~1", "/").replace("~0", "~"));
                    assertNotNull(target, "悬空契约引用：" + reference);
                }
            }
            object.values().forEach(child -> checkReferences(child, root));
        } else if (value instanceof List) {
            ((List<?>) value).forEach(child -> checkReferences(child, root));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        assertTrue(value instanceof Map, "期望结构化对象");
        return (Map<String, Object>) value;
    }
}
