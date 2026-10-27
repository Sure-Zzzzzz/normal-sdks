package io.github.surezzzzzz.sdk.ticket.server.configuration;

import io.github.surezzzzzz.sdk.oss.s3.client.S3Client;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketServiceException;
import io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;

/**
 * S3 附件出口。单独隔离可选 S3 类型，散形态仅加载 {@link TicketFileConfiguration} 的 Noop 配置。
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.surezzzzzz.sdk.oss.s3.client.S3Client")
public class TicketS3FileConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TicketS3FileConfiguration.class);

    /**
     * S3 适配（s3-client-starter 在场且 attachment.enabled=true）。
     *
     * @param s3Client S3 客户端
     * @param ticketProperties 工单配置
     * @return FileProvider
     */
    @Bean
    @ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.ticket.server.attachment", name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(FileProvider.class)
    public FileProvider s3FileProvider(S3Client s3Client,
            SimpleTicketServerProperties ticketProperties) {
        log.info("工单附件出口：S3 预签名直传（聚形态）");
        return new FileProvider() {
            @Override
            public String issueUploadKey(String ticketNo, String fileName, String contentType) {
                return issueUploadKey(ticketNo, null, fileName, contentType);
            }

            @Override
            public String issueUploadKey(String ticketNo, String ownerSubjectId,
                                         String fileName, String contentType) {
                String prefix = ticketNo == null ? "ticket/pending" : "ticket/" + ticketNo;
                String owner = ownerSubjectId == null ? "unknown" : ownerDigest(ownerSubjectId);
                return prefix + "/" + owner + "/" + UUID.randomUUID().toString()
                        + "-" + sanitize(fileName);
            }

            @Override
            public boolean isUploadKeyOwned(String objectKey, String ownerSubjectId) {
                if (objectKey == null || ownerSubjectId == null) {
                    return false;
                }
                String owner = ownerDigest(ownerSubjectId);
                return objectKey.contains("/" + owner + "/");
            }

            @Override
            public String presignUpload(String objectKey, long size, String contentType, Duration ttl) {
                try {
                    return s3Client.generateUploadPresignedUrl(ticketProperties.getAttachment().getBucket(),
                            objectKey, ttl.getSeconds(), contentType);
                } catch (Exception e) {
                    throw new TicketServiceException(e);
                }
            }

            @Override
            public String presignDownload(String objectKey, Duration ttl) {
                try {
                    return s3Client.generatePresignedUrl(ticketProperties.getAttachment().getBucket(),
                            objectKey, ttl.getSeconds());
                } catch (Exception e) {
                    throw new TicketServiceException(e);
                }
            }

            @Override
            public boolean exists(String objectKey) {
                return s3Client.doesObjectExist(ticketProperties.getAttachment().getBucket(), objectKey);
            }

            @Override
            public void delete(String objectKey) {
                s3Client.deleteObject(ticketProperties.getAttachment().getBucket(), objectKey);
            }
        };
    }

    private static String sanitize(String fileName) {
        return fileName == null ? "file" : fileName.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    private static String ownerDigest(String ownerSubjectId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ownerSubjectId.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new TicketServiceException(e);
        }
    }
}
