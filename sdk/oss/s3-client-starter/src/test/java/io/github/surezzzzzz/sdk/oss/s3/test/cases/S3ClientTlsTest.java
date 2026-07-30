package io.github.surezzzzzz.sdk.oss.s3.test.cases;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.sun.net.httpserver.*;
import io.github.surezzzzzz.sdk.oss.s3.configuration.S3ClientConfiguration;
import io.github.surezzzzzz.sdk.oss.s3.configuration.S3ClientProperties;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.S3ClientPropertiesInvalidException;
import io.github.surezzzzzz.sdk.oss.s3.support.TrustedCaHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3Client TLS 配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
class S3ClientTlsTest {

    private static final String TEST_ACCESS_KEY = "test-access-key";
    private static final String TEST_SECRET_KEY = "test-secret-key";
    private static final String TRUSTED_CA_RESOURCE = "test-ca.txt";
    private static final String TRUSTED_CA_DER_RESOURCE = "test-ca-der.bin";
    private static final String TRUSTED_CA_SECONDARY_RESOURCE = "test-ca-secondary.txt";
    private static final String TRUSTED_CA_NO_KEY_CERT_SIGN_RESOURCE = "test-ca-no-key-cert-sign.txt";
    private static final String TRUSTED_CA_EXPIRED_RESOURCE = "test-ca-expired.txt";
    private static final String TRUSTED_CA_FUTURE_RESOURCE = "test-ca-future.txt";
    private static final String LEAF_CERTIFICATE_RESOURCE = "test-leaf.txt";
    private static final String TLS_KEY_STORE_RESOURCE = "test-tls-keystore.bin";
    private static final String TLS_KEY_STORE_TYPE = "PKCS12";
    private static final String TLS_KEY_STORE_PASSWORD = "test-ca-password";
    private static final String XML_CONTENT_TYPE = "application/xml";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";
    private static final String S3_LIST_BUCKETS_RESPONSE =
            "<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                    + "<Buckets><Bucket><Name>test-bucket</Name></Bucket></Buckets>"
                    + "</ListAllMyBucketsResult>";
    private static final String STS_GET_CALLER_IDENTITY_RESPONSE =
            "<GetCallerIdentityResponse xmlns=\"https://sts.amazonaws.com/doc/2011-06-15/\">"
                    + "<GetCallerIdentityResult><Account>123456789012</Account><Arn>"
                    + "arn:aws:sts::123456789012:root</Arn><UserId>test-user</UserId>"
                    + "</GetCallerIdentityResult></GetCallerIdentityResponse>";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3ClientConfiguration.class);

    private final List<String> initializedBeanNames = new ArrayList<>();
    private String previousAwsCertificateChecking;
    private boolean awsCertificateCheckingChanged;

    @AfterEach
    void restoreAwsCertificateChecking() {
        initializedBeanNames.clear();
        if (!awsCertificateCheckingChanged) {
            return;
        }
        if (previousAwsCertificateChecking == null) {
            System.clearProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY);
        } else {
            System.setProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY, previousAwsCertificateChecking);
        }
        awsCertificateCheckingChanged = false;
    }

    @Test
    @DisplayName("配置 CA 后 S3 与 STS 使用同一专用信任链")
    void testS3AndStsUseTrustedCa() throws Exception {
        try (LocalTlsServer server = new LocalTlsServer()) {
            S3ClientConfiguration configuration = createConfiguration(server.getLocalhostEndpoint(), getTrustedCaFile().toString());
            AmazonS3 amazonS3 = configuration.amazonS3();
            AWSSecurityTokenService securityTokenService = configuration.awsSecurityTokenService();

            assertDoesNotThrow(() -> amazonS3.listBuckets(), "正确 CA 应允许 S3 TLS 握手和最小请求");
            assertDoesNotThrow(() -> securityTokenService.getCallerIdentity(new GetCallerIdentityRequest()),
                    "正确 CA 应允许 STS TLS 握手和最小请求");
            List<RequestSnapshot> requests = server.getRequests();
            log.info("S3 与 STS TLS 请求数量={}, 请求={}", requests.size(), requests);
            assertEquals(2, requests.size(), "S3 与 STS 都必须完成一次 HTTPS 请求");
            assertEquals(HTTP_METHOD_GET, requests.get(0).getMethod(), "listBuckets 必须使用 GET 请求");
            assertEquals(HTTP_METHOD_POST, requests.get(1).getMethod(), "getCallerIdentity 必须使用 POST 请求");
            assertTrue(requests.get(1).getBody().contains("Action=GetCallerIdentity"),
                    "STS 请求必须包含 GetCallerIdentity Action");
        }
    }

    @Test
    @DisplayName("未配置 CA 时私有 CA 服务端证书仍被拒绝")
    void testDefaultTrustDoesNotTrustTestCa() throws Exception {
        try (LocalTlsServer server = new LocalTlsServer()) {
            S3ClientConfiguration configuration = createConfiguration(server.getLocalhostEndpoint(), null);
            AmazonS3 amazonS3 = configuration.amazonS3();

            AmazonClientException ex = assertThrows(AmazonClientException.class, amazonS3::listBuckets,
                    "未配置 CA 时应在 TLS 证书链校验阶段失败");
            log.info("未配置 CA 的 TLS 异常: {}", ex.getMessage());
            assertNotNull(ex.getCause(), "TLS 失败应保留原始原因");
            assertTrue(server.getRequests().isEmpty(), "TLS 握手失败时服务端不应收到 HTTP 请求");
        }
    }

    @Test
    @DisplayName("正确 CA 不会关闭 hostname verification")
    void testHostnameMismatchStillFails() throws Exception {
        try (LocalTlsServer server = new LocalTlsServer()) {
            S3ClientConfiguration configuration = createConfiguration(server.getLoopbackEndpoint(), getTrustedCaFile().toString());
            AmazonS3 amazonS3 = configuration.amazonS3();

            AmazonClientException ex = assertThrows(AmazonClientException.class, amazonS3::listBuckets,
                    "127.0.0.1 不在 localhost SAN 中，TLS hostname verification 应失败");
            log.info("主机名不匹配 TLS 异常: {}", ex.getMessage());
            assertNotNull(ex.getCause(), "主机名校验失败应保留原始原因");
            assertTrue(server.getRequests().isEmpty(), "主机名校验失败时服务端不应收到 HTTP 请求");
        }
    }

    @Test
    @DisplayName("未配置 CA 时 HTTP endpoint 仍使用 HTTP")
    void testHttpEndpointWithoutTrustedCa() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer()) {
            S3ClientConfiguration configuration = createConfiguration(server.getEndpoint(), null);
            AmazonS3 amazonS3 = configuration.amazonS3();

            assertDoesNotThrow(() -> amazonS3.listBuckets(), "未配置 CA 的 HTTP endpoint 必须保持可用");
            log.info("HTTP 回归请求数量={}", server.getRequestCount());
            assertEquals(1, server.getRequestCount(), "HTTP endpoint 必须收到 S3 请求");
        }
    }

    @Test
    @DisplayName("CA 文件按内容解析 PEM、DER 和多证书")
    void testTrustedCaFileContentParsing() throws Exception {
        Path crtFile = copyResourceToTemporaryFile(TRUSTED_CA_RESOURCE, "test-ca-", ".crt");
        Path pemFile = copyResourceToTemporaryFile(TRUSTED_CA_RESOURCE, "test-ca-", ".pem");
        Path cerFile = copyResourceToTemporaryFile(TRUSTED_CA_DER_RESOURCE, "test-ca-", ".cer");
        Path multipleFile = Files.createTempFile("test-ca-multiple-", ".pem");
        Files.copy(getTrustedCaFile(), multipleFile, StandardCopyOption.REPLACE_EXISTING);
        appendFile(copyResourceToTemporaryFile(TRUSTED_CA_SECONDARY_RESOURCE, "test-ca-secondary-", ".pem"),
                multipleFile);

        assertEquals(1, TrustedCaHelper.loadCertificates(crtFile.toString()).size(), ".crt PEM 内容应解析成功");
        assertEquals(1, TrustedCaHelper.loadCertificates(pemFile.toString()).size(), ".pem 内容应解析成功");
        assertEquals(1, TrustedCaHelper.loadCertificates(cerFile.toString()).size(), ".cer DER 内容应解析成功");
        assertEquals(2, TrustedCaHelper.loadCertificates(multipleFile.toString()).size(), "多 CA 文件应加载全部证书");
        log.info("CA PEM、DER 与多证书内容解析成功");
    }

    @Test
    @DisplayName("CA 文件必须是有效 CA 证书")
    void testTrustedCaFileValidation() throws Exception {
        Path missingFile = Files.createTempDirectory("test-ca-missing").resolve("missing-ca.txt");
        Path directory = Files.createTempDirectory("test-ca-directory");
        Path invalidFile = Files.createTempFile("test-ca-invalid-", ".txt");
        Path emptyFile = Files.createTempFile("test-ca-empty-", ".txt");
        Files.write(invalidFile, "not-a-certificate".getBytes(StandardCharsets.UTF_8));
        Path leafFile = copyResourceToTemporaryFile(LEAF_CERTIFICATE_RESOURCE, "test-leaf-", ".txt");
        Path noKeyCertSignFile = copyResourceToTemporaryFile(
                TRUSTED_CA_NO_KEY_CERT_SIGN_RESOURCE, "test-ca-no-key-cert-sign-", ".txt");
        Path expiredFile = copyResourceToTemporaryFile(TRUSTED_CA_EXPIRED_RESOURCE, "test-ca-expired-", ".txt");
        Path futureFile = copyResourceToTemporaryFile(TRUSTED_CA_FUTURE_RESOURCE, "test-ca-future-", ".txt");

        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(missingFile.toString()), "不存在 CA 文件应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(directory.toString()), "目录 CA 路径应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(invalidFile.toString()), "损坏 CA 文件应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(emptyFile.toString()), "空 CA 文件应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(leafFile.toString()), "叶证书不能作为 CA 文件");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(noKeyCertSignFile.toString()),
                "声明 KeyUsage 但缺少 keyCertSign 的 CA 文件应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(expiredFile.toString()), "过期 CA 文件应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(futureFile.toString()), "未生效 CA 文件应被拒绝");
        assertPropertiesInvalid(() -> TrustedCaHelper.loadCertificates(String.valueOf('\0')), "非法 CA 路径应被拒绝");
    }

    @Test
    @DisplayName("非法 endpoint 和 HTTP CA 配置在初始化前失败")
    void testEndpointValidation() throws Exception {
        Path trustedCaFile = getTrustedCaFile();
        String[] invalidEndpoints = {
                "ftp://localhost",
                "https://user@localhost",
                "https://localhost?test=value",
                "https://localhost#test",
                "https://localhost:65536",
                "https:///missing-host"
        };
        for (String invalidEndpoint : invalidEndpoints) {
            assertPropertiesInvalid(() -> TrustedCaHelper.parseEndpoint(invalidEndpoint),
                    "非法 endpoint 应被拒绝: " + invalidEndpoint);
        }
        assertPropertiesInvalid(() -> createConfiguration("http://localhost", trustedCaFile.toString()),
                "HTTP endpoint 配置 CA 应被拒绝");
    }

    @Test
    @DisplayName("AWS SDK 全局关闭证书校验时启动失败")
    void testAwsGlobalCertificateCheckingDisabled() {
        previousAwsCertificateChecking = System.getProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY);
        awsCertificateCheckingChanged = true;
        System.setProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY, Boolean.TRUE.toString());

        assertPropertiesInvalid(() -> createConfiguration("https://localhost", null),
                "AWS SDK 全局关校验时应拒绝启动");
    }

    @Test
    @DisplayName("HTTP endpoint 配置 CA 时 Spring 上下文在 AWS 客户端 Bean 创建前失败")
    void testHttpEndpointWithTrustedCaFailsApplicationContext() throws Exception {
        assertApplicationContextFailsBeforeAwsBeans(
                "http://localhost",
                getTrustedCaFile().toString(),
                "HTTP endpoint 配置 CA"
        );
    }

    @Test
    @DisplayName("非法 endpoint 时 Spring 上下文在 AWS 客户端 Bean 创建前失败")
    void testInvalidEndpointFailsApplicationContext() {
        assertApplicationContextFailsBeforeAwsBeans(
                "https://user@localhost",
                null,
                "非法 endpoint"
        );
    }

    @Test
    @DisplayName("不可读 CA 文件时 Spring 上下文在 AWS 客户端 Bean 创建前失败")
    void testUnreadableTrustedCaFailsApplicationContext() throws Exception {
        Path missingFile = Files.createTempDirectory("test-ca-context-missing").resolve("missing-ca.txt");
        assertApplicationContextFailsBeforeAwsBeans(
                "https://localhost",
                missingFile.toString(),
                "不可读 CA 文件"
        );
    }

    @Test
    @DisplayName("AWS SDK 全局关校验时 Spring 上下文在 AWS 客户端 Bean 创建前失败")
    void testAwsGlobalCertificateCheckingDisabledFailsApplicationContext() {
        previousAwsCertificateChecking = System.getProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY);
        awsCertificateCheckingChanged = true;
        System.setProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY, Boolean.TRUE.toString());

        assertApplicationContextFailsBeforeAwsBeans(
                "https://localhost",
                null,
                "AWS SDK 全局关闭证书校验"
        );
    }

    private S3ClientConfiguration createConfiguration(String endpoint, String trustedCaFile) {
        S3ClientProperties properties = new S3ClientProperties();
        properties.setEndpoint(endpoint);
        properties.setTrustedCaFile(trustedCaFile);
        properties.setAccessKey(TEST_ACCESS_KEY);
        properties.setSecretKey(TEST_SECRET_KEY);
        S3ClientConfiguration configuration = new S3ClientConfiguration();
        ReflectionTestUtils.setField(configuration, "properties", properties);
        configuration.init();
        return configuration;
    }

    private void assertApplicationContextFailsBeforeAwsBeans(
            String endpoint, String trustedCaFile, String scenario) {
        List<String> properties = new ArrayList<>();
        properties.add(property("endpoint", endpoint));
        properties.add(property("access-key", TEST_ACCESS_KEY));
        properties.add(property("secret-key", TEST_SECRET_KEY));
        if (trustedCaFile != null) {
            properties.add(property("trusted-ca-file", trustedCaFile));
        }
        initializedBeanNames.clear();
        contextRunner.withBean("tlsTestBeanCreationRecorder", BeanPostProcessor.class,
                        () -> new BeanPostProcessor() {
                            @Override
                            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                                initializedBeanNames.add(beanName);
                                return bean;
                            }
                        })
                .withPropertyValues(properties.toArray(new String[properties.size()]))
                .run(context -> {
                    log.info("{}启动异常: {}, 已初始化 Bean: {}", scenario, context.getStartupFailure(), initializedBeanNames);
                    assertNotNull(context.getStartupFailure(), scenario + "必须导致 Spring 上下文启动失败");
                    assertTrue(hasCause(context.getStartupFailure(), S3ClientPropertiesInvalidException.class),
                            "启动异常链必须包含 S3ClientPropertiesInvalidException");
                    assertTrue(initializedBeanNames.contains("s3ClientConfiguration"),
                            "配置 Bean 必须已进入初始化阶段");
                    assertFalse(initializedBeanNames.contains("amazonS3"), "初始化失败前不得创建 AmazonS3 Bean");
                    assertFalse(initializedBeanNames.contains("awsSecurityTokenService"), "初始化失败前不得创建 STS Bean");
                });
    }

    private String property(String name, String value) {
        return String.format("%s.%s=%s", S3ClientConstant.CONFIG_PREFIX, name, value);
    }

    private Path getTrustedCaFile() throws Exception {
        return copyResourceToTemporaryFile(TRUSTED_CA_RESOURCE, "test-ca-", ".txt");
    }

    private KeyStore getTlsKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(TLS_KEY_STORE_TYPE);
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(TLS_KEY_STORE_RESOURCE)) {
            assertNotNull(inputStream, "测试 TLS keystore 资源必须存在");
            keyStore.load(inputStream, TLS_KEY_STORE_PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private Path copyResourceToTemporaryFile(String resourceName, String prefix, String suffix) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(inputStream, "测试资源必须存在");
            Path target = Files.createTempFile(prefix, suffix);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
    }

    private void appendFile(Path source, Path target) throws IOException {
        try (InputStream inputStream = Files.newInputStream(source);
             java.io.OutputStream outputStream = Files.newOutputStream(
                     target, java.nio.file.StandardOpenOption.APPEND)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

    private void assertPropertiesInvalid(ThrowingRunnable runnable, String message) {
        S3ClientPropertiesInvalidException ex = assertThrows(S3ClientPropertiesInvalidException.class, runnable::run, message);
        log.info("配置异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.S3_CLIENT_PROPERTIES_INVALID, ex.getErrorCode(), "错误码必须为 OSS_301");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    private static final class RequestSnapshot {

        private final String method;
        private final String body;

        private RequestSnapshot(String method, String body) {
            this.method = method;
            this.body = body;
        }

        private String getMethod() {
            return method;
        }

        private String getBody() {
            return body;
        }

        @Override
        public String toString() {
            return String.format("RequestSnapshot{method='%s', body='%s'}", method, body);
        }
    }

    private final class LocalHttpServer implements AutoCloseable {

        private final HttpServer server;
        private int requestCount;

        private LocalHttpServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.createContext("/", this::handleRequest);
            server.start();
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
            requestCount++;
            byte[] responseBody = S3_LIST_BUCKETS_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", XML_CONTENT_TYPE);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        }

        private String getEndpoint() {
            return String.format("http://localhost:%d", server.getAddress().getPort());
        }

        private int getRequestCount() {
            return requestCount;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private final class LocalTlsServer implements AutoCloseable {

        private final HttpsServer server;
        private final List<RequestSnapshot> requests = new ArrayList<>();

        private LocalTlsServer() throws Exception {
            server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(createServerSslContext()));
            server.createContext("/", this::handleRequest);
            server.start();
        }

        private SSLContext createServerSslContext() throws Exception {
            KeyStore keyStore = getTlsKeyStore();
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, TLS_KEY_STORE_PASSWORD.toCharArray());
            SSLContext sslContext = SSLContext.getInstance(S3ClientConstant.TLS_CONTEXT_PROTOCOL);
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
            String requestBody = new String(readRequestBody(exchange.getRequestBody()), StandardCharsets.UTF_8);
            requests.add(new RequestSnapshot(exchange.getRequestMethod(), requestBody));
            String response = requestBody.contains("Action=GetCallerIdentity")
                    ? STS_GET_CALLER_IDENTITY_RESPONSE : S3_LIST_BUCKETS_RESPONSE;
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", XML_CONTENT_TYPE);
            byte[] responseBody = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        }

        private byte[] readRequestBody(InputStream inputStream) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        }

        private String getLocalhostEndpoint() {
            return String.format("https://localhost:%d", server.getAddress().getPort());
        }

        private String getLoopbackEndpoint() {
            return String.format("https://127.0.0.1:%d", server.getAddress().getPort());
        }

        private List<RequestSnapshot> getRequests() {
            return requests;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
