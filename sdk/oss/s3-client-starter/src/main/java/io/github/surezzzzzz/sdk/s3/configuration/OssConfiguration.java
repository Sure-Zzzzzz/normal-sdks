package io.github.surezzzzzz.sdk.s3.configuration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import io.github.surezzzzzz.sdk.s3.OssPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/11 16:47
 */
@Configuration
@Slf4j
@ComponentScan(basePackageClasses = OssPackage.class, includeFilters = @ComponentScan.Filter(OssComponent.class))
public class OssConfiguration {

    @Autowired
    private OssProperties ossProperties;

    @Bean
    public AWSSecurityTokenService awsSecurityTokenService() {
        log.info("开始创建AWS安全令牌服务客户端");
        return AWSSecurityTokenServiceClientBuilder.standard()
                .withClientConfiguration(new ClientConfiguration()
                        .withUseExpectContinue(false)
                        .withProtocol(Protocol.HTTP)
                        //sts只能用v4版本的签名方式
                        .withSignerOverride("AWSS3V4SignerType")
                        .withMaxConnections(ossProperties.getMaxConnections())
                        .withConnectionTimeout(ossProperties.getConnectionTimeout())
                        .withClientExecutionTimeout(ossProperties.getClientExecutionTimeout())
                        .withConnectionMaxIdleMillis(ossProperties.getConnectionMaxIdleMillis())
                        .withConnectionTTL(ossProperties.getConnectionTTL()))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(ossProperties.getAccessKey(), ossProperties.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(ossProperties.getEndpoint(), null))
                .build();

    }

    @Bean
    public AmazonS3 amazonS3() {
        log.info("开始创建AmazonS3客户端");
        return AmazonS3ClientBuilder.standard()
                .withClientConfiguration(new ClientConfiguration()
                        .withUseExpectContinue(false)
                        .withProtocol(Protocol.HTTP)
                        //由于我们需要用公网域名替换内网ip:端口，所以需要用v2版本的签名方式，请不要修改
                        .withSignerOverride("S3SignerType")
                        .withMaxConnections(ossProperties.getMaxConnections())
                        .withConnectionTimeout(ossProperties.getConnectionTimeout())
                        .withClientExecutionTimeout(ossProperties.getClientExecutionTimeout())
                        .withConnectionMaxIdleMillis(ossProperties.getConnectionMaxIdleMillis())
                        .withConnectionTTL(ossProperties.getConnectionTTL()))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(ossProperties.getAccessKey(), ossProperties.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(ossProperties.getEndpoint(), null))
                .withPathStyleAccessEnabled(true)
                .build();
    }

}
