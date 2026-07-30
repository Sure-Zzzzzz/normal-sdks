package io.github.surezzzzzz.sdk.oss.s3.configuration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import io.github.surezzzzzz.sdk.oss.s3.S3ClientPackage;
import io.github.surezzzzzz.sdk.oss.s3.annotation.S3ClientComponent;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.S3ClientPropertiesInvalidException;
import io.github.surezzzzzz.sdk.oss.s3.support.TrustedCaHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * S3Client 自动配置类
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(S3ClientProperties.class)
@ComponentScan(
        basePackageClasses = S3ClientPackage.class,
        includeFilters = @ComponentScan.Filter(S3ClientComponent.class)
)
@ConditionalOnProperty(prefix = S3ClientConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class S3ClientConfiguration {

    @Autowired
    private S3ClientProperties properties;

    private URI endpointUri;
    private SSLConnectionSocketFactory sslSocketFactory;

    /**
     * 在创建 AWS 客户端前校验端点与 TLS 配置。
     */
    @PostConstruct
    public void init() {
        validateAwsCertificateChecking();
        endpointUri = TrustedCaHelper.parseEndpoint(properties.getEndpoint());
        if (StringUtils.isBlank(properties.getTrustedCaFile())) {
            return;
        }
        if (!TrustedCaHelper.isHttps(endpointUri)) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_ENDPOINT_NOT_HTTPS);
        }
        List<X509Certificate> certificates = TrustedCaHelper.loadCertificates(properties.getTrustedCaFile());
        sslSocketFactory = TrustedCaHelper.createSslSocketFactory(certificates, properties.getTrustedCaFile());
        log.info("S3 客户端已加载自定义 CA 信任文件，证书数量：{}", certificates.size());
    }

    /**
     * 创建 AWS 安全令牌服务客户端。
     *
     * @return AWS 安全令牌服务客户端
     */
    @Bean
    public AWSSecurityTokenService awsSecurityTokenService() {
        log.info("开始创建AWS安全令牌服务客户端");
        return AWSSecurityTokenServiceClientBuilder.standard()
                .withClientConfiguration(buildClientConfiguration(S3ClientConstant.SIGNER_TYPE_STS))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(properties.getAccessKey(), properties.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUri.toString(), null))
                .build();
    }

    /**
     * 创建 AmazonS3 客户端。
     *
     * @return AmazonS3 客户端
     */
    @Bean
    public AmazonS3 amazonS3() {
        log.info("开始创建AmazonS3客户端");
        return AmazonS3ClientBuilder.standard()
                .withClientConfiguration(buildClientConfiguration(S3ClientConstant.SIGNER_TYPE_S3))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(properties.getAccessKey(), properties.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUri.toString(), null))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    private void validateAwsCertificateChecking() {
        if (Boolean.parseBoolean(System.getProperty(S3ClientConstant.AWS_CERT_CHECKING_DISABLED_PROPERTY))) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_AWS_CERT_CHECKING_DISABLED);
        }
    }

    private ClientConfiguration buildClientConfiguration(String signerOverride) {
        ClientConfiguration config = new ClientConfiguration()
                .withUseExpectContinue(false)
                .withProtocol(TrustedCaHelper.isHttps(endpointUri) ? Protocol.HTTPS : Protocol.HTTP)
                .withMaxConnections(properties.getMaxConnections())
                .withConnectionTimeout(properties.getConnectionTimeout())
                .withClientExecutionTimeout(properties.getClientExecutionTimeout())
                .withConnectionMaxIdleMillis(properties.getConnectionMaxIdleMillis())
                .withConnectionTTL(properties.getConnectionTTL());
        if (sslSocketFactory != null) {
            config.getApacheHttpClientConfig().setSslSocketFactory(sslSocketFactory);
        }
        if (signerOverride != null) {
            config.setSignerOverride(signerOverride);
        }
        return config;
    }
}
