package io.github.surezzzzzz.sdk.s3.client.template;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.*;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.s3.client.configuration.SimpleS3ClientProperties;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteAuthenticationType;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * STS 临时凭证测试（同包）：成功路径注入 mock STS 客户端验证凭证透传、
 * assumeRole 全参数（roleArn、NotResource 降权策略、sessionName 规则）
 * 与桶级/目录级范围拼接；默认构建链走真实构建断言凭据形态落位
 * （构建不发起网络请求，两态：静态凭据 / 会话令牌临时凭据）。
 *
 * @author surezzzzzz
 */
@Slf4j
class S3ClientTemplateStsTest {

    private static final String TARGET = "test-sts";

    private static final int DURATION_SECONDS = 43200;

    private AWSSecurityTokenService stsClient;

    private SimpleS3ClientProperties properties;

    private S3ClientTemplate template;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        S3RouteTemplate routeTemplate = mock(S3RouteTemplate.class);
        TaskRetryExecutor retryExecutor = mock(TaskRetryExecutor.class);
        when(retryExecutor.executeWithFixedDelay(any(), anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    Callable<Object> task = invocation.getArgument(0);
                    return task.call();
                });
        when(routeTemplate.execute(anyString(), any()))
                .thenAnswer(invocation -> {
                    Function<AmazonS3, Object> callback = invocation.getArgument(1);
                    return callback.apply(mock(AmazonS3.class));
                });

        SimpleS3RouteProperties routeProperties = new SimpleS3RouteProperties();
        SimpleS3RouteProperties.TargetConfig targetConfig = new SimpleS3RouteProperties.TargetConfig();
        targetConfig.setEndpoint("http://sts-only-test.invalid");
        targetConfig.setRegion("us-east-1");
        SimpleS3RouteProperties.AuthenticationConfig authentication =
                new SimpleS3RouteProperties.AuthenticationConfig();
        authentication.setType(S3RouteAuthenticationType.ACCESS_KEY);
        authentication.setAccessKey("sts-test-access");
        authentication.setSecretKey("sts-test-secret");
        targetConfig.setAuthentication(authentication);
        routeProperties.setTargets(new HashMap<>(java.util.Collections.singletonMap(TARGET, targetConfig)));

        properties = new SimpleS3ClientProperties();
        properties.getSts().setRoleArn("arn:aws:iam::123456789012:role/s3-limited");
        properties.getSts().setDurationSeconds(DURATION_SECONDS);

        stsClient = mock(AWSSecurityTokenService.class);
        template = new S3ClientTemplate(routeTemplate, routeProperties, properties, retryExecutor)
                .overrideStsClientFactory(config -> stsClient);
    }

    private Credentials expectedCredentials() {
        Credentials credentials = new Credentials();
        credentials.setAccessKeyId("sts-ak");
        credentials.setSecretAccessKey("sts-sk");
        credentials.setSessionToken("sts-token");
        return credentials;
    }

    @Test
    void getNormalStsCredentialsReturnsSessionTokenCredentials() {
        Credentials expected = expectedCredentials();
        GetSessionTokenResult result = new GetSessionTokenResult();
        result.setCredentials(expected);
        when(stsClient.getSessionToken(any(GetSessionTokenRequest.class))).thenReturn(result);

        Credentials actual = template.getNormalStsCredentials(TARGET);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<GetSessionTokenRequest> requestCaptor =
                ArgumentCaptor.forClass(GetSessionTokenRequest.class);
        verify(stsClient).getSessionToken(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getDurationSeconds()).isEqualTo(DURATION_SECONDS);
        log.info("普通 STS 凭证透传 Credentials 且 durationSeconds 按配置");
    }

    @Test
    void getPathStsCredentialsAssumesRoleWithNotResourcePolicy() {
        Credentials expected = expectedCredentials();
        AssumeRoleResult result = new AssumeRoleResult();
        result.setCredentials(expected);
        when(stsClient.assumeRole(any(AssumeRoleRequest.class))).thenReturn(result);

        Credentials actual = template.getPathStsCredentials(TARGET, "bucket-a");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<AssumeRoleRequest> requestCaptor = ArgumentCaptor.forClass(AssumeRoleRequest.class);
        verify(stsClient).assumeRole(requestCaptor.capture());
        AssumeRoleRequest request = requestCaptor.getValue();
        assertThat(request.getRoleArn()).isEqualTo("arn:aws:iam::123456789012:role/s3-limited");
        assertThat(request.getDurationSeconds()).isEqualTo(DURATION_SECONDS);
        assertThat(request.getRoleSessionName()).isEqualTo("bucket-a-session");
        String policy = request.getPolicy();
        assertThat(policy).contains("arn:aws:s3:::bucket-a/*");
        assertThat(policy).contains("\"arn:aws:s3:::bucket-a\"");
        assertThat(policy).contains("\"Effect\":\"Deny\"");
        assertThat(policy).contains("\"NotResource\"");
        assertThat(policy).contains("\"Version\"");
        log.info("路径级 STS assumeRole 携带 NotResource 降权策略与受控会话名");
    }

    @Test
    void getDirStsCredentialsLimitsScopeToDirectory() {
        AssumeRoleResult result = new AssumeRoleResult();
        result.setCredentials(expectedCredentials());
        when(stsClient.assumeRole(any(AssumeRoleRequest.class))).thenReturn(result);

        template.getDirStsCredentials(TARGET, "bucket-a", "dir-1");

        ArgumentCaptor<AssumeRoleRequest> requestCaptor = ArgumentCaptor.forClass(AssumeRoleRequest.class);
        verify(stsClient).assumeRole(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPolicy()).contains("arn:aws:s3:::bucket-a/dir-1/*");
        log.info("目录级 STS 限定桶内目录范围 NotResource");
    }

    @Test
    void getBucketStsCredentialsLimitsScopeToBucket() {
        AssumeRoleResult result = new AssumeRoleResult();
        result.setCredentials(expectedCredentials());
        when(stsClient.assumeRole(any(AssumeRoleRequest.class))).thenReturn(result);

        template.getBucketStsCredentials(TARGET, "bucket-b");

        ArgumentCaptor<AssumeRoleRequest> requestCaptor = ArgumentCaptor.forClass(AssumeRoleRequest.class);
        verify(stsClient).assumeRole(requestCaptor.capture());
        String policy = requestCaptor.getValue().getPolicy();
        assertThat(policy).contains("arn:aws:s3:::bucket-b/*");
        assertThat(policy).doesNotContain("arn:aws:s3:::bucket-a");
        log.info("桶级 STS 限定单桶范围 NotResource");
    }

    @Test
    void defaultStsClientBuildsWithStaticCredentials() throws Exception {
        SimpleS3RouteProperties.TargetConfig target = stsTargetConfig("sts-ak", "sts-sk", null);
        AWSSecurityTokenService client = template.buildDefaultStsClient(target);
        try {
            Object credentials = credentialsProvider(client).getCredentials();
            assertThat(credentials).isInstanceOf(BasicAWSCredentials.class);
            assertThat(((BasicAWSCredentials) credentials).getAWSAccessKeyId()).isEqualTo("sts-ak");
            assertThat(((BasicAWSCredentials) credentials).getAWSSecretKey()).isEqualTo("sts-sk");
            log.info("默认 STS 构建使用 ACCESS_KEY 静态凭据");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void defaultStsClientBuildsWithSessionCredentialsWhenTokenPresent() throws Exception {
        SimpleS3RouteProperties.TargetConfig target = stsTargetConfig("sts-ak", "sts-sk", "sts-session");
        AWSSecurityTokenService client = template.buildDefaultStsClient(target);
        try {
            Object credentials = credentialsProvider(client).getCredentials();
            assertThat(credentials).isInstanceOf(BasicSessionCredentials.class);
            assertThat(((BasicSessionCredentials) credentials).getSessionToken()).isEqualTo("sts-session");
            log.info("带会话令牌时默认 STS 构建使用临时凭据");
        } finally {
            client.shutdown();
        }
    }

    private SimpleS3RouteProperties.TargetConfig stsTargetConfig(String accessKey, String secretKey,
                                                                 String sessionToken) {
        SimpleS3RouteProperties.TargetConfig targetConfig = new SimpleS3RouteProperties.TargetConfig();
        targetConfig.setEndpoint("http://127.0.0.1:19000");
        targetConfig.setRegion("us-east-1");
        SimpleS3RouteProperties.AuthenticationConfig authentication =
                new SimpleS3RouteProperties.AuthenticationConfig();
        authentication.setType(S3RouteAuthenticationType.ACCESS_KEY);
        authentication.setAccessKey(accessKey);
        authentication.setSecretKey(secretKey);
        authentication.setSessionToken(sessionToken);
        targetConfig.setAuthentication(authentication);
        return targetConfig;
    }

    private AWSCredentialsProvider credentialsProvider(
            AWSSecurityTokenService client) throws Exception {
        for (Class<?> type = client.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("awsCredentialsProvider");
                field.setAccessible(true);
                return (AWSCredentialsProvider) field.get(client);
            } catch (NoSuchFieldException ignored) {
                // 沿类层级向上查找
            }
        }
        throw new NoSuchFieldException("awsCredentialsProvider");
    }
}
