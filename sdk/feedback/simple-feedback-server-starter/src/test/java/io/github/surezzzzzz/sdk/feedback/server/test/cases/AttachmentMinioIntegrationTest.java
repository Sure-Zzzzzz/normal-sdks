package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.service.AttachmentStorageService;
import io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication;
import io.github.surezzzzzz.sdk.oss.s3.client.S3Client;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 附件链 MinIO 真件集成测试（规范 §12.9：不以 mock 替代 S3 兼容端点；上传→下载回读字节一致；
 * 服务端生成 objectKey；散形态=附件能力缺席 400）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleFeedbackServerTestApplication.class)
class AttachmentMinioIntegrationTest {

    private static final String BUCKET = "feedback-attachment-test";

    @Autowired
    private AttachmentStorageService storageService;

    @Autowired
    private ObjectProvider<S3Client> s3ClientProvider;

    /**
     * 幂等建桶（半年过期生命周期桶由部署侧决定，测试仅保证桶存在）。
     */
    @BeforeAll
    static void ensureBucket(@Autowired ObjectProvider<S3Client> s3ClientProvider) {
        s3ClientProvider.getIfAvailable().createS3Bucket(BUCKET);
    }

    @Test
    @DisplayName("上传→objectKey 服务端生成→下载回读字节一致；两次上传键不同")
    void testUploadDownloadRoundtrip() throws Exception {
        assertTrue(storageService.attachmentAvailable());
        byte[] payload = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        String submissionId = UUID.randomUUID().toString();

        String key1 = storageService.upload(submissionId, "image/png",
                new ByteArrayInputStream(payload), payload.length, BUCKET);
        String key2 = storageService.upload(submissionId, "image/png",
                new ByteArrayInputStream(payload), payload.length, BUCKET);
        log.info("输入: payloadSize={} submissionId={}", payload.length, submissionId);

        assertTrue(key1.startsWith("feedback/" + submissionId + "/"), "objectKey 应以 feedback/{submissionId}/ 开头");
        assertNotEquals(key1, key2, "两次上传应生成不同对象键");
        assertTrue(key1.endsWith(".png"));

        AttachmentStorageService.StoredObject stored = storageService.download(key1, BUCKET);
        try (InputStream content = stored.getContent()) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = content.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            assertArrayEquals(payload, output.toByteArray());
        }
        assertEquals(payload.length, stored.getContentLength());
        log.info("输出: 回读字节一致 keyPrefix=feedback 长度={}", stored.getContentLength());
    }

    @Test
    @DisplayName("散形态：S3Client 缺席时附件能力降级，上传/下载返回 400 附件未开放")
    void testScatteredModeDegradation() {
        org.springframework.beans.factory.support.DefaultListableBeanFactory emptyFactory =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        AttachmentStorageService scattered = new AttachmentStorageService(
                emptyFactory.getBeanProvider(S3Client.class));
        assertFalse(scattered.attachmentAvailable());
        SimpleFeedbackServerException uploadRejected = assertThrows(SimpleFeedbackServerException.class,
                () -> scattered.upload("any", "image/png", new ByteArrayInputStream(new byte[1]), 1L, BUCKET));
        assertEquals(ErrorCode.ATTACHMENT_NOT_ENABLED, uploadRejected.getErrorCode());
        SimpleFeedbackServerException downloadRejected = assertThrows(SimpleFeedbackServerException.class,
                () -> scattered.download("any-key", BUCKET));
        assertEquals(ErrorCode.ATTACHMENT_NOT_ENABLED, downloadRejected.getErrorCode());
        log.info("输出: 散形态上传/下载均返回 {}", ErrorCode.ATTACHMENT_NOT_ENABLED);
    }
}
