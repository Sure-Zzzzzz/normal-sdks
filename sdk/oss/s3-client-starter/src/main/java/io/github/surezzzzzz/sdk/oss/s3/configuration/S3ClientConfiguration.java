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
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * S3Client 自动配置类
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

    /**
     * 创建 AWS 安全令牌服务客户端
     */
    @Bean
    public AWSSecurityTokenService awsSecurityTokenService() {
        log.info("开始创建AWS安全令牌服务客户端");
        return AWSSecurityTokenServiceClientBuilder.standard()
                .withClientConfiguration(buildClientConfiguration(S3ClientConstant.SIGNER_TYPE_STS))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(properties.getAccessKey(), properties.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(properties.getEndpoint(), null))
                .build();
    }

    /**
     * 创建 AmazonS3 客户端
     */
    @Bean
    public AmazonS3 amazonS3() {
        log.info("开始创建AmazonS3客户端");
        return AmazonS3ClientBuilder.standard()
                .withClientConfiguration(buildClientConfiguration(S3ClientConstant.SIGNER_TYPE_S3))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(properties.getAccessKey(), properties.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(properties.getEndpoint(), null))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    private ClientConfiguration buildClientConfiguration(String signerOverride) {
        ClientConfiguration config = new ClientConfiguration()
                .withUseExpectContinue(false)
                .withProtocol(Protocol.HTTP)
                .withMaxConnections(properties.getMaxConnections())
                .withConnectionTimeout(properties.getConnectionTimeout())
                .withClientExecutionTimeout(properties.getClientExecutionTimeout())
                .withConnectionMaxIdleMillis(properties.getConnectionMaxIdleMillis())
                .withConnectionTTL(properties.getConnectionTTL());
        if (signerOverride != null) {
            config.setSignerOverride(signerOverride);
        }
        return config;
    }
}