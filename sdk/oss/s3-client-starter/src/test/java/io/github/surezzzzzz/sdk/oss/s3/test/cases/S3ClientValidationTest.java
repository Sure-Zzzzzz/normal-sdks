package io.github.surezzzzzz.sdk.oss.s3.test.cases;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.PartETag;
import io.github.surezzzzzz.sdk.oss.s3.client.S3Client;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import io.github.surezzzzzz.sdk.oss.s3.exception.base.S3ServerException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.CompleteMultipartUploadFailedException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.SetObjectTaggingFailedException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S3Client 参数前置校验测试
 * 不启动 Spring，不访问真实 S3，仅覆盖访问 AmazonS3 前的参数校验和本地排序行为
 *
 * @author surezzzzzz
 */
@Slf4j
class S3ClientValidationTest {

    private static final String TEST_BUCKET = "validation-bucket";
    private static final String TEST_KEY = "validation/object.txt";
    private static final String TEST_UPLOAD_ID = "validation-upload-id";

    @Test
    @DisplayName("setObjectTagging - tags 为 null")
    void testSetObjectTaggingNullTags() {
        S3Client s3Client = new S3Client();

        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TEST_KEY, null),
                "tags 为 null 应抛 SetObjectTaggingFailedException");

        assertValidationException(ex, ErrorCode.SET_OBJECT_TAGGING_FAILED, "标签");
    }

    @Test
    @DisplayName("setObjectTagging - Key 为 null")
    void testSetObjectTaggingNullKey() {
        S3Client s3Client = new S3Client();
        Map<String, String> tags = new HashMap<>();
        tags.put(null, "value");

        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TEST_KEY, tags),
                "null Key 应抛 SetObjectTaggingFailedException");

        assertValidationException(ex, ErrorCode.SET_OBJECT_TAGGING_FAILED, "Key");
    }

    @Test
    @DisplayName("setObjectTagging - Key 为空字符串")
    void testSetObjectTaggingEmptyKey() {
        S3Client s3Client = new S3Client();
        Map<String, String> tags = new HashMap<>();
        tags.put("", "value");

        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TEST_KEY, tags),
                "空 Key 应抛 SetObjectTaggingFailedException");

        assertValidationException(ex, ErrorCode.SET_OBJECT_TAGGING_FAILED, "Key");
    }

    @Test
    @DisplayName("setObjectTagging - Key 为空白字符串")
    void testSetObjectTaggingBlankKey() {
        S3Client s3Client = new S3Client();
        Map<String, String> tags = new HashMap<>();
        tags.put("   ", "value");

        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TEST_KEY, tags),
                "空白 Key 应抛 SetObjectTaggingFailedException");

        assertValidationException(ex, ErrorCode.SET_OBJECT_TAGGING_FAILED, "Key");
    }

    @Test
    @DisplayName("setObjectTagging - Value 为 null")
    void testSetObjectTaggingNullValue() {
        S3Client s3Client = new S3Client();
        Map<String, String> tags = new HashMap<>();
        tags.put("valid-key", null);

        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TEST_KEY, tags),
                "null Value 应抛 SetObjectTaggingFailedException");

        assertValidationException(ex, ErrorCode.SET_OBJECT_TAGGING_FAILED, "Value");
        assertTrue(ex.getMessage().contains("valid-key"), "异常消息应包含当前 Key");
    }

    @Test
    @DisplayName("completeMultipartUpload - partETags 为 null")
    void testCompleteMultipartUploadNullPartETags() {
        S3Client s3Client = new S3Client();

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, null),
                "partETags 为 null 应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "partETags");
        assertTrue(ex.getMessage().contains("null"), "null 列表的消息应包含 null");
        assertFalse(ex.getMessage().contains("包含"), "null 列表的消息不应含'包含'，避免与 null 元素场景混淆");
    }

    @Test
    @DisplayName("completeMultipartUpload - partETags 为空列表")
    void testCompleteMultipartUploadEmptyPartETags() {
        S3Client s3Client = new S3Client();

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, Collections.emptyList()),
                "partETags 为空应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "partETags");
    }

    @Test
    @DisplayName("completeMultipartUpload - partETags 包含 null 元素")
    void testCompleteMultipartUploadNullPartETag() {
        S3Client s3Client = new S3Client();
        List<PartETag> partETags = new ArrayList<>();
        partETags.add(null);

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags),
                "partETags 包含 null 元素应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "包含 null");
    }

    @Test
    @DisplayName("completeMultipartUpload - partNumber 小于 1")
    void testCompleteMultipartUploadInvalidPartNumber() {
        S3Client s3Client = new S3Client();
        List<PartETag> partETags = Collections.singletonList(new PartETag(0, "etag-0"));

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags),
                "partNumber 小于 1 应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "0");
    }

    @Test
    @DisplayName("completeMultipartUpload - partNumber 超过 10000")
    void testCompleteMultipartUploadPartNumberTooLarge() {
        S3Client s3Client = new S3Client();
        int partNumber = S3ClientConstant.MAX_MULTIPART_PARTS + 1;
        List<PartETag> partETags = Collections.singletonList(new PartETag(partNumber, "etag-too-large"));

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags),
                "partNumber 超过 10000 应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, String.valueOf(partNumber));
    }

    @Test
    @DisplayName("completeMultipartUpload - ETag 为空字符串")
    void testCompleteMultipartUploadEmptyETag() {
        S3Client s3Client = new S3Client();
        List<PartETag> partETags = Collections.singletonList(new PartETag(1, ""));

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags),
                "ETag 为空字符串应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "ETag");
    }

    @Test
    @DisplayName("completeMultipartUpload - ETag 为空白字符串")
    void testCompleteMultipartUploadBlankETag() {
        S3Client s3Client = new S3Client();
        List<PartETag> partETags = Collections.singletonList(new PartETag(1, "   "));

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags),
                "ETag 为空白字符串应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "ETag");
    }

    @Test
    @DisplayName("completeMultipartUpload - partNumber 重复")
    void testCompleteMultipartUploadDuplicatePartNumber() {
        S3Client s3Client = new S3Client();
        List<PartETag> partETags = Arrays.asList(
                new PartETag(1, "etag-1"),
                new PartETag(1, "etag-1-duplicate"));

        CompleteMultipartUploadFailedException ex = assertThrows(CompleteMultipartUploadFailedException.class,
                () -> s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags),
                "partNumber 重复应抛 CompleteMultipartUploadFailedException");

        assertValidationException(ex, ErrorCode.COMPLETE_MULTIPART_UPLOAD_FAILED, "1");
    }

    @Test
    @DisplayName("completeMultipartUpload - 无序 partETags 使用排序副本且不修改原列表")
    void testCompleteMultipartUploadUnorderedPartETagsSortedCopy() {
        S3Client s3Client = new S3Client();
        AmazonS3 amazonS3 = mock(AmazonS3.class);
        ReflectionTestUtils.setField(s3Client, "amazonS3", amazonS3);
        when(amazonS3.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(new CompleteMultipartUploadResult());

        List<PartETag> partETags = new ArrayList<>();
        partETags.add(new PartETag(2, "etag-2"));
        partETags.add(new PartETag(1, "etag-1"));

        s3Client.completeMultipartUpload(TEST_BUCKET, TEST_KEY, TEST_UPLOAD_ID, partETags);

        ArgumentCaptor<CompleteMultipartUploadRequest> requestCaptor =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(amazonS3).completeMultipartUpload(requestCaptor.capture());
        List<PartETag> submittedPartETags = requestCaptor.getValue().getPartETags();

        log.info("原始 partETags: {}, 提交 partETags: {}", partETags, submittedPartETags);
        assertEquals(2, partETags.get(0).getPartNumber(), "原始列表第一个 partNumber 不应被修改");
        assertEquals(1, partETags.get(1).getPartNumber(), "原始列表第二个 partNumber 不应被修改");
        assertEquals(1, submittedPartETags.get(0).getPartNumber(), "提交列表第一个 partNumber 应为 1");
        assertEquals(2, submittedPartETags.get(1).getPartNumber(), "提交列表第二个 partNumber 应为 2");
        assertNotSame(partETags, submittedPartETags, "提交列表应为排序副本，不应复用原始列表对象");
    }

    private void assertValidationException(S3ServerException ex, String expectedErrorCode, String messageKeyword) {
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(expectedErrorCode, ex.getErrorCode(), "errorCode 不符合预期");
        assertNull(ex.getCause(), "前置校验异常不应有 cause");
        assertTrue(ex.getMessage().contains(messageKeyword), "异常消息应包含关键字: " + messageKeyword);
    }
}
