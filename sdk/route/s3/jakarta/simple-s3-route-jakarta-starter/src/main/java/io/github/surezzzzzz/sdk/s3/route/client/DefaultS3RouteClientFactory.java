package io.github.surezzzzzz.sdk.s3.route.client;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.*;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.*;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.support.S3RouteTrustedCaHelper;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * 默认 target 客户端创建器，基于 AWS SDK v1 构建带独立连接池的 AmazonS3。
 * 与仓库既有 S3 客户端先例一致关闭 Expect-Continue，兼容不支持的代理与网关；
 * 连接池默认参数与先例对齐；支持 V2 签名覆盖与私有 CA 信任链。
 *
 * @author surezzzzzz
 */
public class DefaultS3RouteClientFactory implements S3RouteClientFactory {

    @Override
    public AmazonS3 create(String targetKey, SimpleS3RouteProperties.TargetConfig target) {
        if (target == null || target.getClient() == null) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
        }
        validateAwsCertificateChecking();
        ClientConfiguration configuration = buildClientConfiguration(target);
        return AmazonS3ClientBuilder.standard()
                .withClientConfiguration(configuration)
                .withCredentials(credentialsProvider(target))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        target.getEndpoint(), target.getRegion()))
                .withPathStyleAccessEnabled(target.isPathStyleEnabled())
                .build();
    }

    private ClientConfiguration buildClientConfiguration(SimpleS3RouteProperties.TargetConfig target) {
        SimpleS3RouteProperties.ClientConfig client = target.getClient();
        ClientConfiguration configuration = new ClientConfiguration()
                .withUseExpectContinue(false)
                .withProtocol(protocol(target.getEndpoint()))
                .withConnectionTimeout(client.getConnectTimeoutMs())
                .withSocketTimeout(client.getSocketTimeoutMs())
                .withMaxConnections(client.getMaxConnections())
                .withRequestTimeout(client.getRequestTimeoutMs())
                .withClientExecutionTimeout(client.getClientExecutionTimeoutMs())
                .withConnectionMaxIdleMillis(client.getConnectionMaxIdleMs())
                .withConnectionTTL(client.getConnectionTtlMs());
        if (target.getSignerType() == S3RouteSignerType.S3_V2) {
            configuration.setSignerOverride(SimpleS3RouteConstant.SIGNER_TYPE_S3_V2);
        }
        if (hasText(target.getTrustedCaFile())) {
            configuration.getApacheHttpClientConfig()
                    .setSslSocketFactory(buildSslSocketFactory(target.getTrustedCaFile()));
        }
        return configuration;
    }

    private SSLConnectionSocketFactory buildSslSocketFactory(String trustedCaFile) {
        List<X509Certificate> certificates = S3RouteTrustedCaHelper.loadCertificates(trustedCaFile);
        return S3RouteTrustedCaHelper.createSslSocketFactory(certificates);
    }

    private Protocol protocol(String endpoint) {
        String scheme = URI.create(endpoint).getScheme();
        return SimpleS3RouteConstant.HTTPS_SCHEME.equalsIgnoreCase(scheme) ? Protocol.HTTPS : Protocol.HTTP;
    }

    private void validateAwsCertificateChecking() {
        if (Boolean.parseBoolean(System.getProperty(
                SimpleS3RouteConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY))) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.AWS_CERT_CHECKING_DISABLED);
        }
    }

    private AWSCredentialsProvider credentialsProvider(SimpleS3RouteProperties.TargetConfig target) {
        SimpleS3RouteProperties.AuthenticationConfig authentication = target.getAuthentication();
        if (authentication != null && authentication.getType() == S3RouteAuthenticationType.ACCESS_KEY) {
            if (hasText(authentication.getSessionToken())) {
                return new AWSStaticCredentialsProvider(new BasicSessionCredentials(
                        authentication.getAccessKey(), authentication.getSecretKey(),
                        authentication.getSessionToken()));
            }
            return new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                    authentication.getAccessKey(), authentication.getSecretKey()));
        }
        return new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
