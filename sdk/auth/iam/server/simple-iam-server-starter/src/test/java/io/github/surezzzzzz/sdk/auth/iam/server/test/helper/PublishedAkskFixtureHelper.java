package io.github.surezzzzzz.sdk.auth.iam.server.test.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.Getter;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Writer;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/**
 * 独立进程消费已发布 AKSK；仅拥有新建数据库、临时配置和进程，不接触已有服务。
 *
 * @author surezzzzzz
 */
public final class PublishedAkskFixtureHelper implements AutoCloseable {
    private static final String PREFIX = "io.github.surezzzzzz.sdk.";
    // 与发布版服务端读取端逐字对齐的 settings 行内容；Duration 为数组形态，见 insertClient 注释。
    private static final String SERVER_CLIENT_SETTINGS_JSON =
            "{\"@class\":\"java.util.Collections$UnmodifiableMap\","
                    + "\"settings.client.require-proof-key\":false,"
                    + "\"settings.client.require-authorization-consent\":false}";
    private static final String SERVER_TOKEN_SETTINGS_JSON =
            "{\"@class\":\"java.util.Collections$UnmodifiableMap\","
                    + "\"settings.token.reuse-refresh-tokens\":true,"
                    + "\"settings.token.access-token-time-to-live\":[\"java.time.Duration\",300.000000000],"
                    + "\"settings.token.id-token-time-to-live\":[\"java.time.Duration\",1800.000000000],"
                    + "\"settings.token.authorization-code-time-to-live\":[\"java.time.Duration\",300.000000000],"
                    + "\"settings.token.device-code-time-to-live\":[\"java.time.Duration\",300.000000000],"
                    + "\"settings.token.refresh-token-time-to-live\":[\"java.time.Duration\",3600.000000000]}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();
    private final String database = "iam_akp_fixture_" + UUID.randomUUID().toString().replace("-", "");
    private final String subject = "iam-akp-" + UUID.randomUUID();
    /**
     * 正式仓库要求 client secret 全库唯一，每个客户端持有独立随机口令。
     */
    private final Map<String, String> clientSecrets = new LinkedHashMap<>();
    @Getter
    private final String introspectionClient = "iam-introspection-" + UUID.randomUUID();
    private DriverManagerDataSource administration;
    private JdbcTemplate jdbc;
    private Process process;
    private Path directory;
    @Getter
    private String endpoint;

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 客户端口令经 Basic 头进入服务端后会被按 form 编码规则二次解码，
     * 标准 Base64 的 {@code +} 会被替换为空格导致比对失败，必须只用 URL 安全字母。
     */
    private static String urlSafeClientSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty())
            throw new IllegalStateException("缺少外部测试资产路径配置：" + name);
        return value;
    }

    /**
     * 本地配置由 Boot 正常加载，认证材料不进入命令行、模型输出或提交内容。
     */
    public void start() {
        if (process != null && process.isAlive()) {
            // Spring 测试上下文重载会再次调用动态属性方法；进程已在即视为就绪，避免重建随机库。
            return;
        }
        try {
            Path artifact = Paths.get(requiredProperty("iam.akp.fixture.jar")).toAbsolutePath();
            try (JarFile jar = new JarFile(artifact.toFile())) {
                if (jar.getJarEntry("BOOT-INF/lib/simple-aksk-server-starter-3.2.2.jar") == null) {
                    throw new IllegalStateException("外部 fixture 必须消费已发布 AKSK 3.2.2。");
                }
            }
            StandardEnvironment environment = new StandardEnvironment();
            ConfigDataEnvironmentPostProcessor.applyTo(environment);
            String url = environment.getRequiredProperty(PREFIX + "mysql.route.datasources.default.url");
            String user = environment.getRequiredProperty(PREFIX + "mysql.route.datasources.default.username");
            String secret = environment.getRequiredProperty(PREFIX + "mysql.route.datasources.default.password");
            int slash = url.indexOf('/', "jdbc:mysql://".length());
            int query = url.indexOf('?', slash);
            if (slash < 0 || query < 0) throw new IllegalStateException("测试数据源地址必须含数据库及连接参数。");
            String serverUrl = url.substring(0, slash + 1) + url.substring(query);
            String fixtureUrl = url.substring(0, slash + 1) + database + url.substring(query);
            administration = new DriverManagerDataSource(serverUrl, user, secret);
            try (Connection connection = administration.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource(fixtureUrl, user, secret);
            jdbc = new JdbcTemplate(dataSource);
            // 全量 schema 仅作用于刚创建的新空库；后续启动、第二实例不再执行。
            new ResourceDatabasePopulator(new FileSystemResource(requiredProperty("iam.akp.fixture.schema"))).execute(dataSource);
            for (String id : Arrays.asList(subject, introspectionClient)) {
                insertClient(id, urlSafeClientSecret(), "示例集成客户端");
            }
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            endpoint = "http://127.0.0.1:" + port;
            directory = Files.createTempDirectory("iam-published-aksk-");
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("spring.profiles.active", "local");
            config.put("server.address", "127.0.0.1");
            config.put("server.port", port);
            config.put("spring.jpa.hibernate.ddl-auto", "validate");
            config.put(PREFIX + "mysql.route.enable", true);
            config.put(PREFIX + "mysql.route.primary-datasource", "default");
            config.put(PREFIX + "mysql.route.datasources.default.url", fixtureUrl);
            config.put(PREFIX + "mysql.route.datasources.default.username", user);
            config.put(PREFIX + "mysql.route.datasources.default.driver-class-name", "com.mysql.cj.jdbc.Driver");
            config.put(PREFIX + "redis.route.enable", true);
            config.put(PREFIX + "redis.route.sources.default.host", environment.getRequiredProperty(PREFIX + "redis.route.sources.default.host"));
            config.put(PREFIX + "redis.route.sources.default.port", environment.getRequiredProperty(PREFIX + "redis.route.sources.default.port"));
            config.put(PREFIX + "redis.route.sources.default.database", environment.getRequiredProperty(PREFIX + "redis.route.sources.default.database"));
            config.put(PREFIX + "auth.aksk.server.jwt.key-id", "iam-fixture-" + UUID.randomUUID());
            config.put(PREFIX + "auth.aksk.server.jwt.public-key", "classpath:keys/public.pem");
            config.put(PREFIX + "auth.aksk.server.jwt.private-key", "classpath:keys/private.pem");
            config.put(PREFIX + "auth.aksk.server.admin.enabled", false);
            config.put(PREFIX + "auth.aksk.server.cleanup.enable", false);
            config.put(PREFIX + "auth.aksk.server.redis.token.me", database);
            config.put(PREFIX + "cache.me", database);
            config.put(PREFIX + "cache.key-prefix", "iam-akp-fixture");
            config.put(PREFIX + "cache.consistency.mode", "strong");
            config.put(PREFIX + "cache.pubsub.mode", "routed");
            config.put(PREFIX + "limiter.redis.smart.enable", true);
            config.put(PREFIX + "limiter.redis.smart.me", database);
            config.put(PREFIX + "limiter.redis.smart.mode", "annotation");
            config.put(PREFIX + "auth.iam.resource.server.enabled", false);
            config.put("logging.level.root", "WARN");
            writeYaml(directory.resolve("application.yml"), config);
            Map<String, Object> sensitive = new LinkedHashMap<>();
            sensitive.put(PREFIX + "mysql.route.datasources.default.password", secret);
            sensitive.put(PREFIX + "auth.aksk.server.jwt.encryption-key", randomSecret());
            String redisPassword = environment.getProperty(PREFIX + "redis.route.sources.default.password");
            if (redisPassword != null) sensitive.put(PREFIX + "redis.route.sources.default.password", redisPassword);
            writeYaml(directory.resolve("application-local.yml"), sensitive);
            String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            process = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-jar", artifact.toString(),
                    "--spring.config.location=" + directory.toUri())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("runtime.log").toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
            String lastObservation = "尚未发起探测";
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) throw new IllegalStateException("正式 AKSK fixture 启动失败；日志仅留本地。");
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBasicAuth(introspectionClient, clientSecrets.get(introspectionClient));
                    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                    body.add("token", "fixture-readiness-invalid");
                    ResponseEntity<String> probed = http.postForEntity(endpoint + "/oauth2/introspect",
                            new HttpEntity<>(body, headers), String.class);
                    // 只记录状态码与原因短语，响应正文可能含内省详情，不进入诊断输出。
                    lastObservation = "HTTP " + probed.getStatusCodeValue();
                    if (probed.getStatusCodeValue() == 200) return;
                } catch (org.springframework.web.client.RestClientException notReady) {
                    lastObservation = notReady.getClass().getSimpleName();
                }
                Thread.sleep(100);
            }
            throw new IllegalStateException("正式 AKSK fixture 未在期限内就绪，最后探测结果：" + lastObservation + "。");
        } catch (Exception failure) {
            close();
            // 保留异常链定位真实失败点；认证材料只存在于受控配置对象，不会进入异常栈。
            throw new IllegalStateException("正式 AKP 测试环境初始化失败，认证材料不输出。", failure);
        }
    }

    /**
     * 内省凭据仅在测试 JVM 中交给正式 Provider。
     */
    public String introspectionSecret() {
        return clientSecrets.get(introspectionClient);
    }

    /**
     * 返回真实服务主体标识，用于验证角色所有权。
     */
    public String subjectId() {
        return subject;
    }

    /**
     * 替换测试拥有的权威授权投影；旧令牌能否感知变更由真实 Server 决定。
     */
    public void authorize(boolean admitted, List<String> apis, DataGrantDocument data) throws IOException {
        authorizeClient(subject, admitted, apis, data);
    }

    private void authorizeClient(String id, boolean admitted, List<String> apis, DataGrantDocument data) throws IOException {
        jdbc.update("INSERT INTO aksk_application_authorization "
                        + "(client_id,application_code,admitted,roles_json,page_permissions_json,api_permissions_json,data_grant_document_json,"
                        + "authorization_version,manifest_version,manifest_digest,enabled,created_at,updated_at) "
                        + "VALUES (?,'iam',?,'[]','[]',?,?,1,'fixture','fixture',1,NOW(),NOW())",
                id, admitted, mapper.writeValueAsString(apis), data == null ? null : mapper.writeValueAsString(data));
    }

    /**
     * 通过发布版 OAuth2 端点取得真实令牌，不使用测试 AuthenticationAdapter。
     */
    public String token() throws IOException {
        return tokenFor(subject);
    }

    /**
     * 独立客户端携带受限授权，避免改投影绕过发布版的变更与撤销流程。
     */
    public String restrictedToken(List<String> apis, DataGrantDocument data) throws IOException {
        String id = "iam-restricted-" + UUID.randomUUID();
        insertClient(id, urlSafeClientSecret(), "示例受限客户端");
        authorizeClient(id, true, apis, data);
        return tokenFor(id);
    }

    /**
     * 按发布版服务端可解析的行格式直接写入客户端。
     *
     * <p>不能借用 {@code JdbcRegisteredClientRepository} 写入：其对 {@code java.time.Duration}
     * 生成对象形态 JSON，而发布版服务端读取端要求数组形态，两者不兼容会导致客户端读取失败。</p>
     */
    private void insertClient(String id, String clientSecret, String name) {
        clientSecrets.put(id, clientSecret);
        jdbc.update("INSERT INTO oauth2_registered_client "
                        + "(id, client_id, client_id_issued_at, client_secret, client_secret_expires_at, client_name, "
                        + "client_authentication_methods, authorization_grant_types, redirect_uris, scopes, "
                        + "client_settings, token_settings) "
                        + "VALUES (?, ?, NOW(6), ?, NULL, ?, "
                        + "'client_secret_basic', 'client_credentials', 'https://sample.example.test/callback', 'read', ?, ?)",
                UUID.randomUUID().toString(), id, "{noop}" + clientSecret, name,
                SERVER_CLIENT_SETTINGS_JSON, SERVER_TOKEN_SETTINGS_JSON);
    }

    private String tokenFor(String id) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(id, clientSecrets.get(id));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read");
        ResponseEntity<String> response = http.postForEntity(endpoint + "/oauth2/token", new HttpEntity<>(body, headers), String.class);
        JsonNode json = mapper.readTree(response.getBody());
        String token = json.path("access_token").asText();
        if (response.getStatusCodeValue() != 200 || token.isEmpty())
            throw new IllegalStateException("正式 AKSK 未返回有效令牌。");
        return token;
    }

    /**
     * 通过正式撤销端点验证在线失效，不手工清理缓存。
     */
    public void revoke(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(subject, clientSecrets.get(subject));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        if (http.postForEntity(endpoint + "/oauth2/revoke", new HttpEntity<>(body, headers), String.class).getStatusCodeValue() != 200) {
            throw new IllegalStateException("正式 AKSK 撤销失败。");
        }
    }

    private void writeYaml(Path path, Map<String, Object> values) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            new Yaml().dump(values, writer);
        }
    }

    /**
     * 只释放本实例确切拥有的子进程和随机空库，不操作常驻环境。
     */
    @Override
    public void close() {
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        if (administration != null && database.matches("iam_akp_fixture_[a-f0-9]{32}")) {
            try (Connection connection = administration.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS `" + database + "`");
            } catch (Exception cleanupFailure) {
                throw new IllegalStateException("测试拥有数据库未能回收，需核对本地资源。", null);
            }
        }
        if (directory != null) {
            try {
                Files.deleteIfExists(directory.resolve("application-local.yml"));
            } catch (IOException cleanupFailure) {
                throw new IllegalStateException("测试临时敏感配置未能回收。", null);
            }
        }
    }
}
