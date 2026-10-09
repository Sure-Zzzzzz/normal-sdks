package io.github.surezzzzzz.sdk.iam.feign.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.AkskFeignConfiguration;
import io.github.surezzzzzz.sdk.iam.feign.client.IamDepartmentFeignClient;
import io.github.surezzzzzz.sdk.iam.feign.client.IamOpenRoleFeignClient;
import io.github.surezzzzzz.sdk.iam.feign.client.IamUserFeignClient;
import io.github.surezzzzzz.sdk.iam.feign.client.model.IamDepartmentResponse;
import io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRolePageResponse;
import io.github.surezzzzzz.sdk.iam.feign.client.model.IamOpenRoleResponse;
import io.github.surezzzzzz.sdk.iam.feign.client.model.IamUserPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM Feign 契约测试：JDK HttpServer 桩出服务端，经 @EnableFeignClients 真实链路逐族断言
 * 路径/方法/If-Match 头/请求体/双分页解析（形态对齐 aksk feign 底座自身测试与 KMS feign 件）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = IamFeignClientContractTest.ContractTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.client.enable=true",
                "io.github.surezzzzzz.sdk.cache.enabled=false",
                // 排除 redis 令牌自动配置（类为 runtime 传递依赖，编译期不可见，走字符串排除）：
                // 契约测试不依赖 Redis，令牌链以桩 TokenManager 提供
                "spring.autoconfigure.exclude=io.github.surezzzzzz.sdk.auth.aksk.redis."
                        + "tokenmanager.configuration.SimpleAkskRedisTokenManagerAutoConfiguration"},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IamFeignClientContractTest {

    private static HttpStubServer stub;
    @Autowired
    private IamUserFeignClient users;
    @Autowired
    private IamDepartmentFeignClient departments;
    @Autowired
    private IamOpenRoleFeignClient openRoles;

    @DynamicPropertySource
    static void baseUrl(DynamicPropertyRegistry registry) {
        registry.add("io.github.surezzzzzz.sdk.iam.client.base-url",
                () -> "http://127.0.0.1:" + stub.port());
    }

    @BeforeAll
    static void startStub() {
        stub = new HttpStubServer();
        stub.start();
    }

    @AfterAll
    static void stopStub() {
        stub.stop();
    }

    private static String springPageJson() {
        return "{\"content\":[" + userJson() + "],\"totalElements\":1,\"totalPages\":1,\"number\":0,\"size\":20,"
                + "\"first\":true,\"last\":true,\"empty\":false}";
    }

    private static String userJson() {
        return "{\"id\":3,\"subjectId\":\"sid-e2e-1\",\"username\":\"e2e-user\",\"displayName\":\"成员\",\"departmentId\":1,"
                + "\"departmentName\":\"root\",\"status\":1,\"email\":null,\"phone\":\"+8613800000001\","
                + "\"roles\":[\"iam_user\"]}";
    }

    private static String departmentJson() {
        return "{\"id\":1,\"code\":\"ROOT\",\"name\":\"root\",\"parentId\":null,\"fullPath\":\"/root\","
                + "\"sortOrder\":1,\"status\":1}";
    }

    private static String roleJson() {
        return "{\"openRoleId\":\"uuid-1\",\"externalId\":\"ext-1\",\"code\":\"role-1\",\"applicationId\":3,"
                + "\"rootDepartmentId\":1,\"name\":\"角色\",\"description\":\"描述\",\"revision\":4,"
                + "\"state\":\"ACTIVE\",\"rulePresent\":true}";
    }

    private static String ruleJson() {
        return "{\"openRoleId\":\"uuid-1\",\"applicationId\":3,\"revision\":6,\"pagePermissions\":[],"
                + "\"apiPermissions\":[\"kms.key:api\"],\"dataGrantTemplate\":{\"iam:user\":{\"read\":true}}}";
    }

    @org.junit.jupiter.api.BeforeEach
    void resetStub() {
        stub.reset();
    }

    @Test
    void shouldCallUserEndpointsWithSpringPageWire() {
        // feign 的 query 顺序不保证：同时注册带序全串与无序纯路径两形态
        stub.reply("GET", "/iam/api/users?page=0&size=20&keyword=e2e", springPageJson());
        stub.reply("GET", "/iam/api/users", springPageJson());
        IamUserPageResponse page = users.listUsers(null, null, "e2e", 0, 20);
        assertEquals(1, page.content.size(), "Spring Page 条目");
        assertEquals(1L, page.totalElements, "totalElements");
        assertEquals(0, page.number, "页码 0 起");
        assertTrue(stub.lastPath().startsWith("/iam/api/users"), "null 可选参省略+命中 users 路径");
        serverVerify();

        stub.reply("GET", "/iam/api/users/sub-1/roles", "[\"iam_user\"]");
        assertEquals("iam_user", users.getUserRoles("sub-1").get(0), "角色编码裸列表");
        serverVerify();

        stub.reply("GET", "/iam/api/users/sub-1/page-admitted-applications", "[\"iam\",\"kms\"]");
        assertEquals("kms", users.listPageAdmittedApplications("sub-1").get(1), "页面准入应用编码裸列表");
        serverVerify();

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("username", "new-user");
        body.put("password", "P@ssw0rd");
        stub.reply("POST", "/iam/api/users", userJson());
        users.createUser(body);
        assertTrue(stub.lastBody().contains("new-user"), "创建体字段");
        serverVerify();
    }

    @Test
    void shouldCallDepartmentEndpoints() {
        stub.reply("GET", "/iam/api/departments", "[" + departmentJson() + "]");
        List<IamDepartmentResponse> list = departments.listDepartments();
        assertEquals("root", list.get(0).name, "部门列表（数组）");
        serverVerify();

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", "dev");
        body.put("name", "研发");
        stub.reply("POST", "/iam/api/departments", departmentJson());
        departments.createDepartment(body);
        assertTrue(stub.lastBody().contains("dev"), "创建体");
        serverVerify();
    }

    @Test
    void shouldCallOpenRoleEndpointsWithIfMatchAndOwnPagination() {
        stub.reply("POST", "/iam/api/roles", roleJson());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("externalId", "ext-1");
        body.put("applicationId", 3L);
        body.put("rootDepartmentId", 1L);
        body.put("name", "角色");
        IamOpenRoleResponse created = openRoles.createOpenRole(body);
        assertEquals(4L, created.revision, "revision 解析");
        serverVerify();

        stub.reply("GET", "/iam/api/roles?page=1&size=20",
                "{\"items\":[" + roleJson() + "],\"total\":1,\"page\":1,\"size\":20}");
        IamOpenRolePageResponse page = openRoles.listOpenRoles(1, 20);
        assertEquals(1, page.items.size(), "自持分页条目");
        assertEquals(1, page.page, "自持分页页码 1 起");
        serverVerify();

        Map<String, Object> ruleBody = new LinkedHashMap<String, Object>();
        ruleBody.put("manifestVersion", 2L);
        ruleBody.put("manifestDigest", "sha");
        stub.reply("PUT", "/iam/api/roles/uuid-1/authorization-rules/3", ruleJson());
        openRoles.putOpenRoleRule("uuid-1", 3L, "open-role:uuid-1:4", ruleBody);
        assertEquals("open-role:uuid-1:4", stub.lastIfMatch(), "If-Match 头携带");
        serverVerify();

        stub.fail("DELETE", "/iam/api/roles/uuid-1/authorization-rules/3", 428);
        feign.FeignException exception = assertThrows(feign.FeignException.class,
                () -> openRoles.deleteOpenRoleRule("uuid-1", 3L, null),
                "428 缺条件透传");
        assertEquals(428, exception.status(), "状态码保留");
        serverVerify();
    }

    @Test
    void shouldCarryAkskAuthMetadataOnInterfaces() {
        AkskClientFeignClient annotation = IamUserFeignClient.class.getAnnotation(AkskClientFeignClient.class);
        assertNotNull(annotation, "契约接口标注底座元注解");
        assertEquals("iam-users", annotation.name(), "服务名（族内唯一）");
        boolean carries = false;
        for (Class<?> configuration : annotation.configuration()) {
            carries = carries || configuration == AkskFeignConfiguration.class;
        }
        assertTrue(carries, "携带底座 Feign 配置");
        assertEquals("iam-open-roles", IamOpenRoleFeignClient.class.getAnnotation(AkskClientFeignClient.class).name());
        assertEquals("iam-departments", IamDepartmentFeignClient.class.getAnnotation(AkskClientFeignClient.class).name());
    }

    private void serverVerify() {
        assertTrue(stub.lastPath().startsWith("/iam/api"), "基路径固定");
    }

    /**
     * 契约测试启动类：启用 Feign 扫描本模块三接口，桩出底座令牌链。
     */
    @SpringBootApplication
    @EnableFeignClients(basePackageClasses = IamUserFeignClient.class)
    static class ContractTestApplication {

        @Bean
        @org.springframework.context.annotation.Primary
        public TokenManager testTokenManager() {
            return new TokenManager() {
                @Override
                public String getToken() {
                    return "test-token";
                }

                @Override
                public void clearToken() {
                    // 桩实现无缓存可清
                }
            };
        }
    }

    /**
     * JDK HttpServer 最小桩：按方法+路径回放 JSON，记录路径/If-Match/请求体。
     */
    static final class HttpStubServer {
        private final Map<String, String> canned = new HashMap<String, String>();
        private final Map<String, Integer> statuses = new HashMap<String, Integer>();
        private final List<String[]> exchanges = new ArrayList<String[]>();
        private com.sun.net.httpserver.HttpServer server;

        void start() {
            try {
                server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", this::handle);
                server.start();
            } catch (Exception exception) {
                throw new IllegalStateException("stub server start failed", exception);
            }
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        int port() {
            return server.getAddress().getPort();
        }

        void reply(String method, String path, String body) {
            canned.put(method + " " + path, body);
        }

        void fail(String method, String path, int status) {
            statuses.put(method + " " + path, Integer.valueOf(status));
        }

        String lastPath() {
            return exchanges.get(exchanges.size() - 1)[1];
        }

        String lastIfMatch() {
            return exchanges.get(exchanges.size() - 1)[2];
        }

        String lastBody() {
            return exchanges.get(exchanges.size() - 1)[3];
        }

        void reset() {
            exchanges.clear();
            canned.clear();
            statuses.clear();
        }

        private void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            try {
                handle0(exchange);
            } catch (java.io.IOException ioException) {
                throw ioException;
            } catch (Exception exception) {
                throw new java.io.IOException(exception);
            }
        }

        private void handle0(com.sun.net.httpserver.HttpExchange exchange) throws Exception {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream input = exchange.getRequestBody()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, count);
                }
            }
            String query = exchange.getRequestURI().getRawQuery();
            String path = exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query);
            exchanges.add(new String[]{exchange.getRequestMethod(), path,
                    exchange.getRequestHeaders().getFirst("If-Match"),
                    new String(buffer.toByteArray(), StandardCharsets.UTF_8)});
            // 方法+全串优先命中；未命中退化到"方法+不带 query 的路径"（feign 的 query 顺序不保证）
            String key = exchange.getRequestMethod() + " " + path;
            String body = canned.get(key);
            if (body == null) {
                String plainPath = exchange.getRequestURI().getRawPath();
                body = canned.get(exchange.getRequestMethod() + " " + plainPath);
            }
            if (body == null) {
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            Integer cannedStatus = statuses.get(exchange.getRequestMethod() + " " + path);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(cannedStatus == null ? 200 : cannedStatus.intValue(), bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
