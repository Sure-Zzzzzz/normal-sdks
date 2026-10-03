package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.AmazonClientException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.surezzzzzz.sdk.s3.route.client.DefaultS3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.constant.SimpleS3RouteConstant;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;
import io.github.surezzzzzz.sdk.s3.route.support.S3RouteTrustedCaHelper;
import io.github.surezzzzzz.sdk.s3.route.validator.DefaultS3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3 Route 私有 CA 信任测试。使用进程内、测试专用的最小 HTTPS 夹具
 * （自签 CA 签发、SAN 仅含 localhost），验证配置 CA 后信任链建立、
 * 未配置 CA 时拒绝、主机名校验保留与 CA 文件校验规则；
 * 不访问任何真实对象存储。
 *
 * @author surezzzzzz
 */
@Slf4j
class S3RouteTlsTest {

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
    private static final String S3_LIST_BUCKETS_RESPONSE =
            "<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                    + "<Buckets><Bucket><Name>test-bucket</Name></Bucket></Buckets>"
                    + "</ListAllMyBucketsResult>";

    private String previousAwsCertificateChecking;
    private boolean awsCertificateCheckingChanged;

    private static boolean hasSslExceptionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SSLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @AfterEach
    void restoreAwsCertificateChecking() {
        if (!awsCertificateCheckingChanged) {
            return;
        }
        if (previousAwsCertificateChecking == null) {
            System.clearProperty(SimpleS3RouteConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY);
        } else {
            System.setProperty(
                    SimpleS3RouteConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY, previousAwsCertificateChecking);
        }
        awsCertificateCheckingChanged = false;
    }

    @Test
    void trustedCaAllowsTlsHandshakeAndMinimalRequest() throws Exception {
        try (LocalTlsServer server = new LocalTlsServer()) {
            SimpleS3RouteRegistry registry = registry(server, getTrustedCaFile().toString());
            try {
                assertDoesNotThrow(() -> registry.getAmazonS3("test-main").listBuckets(),
                        "正确 CA 应允许 TLS 握手和最小请求");
                assertEquals(1, server.getRequestCount(), "必须完成一次 HTTPS 请求");
                log.info("配置 CA 后 TLS 握手与最小请求通过");
            } finally {
                registry.destroy();
            }
        }
    }

    @Test
    void defaultTrustRejectsPrivateCaServer() throws Exception {
        try (LocalTlsServer server = new LocalTlsServer()) {
            SimpleS3RouteRegistry registry = registry(server, null);
            try {
                AmazonClientException exception = assertThrows(AmazonClientException.class,
                        () -> registry.getAmazonS3("test-main").listBuckets(),
                        "未配置 CA 时应在 TLS 证书链校验阶段失败");
                assertTrue(hasSslExceptionCause(exception), "失败原因必须落在 TLS 层（SSLException）");
                assertEquals(0, server.getRequestCount(), "TLS 握手失败时服务端不应收到 HTTP 请求");
            } finally {
                registry.destroy();
            }
        }
    }

    @Test
    void hostnameMismatchStillFails() throws Exception {
        try (LocalTlsServer server = new LocalTlsServer()) {
            SimpleS3RouteRegistry registry = registry(
                    "https://127.0.0.1:" + server.getPort(), getTrustedCaFile().toString());
            try {
                AmazonClientException exception = assertThrows(AmazonClientException.class,
                        () -> registry.getAmazonS3("test-main").listBuckets(),
                        "127.0.0.1 不在 localhost SAN 中，主机名校验应失败");
                assertTrue(hasSslExceptionCause(exception), "失败原因必须落在 TLS 层（SSLException）");
                assertEquals(0, server.getRequestCount(), "主机名校验失败时服务端不应收到 HTTP 请求");
            } finally {
                registry.destroy();
            }
        }
    }

    @Test
    void trustedCaFileContentParsing() throws Exception {
        Path crtFile = copyResourceToTemporaryFile(TRUSTED_CA_RESOURCE, "test-ca-", ".crt");
        Path pemFile = copyResourceToTemporaryFile(TRUSTED_CA_RESOURCE, "test-ca-", ".pem");
        Path cerFile = copyResourceToTemporaryFile(TRUSTED_CA_DER_RESOURCE, "test-ca-", ".cer");
        Path multipleFile = Files.createTempFile("test-ca-multiple-", ".pem");
        Files.copy(getTrustedCaFile(), multipleFile, StandardCopyOption.REPLACE_EXISTING);
        appendFile(copyResourceToTemporaryFile(
                TRUSTED_CA_SECONDARY_RESOURCE, "test-ca-secondary-", ".pem"), multipleFile);

        assertEquals(1, S3RouteTrustedCaHelper.loadCertificates(crtFile.toString()).size(),
                ".crt PEM 内容应解析成功");
        assertEquals(1, S3RouteTrustedCaHelper.loadCertificates(pemFile.toString()).size(),
                ".pem 内容应解析成功");
        assertEquals(1, S3RouteTrustedCaHelper.loadCertificates(cerFile.toString()).size(),
                ".cer DER 内容应解析成功");
        assertEquals(2, S3RouteTrustedCaHelper.loadCertificates(multipleFile.toString()).size(),
                "多 CA 文件应加载全部证书");
        log.info("CA PEM、DER 与多证书内容解析通过");
    }

    @Test
    void trustedCaFileValidation() throws Exception {
        Path missingFile = Files.createTempDirectory("test-ca-missing").resolve("missing-ca.txt");
        Path directory = Files.createTempDirectory("test-ca-directory");
        Path invalidFile = Files.createTempFile("test-ca-invalid-", ".txt");
        Path emptyFile = Files.createTempFile("test-ca-empty-", ".txt");
        Files.write(invalidFile, "not-a-certificate".getBytes(StandardCharsets.UTF_8));
        Path leafFile = copyResourceToTemporaryFile(LEAF_CERTIFICATE_RESOURCE, "test-leaf-", ".txt");
        Path noKeyCertSignFile = copyResourceToTemporaryFile(
                TRUSTED_CA_NO_KEY_CERT_SIGN_RESOURCE, "test-ca-no-key-cert-sign-", ".txt");
        Path expiredFile = copyResourceToTemporaryFile(
                TRUSTED_CA_EXPIRED_RESOURCE, "test-ca-expired-", ".txt");
        Path futureFile = copyResourceToTemporaryFile(
                TRUSTED_CA_FUTURE_RESOURCE, "test-ca-future-", ".txt");

        assertConfigurationIllegal(missingFile.toString(), "不存在 CA 文件应被拒绝");
        assertConfigurationIllegal(directory.toString(), "目录 CA 路径应被拒绝");
        assertConfigurationIllegal(invalidFile.toString(), "损坏 CA 文件应被拒绝");
        assertConfigurationIllegal(emptyFile.toString(), "空 CA 文件应被拒绝");
        assertConfigurationIllegal(leafFile.toString(), "叶证书不能作为 CA 文件");
        assertConfigurationIllegal(noKeyCertSignFile.toString(),
                "声明 KeyUsage 但缺少 keyCertSign 的 CA 文件应被拒绝");
        assertConfigurationIllegal(expiredFile.toString(), "过期 CA 文件应被拒绝");
        assertConfigurationIllegal(futureFile.toString(), "未生效 CA 文件应被拒绝");
        assertConfigurationIllegal(String.valueOf('\0'), "非法 CA 路径应被拒绝");
    }

    @Test
    void httpEndpointWithTrustedCaRejectedByValidator() throws Exception {
        SimpleS3RouteProperties properties = properties("http://127.0.0.1:19000",
                getTrustedCaFile().toString());
        S3RouteException exception = assertThrows(S3RouteException.class,
                () -> new DefaultS3RoutePropertiesValidator().validate(properties),
                "HTTP endpoint 配置 CA 应在校验期被拒绝");
        assertEquals(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("HTTPS"),
                "拒绝原因必须是 CA 要求 HTTPS 而非其他配置错误");
        log.info("HTTP + CA 组合校验拒绝消息: {}", exception.getMessage());
    }

    @Test
    void awsCertificateCheckingDisabledRejected() throws Exception {
        previousAwsCertificateChecking = System.getProperty(
                SimpleS3RouteConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY);
        awsCertificateCheckingChanged = true;
        System.setProperty(
                SimpleS3RouteConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY, Boolean.TRUE.toString());

        SimpleS3RouteProperties properties = properties("https://127.0.0.1:19000", null);
        S3RouteException exception = assertThrows(S3RouteException.class,
                () -> new DefaultS3RouteClientFactory().create("test-main", properties.getTargets().get("test-main")),
                "AWS SDK 全局关校验的运行态应被拒绝");
        assertEquals(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, exception.getErrorCode());
        log.info("全局关校验拒绝消息: {}", exception.getMessage());
    }

    private SimpleS3RouteRegistry registry(LocalTlsServer server, String trustedCaFile) {
        return registry("https://localhost:" + server.getPort(), trustedCaFile);
    }

    private SimpleS3RouteRegistry registry(String endpoint, String trustedCaFile) {
        return new SimpleS3RouteRegistry(properties(endpoint, trustedCaFile),
                new DefaultS3RoutePropertiesValidator(), new DefaultS3RouteClientFactory());
    }

    private SimpleS3RouteProperties properties(String endpoint, String trustedCaFile) {
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();
        properties.setEnable(true);
        SimpleS3RouteProperties.TargetConfig target = new SimpleS3RouteProperties.TargetConfig();
        target.setEndpoint(endpoint);
        target.setTrustedCaFile(trustedCaFile);
        properties.getTargets().put("test-main", target);
        return properties;
    }

    private void assertConfigurationIllegal(String trustedCaFile, String message) {
        S3RouteException exception = assertThrows(S3RouteException.class,
                () -> S3RouteTrustedCaHelper.loadCertificates(trustedCaFile), message);
        assertEquals(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, exception.getErrorCode(),
                "CA 文件错误必须落在 target 配置非法错误码");
    }

    private Path getTrustedCaFile() throws Exception {
        return copyResourceToTemporaryFile(TRUSTED_CA_RESOURCE, "test-ca-", ".txt");
    }

    private Path copyResourceToTemporaryFile(String resourceName, String prefix, String suffix)
            throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(inputStream, "测试资源必须存在");
            Path target = Files.createTempFile(prefix, suffix);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
    }

    private void appendFile(Path source, Path target) throws IOException {
        try (InputStream inputStream = Files.newInputStream(source);
             OutputStream outputStream = Files.newOutputStream(
                     target, java.nio.file.StandardOpenOption.APPEND)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

    private final class LocalTlsServer implements AutoCloseable {

        private final HttpsServer server;
        private final List<String> methods = new CopyOnWriteArrayList<String>();

        private LocalTlsServer() throws Exception {
            server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(createServerSslContext()));
            server.createContext("/", this::handleRequest);
            server.start();
        }

        private SSLContext createServerSslContext() throws Exception {
            KeyStore keyStore = KeyStore.getInstance(TLS_KEY_STORE_TYPE);
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(TLS_KEY_STORE_RESOURCE)) {
                assertNotNull(inputStream, "测试 TLS keystore 资源必须存在");
                keyStore.load(inputStream, TLS_KEY_STORE_PASSWORD.toCharArray());
            }
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, TLS_KEY_STORE_PASSWORD.toCharArray());
            SSLContext sslContext = SSLContext.getInstance(SimpleS3RouteConstant.TLS_CONTEXT_PROTOCOL);
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
            methods.add(exchange.getRequestMethod());
            readRequestBody(exchange.getRequestBody());
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", XML_CONTENT_TYPE);
            byte[] responseBody = S3_LIST_BUCKETS_RESPONSE.getBytes(StandardCharsets.UTF_8);
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

        private int getPort() {
            return server.getAddress().getPort();
        }

        private int getRequestCount() {
            return methods.size();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
