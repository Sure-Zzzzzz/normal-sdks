package io.github.surezzzzzz.sdk.s3.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/8 16:56
 */
@Getter
@Setter
@NoArgsConstructor
@OssComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.s3")
public class OssProperties {
    //需要与存储桶的生命周期策略对应
    public String bucketExpirationPrefix = "expiration-";
    public Integer bucketExpirationDays = 180;

    private String roleArn;
    private String accessKey;
    private String secretKey;
    //endpoint 必须为目标网关确切的服务地址和端口，前面必须加上 http://用于指定协议
    private String endpoint;
    //给前端返回url用的
    private String urlPrefix;
    private String downloadDirectory = "./";
    //临时ak/sk的有效时间，默认1天
    private Integer stsDurationSeconds = 86400;
    private Long presignedUrlExpirationSeconds = 86400L;
    // 连接池最大连接数，默认 50 个
    private Integer maxConnections = 500;
    // 请求执行超时时间，默认为 0 （关闭超时）。
    private Integer clientExecutionTimeout = 0;
    // 创建新连接超时时间，默认 10s
    private Integer connectionTimeout = 10 * 1000;
    // 连接处于 idle 状态的时间，默认 60s。
    private Integer connectionMaxIdleMillis = 60 * 1000;
    // 连接池中连接的默认过期时间，默认-1 （关闭过期）
    private Long connectionTTL = -1L;
    // 最大下载重试次数
    private Integer maxDownloadRetryTimes = 5;
    // 最大下载重试时间
    private Integer maxDownloadRetrySeconds = 600;
    // 最大上传重试次数
    private Integer maxUploadRetryTimes = 5;
    // 最大上传重试时间
    private Integer maxUploadRetrySeconds = 600;

}
