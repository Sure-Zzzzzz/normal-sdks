package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.AttachmentRejectException;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackDependencyException;
import io.github.surezzzzzz.sdk.oss.s3.client.S3Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.io.InputStream;
import java.util.UUID;

/**
 * 附件存储门面（中间件一律走自家组件：对象存储经 s3-client-starter 的 S3Client，不用原生 SDK）。
 *
 * <p>聚散：S3Client Bean 缺席（散形态）时附件能力降级——带附件提交返回 400（ATTACHMENT_NOT_ENABLED），
 * 无附件提交不受影响；下载在散形态不可用（无附件行即无可下载项）。</p>
 *
 * <p>非直观约束：objectKey 由服务端生成（feedback/{submissionId}/{uuid}.{ext}），
 * 不接受调用方指定；上传时序=S3 先传、库后写（库失败时对象不可寻址，孤儿由半年生命周期桶回收）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RequiredArgsConstructor
public class AttachmentStorageService {

    private final ObjectProvider<S3Client> s3ClientProvider;

    private static String extensionOf(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return "jpg";
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        return "webp";
    }

    /**
     * 判断附件能力是否在场（S3Client Bean 存在且配置启用）。
     *
     * @return true=附件能力可用
     */
    public boolean attachmentAvailable() {
        return s3ClientProvider.getIfAvailable() != null;
    }

    /**
     * 上传附件字节流（经 S3Client；扩展名从白名单媒体类型映射，不信任原始文件名后缀）。
     *
     * @param submissionId  所属反馈 ID
     * @param contentType   白名单内媒体类型
     * @param inputStream   附件字节流
     * @param contentLength 字节长度
     * @param bucketName    目标桶
     * @return 服务端生成的 objectKey
     */
    public String upload(String submissionId, String contentType, InputStream inputStream, long contentLength,
                         String bucketName) {
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new AttachmentRejectException(ErrorCode.ATTACHMENT_NOT_ENABLED, ErrorMessage.ATTACHMENT_NOT_ENABLED);
        }
        String extension = extensionOf(contentType);
        String objectKey = String.format(SimpleFeedbackServerConstant.TEMPLATE_ATTACHMENT_STORAGE_KEY,
                submissionId, UUID.randomUUID().toString(), extension);
        long startNanos = System.nanoTime();
        try {
            s3Client.uploadObject(bucketName, objectKey, inputStream, contentLength);
        } catch (RuntimeException exception) {
            log.warn("附件上传失败 bucket存在性=配置项 存储键前缀=feedback 结果=SDK异常 耗时ms={}",
                    (System.nanoTime() - startNanos) / 1_000_000L);
            throw new FeedbackDependencyException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    ErrorMessage.DEPENDENCY_UNAVAILABLE, exception);
        }
        log.debug("附件上传完成 存储键前缀=feedback 大小字节={} 耗时ms={}", contentLength,
                (System.nanoTime() - startNanos) / 1_000_000L);
        return objectKey;
    }

    /**
     * 下载附件对象流（经 S3Client 代理；附件元数据由调用方先行鉴权；返回包装类型不暴露第三方 SDK 类型）。
     *
     * @param objectKey  存储键
     * @param bucketName 桶名
     * @return 存储对象（内容流+元数据；流由调用方负责关闭）
     */
    public StoredObject download(String objectKey, String bucketName) {
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new AttachmentRejectException(ErrorCode.ATTACHMENT_NOT_ENABLED, ErrorMessage.ATTACHMENT_NOT_ENABLED);
        }
        long startNanos = System.nanoTime();
        com.amazonaws.services.s3.model.S3Object object = null;
        try {
            object = s3Client.getFullObject(bucketName, objectKey);
            log.debug("附件取流完成 存储键前缀=feedback 耗时ms={}",
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return new StoredObject(object.getObjectContent(),
                    object.getObjectMetadata() == null ? null : object.getObjectMetadata().getContentType(),
                    object.getObjectMetadata() == null ? -1L : object.getObjectMetadata().getContentLength());
        } catch (RuntimeException exception) {
            log.warn("附件取流失败 存储键前缀=feedback 结果=SDK异常 耗时ms={}",
                    (System.nanoTime() - startNanos) / 1_000_000L);
            throw new FeedbackDependencyException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    ErrorMessage.DEPENDENCY_UNAVAILABLE, exception);
        }
    }

    /**
     * 存储对象包装值对象（不暴露第三方 SDK 类型，DESIGN §4）。
     */
    @lombok.Value
    public static class StoredObject {

        /**
         * 内容流（调用方负责关闭）
         */
        private final java.io.InputStream content;

        /**
         * 媒体类型（可空）
         */
        private final String contentType;

        /**
         * 内容长度（-1=未知）
         */
        private final long contentLength;
    }
}
