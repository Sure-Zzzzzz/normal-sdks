package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import io.github.surezzzzzz.sdk.s3.route.client.DefaultS3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteAuthenticationType;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteSignerType;
import io.github.surezzzzzz.sdk.s3.route.constant.SimpleS3RouteConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认 S3 target 客户端创建器构建结果测试。
 *
 * <p>真实构建 AmazonS3Client 并反射断言 endpoint、凭据与客户端参数落位，
 * 不发起任何网络请求。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultS3RouteClientFactoryTest {

    private final DefaultS3RouteClientFactory factory = new DefaultS3RouteClientFactory();
    private final List<AmazonS3> created = new ArrayList<AmazonS3>();

    @AfterEach
    void tearDown() {
        for (AmazonS3 client : created) {
            client.shutdown();
        }
        created.clear();
    }

    @Test
    void anonymousCredentialsForNoneType() throws Exception {
        SimpleS3RouteProperties.TargetConfig target = target("http://127.0.0.1:19000");
        AmazonS3Client client = create(target);

        Object credentials = credentialsProvider(client).getCredentials();
        log.info("NONE 类型凭据类型: {}", credentials.getClass().getSimpleName());
        assertTrue(credentials instanceof AnonymousAWSCredentials, "NONE 应使用匿名凭据");
    }

    @Test
    void basicCredentialsForAccessKeyType() throws Exception {
        SimpleS3RouteProperties.TargetConfig target = target("http://127.0.0.1:19000");
        target.getAuthentication().setType(S3RouteAuthenticationType.ACCESS_KEY);
        target.getAuthentication().setAccessKey("fixture-access-key");
        target.getAuthentication().setSecretKey("fixture-secret-key");
        AmazonS3Client client = create(target);

        Object credentials = credentialsProvider(client).getCredentials();
        assertTrue(credentials instanceof BasicAWSCredentials, "ACCESS_KEY 应使用静态凭据");
        assertEquals("fixture-access-key", ((BasicAWSCredentials) credentials).getAWSAccessKeyId());
        assertEquals("fixture-secret-key", ((BasicAWSCredentials) credentials).getAWSSecretKey());
        log.info("ACCESS_KEY 静态凭据落位验证通过");
    }

    @Test
    void sessionCredentialsWhenSessionTokenPresent() throws Exception {
        SimpleS3RouteProperties.TargetConfig target = target("http://127.0.0.1:19000");
        target.getAuthentication().setType(S3RouteAuthenticationType.ACCESS_KEY);
        target.getAuthentication().setAccessKey("fixture-access-key");
        target.getAuthentication().setSecretKey("fixture-secret-key");
        target.getAuthentication().setSessionToken("fixture-session-token");
        AmazonS3Client client = create(target);

        Object credentials = credentialsProvider(client).getCredentials();
        assertTrue(credentials instanceof BasicSessionCredentials, "带会话令牌应使用临时凭据");
        assertEquals("fixture-session-token",
                ((BasicSessionCredentials) credentials).getSessionToken());
        log.info("临时凭据会话令牌落位验证通过");
    }

    @Test
    void pathStyleFollowsConfiguration() throws Exception {
        AmazonS3Client pathStyle = create(target("http://127.0.0.1:19000"));
        SimpleS3RouteProperties.TargetConfig virtualHostConfig = target("http://127.0.0.1:19000");
        virtualHostConfig.setPathStyleEnabled(false);
        AmazonS3Client virtualHost = create(virtualHostConfig);

        assertTrue(clientOptions(pathStyle).isPathStyleAccess(), "默认应启用 Path Style 寻址");
        assertFalse(clientOptions(virtualHost).isPathStyleAccess(), "显式关闭应使用 Virtual Host 寻址");
        log.info("path-style 两态验证通过");
    }

    @Test
    void clientConfigurationApplied() throws Exception {
        SimpleS3RouteProperties.TargetConfig target = target("http://127.0.0.1:19000");
        target.getClient().setConnectTimeoutMs(1500);
        target.getClient().setSocketTimeoutMs(2500);
        target.getClient().setMaxConnections(7);
        target.getClient().setRequestTimeoutMs(3500);
        target.getClient().setClientExecutionTimeoutMs(4500);
        target.getClient().setConnectionMaxIdleMs(5500);
        target.getClient().setConnectionTtlMs(6500);
        AmazonS3Client client = create(target);

        ClientConfiguration configuration = clientConfiguration(client);
        assertEquals(1500, configuration.getConnectionTimeout());
        assertEquals(2500, configuration.getSocketTimeout());
        assertEquals(7, configuration.getMaxConnections());
        assertEquals(3500, configuration.getRequestTimeout());
        assertEquals(4500, configuration.getClientExecutionTimeout());
        assertEquals(5500, configuration.getConnectionMaxIdleMillis());
        assertEquals(6500, configuration.getConnectionTTL());
        log.info("客户端连接参数落位验证通过");
    }

    @Test
    void defaultPoolParametersMatchClientPrecedent() throws Exception {
        AmazonS3Client client = create(target("http://127.0.0.1:19000"));

        ClientConfiguration configuration = clientConfiguration(client);
        assertEquals(500, configuration.getMaxConnections(), "默认连接数应与既有 S3 客户端先例一致");
        assertEquals(0, configuration.getClientExecutionTimeout(), "默认不启用执行级超时");
        assertEquals(60000, configuration.getConnectionMaxIdleMillis());
        assertEquals(-1L, configuration.getConnectionTTL(), "默认不限制连接 TTL");
        log.info("连接池默认参数与先例一致");
    }

    @Test
    void signerOverrideFollowsConfiguration() throws Exception {
        AmazonS3Client defaultSigner = create(target("http://127.0.0.1:19000"));
        SimpleS3RouteProperties.TargetConfig v2Config = target("http://127.0.0.1:19000");
        v2Config.setSignerType(S3RouteSignerType.S3_V2);
        AmazonS3Client v2Signer = create(v2Config);

        assertNull(clientConfiguration(defaultSigner).getSignerOverride(),
                "默认应使用 SDK 自带的 AWS Signature V4");
        assertEquals(SimpleS3RouteConstant.SIGNER_TYPE_S3_V2,
                clientConfiguration(v2Signer).getSignerOverride(),
                "S3_V2 应覆盖为 S3SignerType 签名器");
        log.info("签名版本两态验证通过");
    }

    @Test
    void protocolFollowsEndpointScheme() throws Exception {
        AmazonS3Client http = create(target("http://127.0.0.1:19000"));
        AmazonS3Client https = create(target("https://127.0.0.1:19001"));

        assertEquals(Protocol.HTTP, clientConfiguration(http).getProtocol());
        assertEquals(Protocol.HTTPS, clientConfiguration(https).getProtocol());
        log.info("endpoint 协议两态验证通过");
    }

    @Test
    void endpointApplied() throws Exception {
        AmazonS3Client client = create(target("http://127.0.0.1:19000"));

        URI endpoint = endpoint(client);
        log.info("构建出的 endpoint: {}", endpoint);
        assertEquals("127.0.0.1", endpoint.getHost());
        assertEquals(19000, endpoint.getPort());
    }

    private AmazonS3Client create(SimpleS3RouteProperties.TargetConfig target) {
        AmazonS3 client = factory.create("test-main", target);
        created.add(client);
        return (AmazonS3Client) client;
    }

    private AWSCredentialsProvider credentialsProvider(AmazonS3Client client)
            throws Exception {
        return (AWSCredentialsProvider) field(client, "awsCredentialsProvider");
    }

    private ClientConfiguration clientConfiguration(AmazonS3Client client) throws Exception {
        return (ClientConfiguration) field(client, "clientConfiguration");
    }

    private S3ClientOptions clientOptions(AmazonS3Client client) throws Exception {
        return (S3ClientOptions) field(client, "clientOptions");
    }

    private URI endpoint(AmazonS3Client client) throws Exception {
        return (URI) field(client, "endpoint");
    }

    private Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private SimpleS3RouteProperties.TargetConfig target(String endpoint) {
        SimpleS3RouteProperties.TargetConfig target = new SimpleS3RouteProperties.TargetConfig();
        target.setEndpoint(endpoint);
        return target;
    }
}
