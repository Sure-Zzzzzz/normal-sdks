package io.github.surezzzzzz.sdk.oss.s3.test.cases;

import io.github.surezzzzzz.sdk.oss.s3.client.S3Client;
import io.github.surezzzzzz.sdk.oss.s3.constant.FileDisposition;
import io.github.surezzzzzz.sdk.oss.s3.support.ContentTypeHelper;
import io.github.surezzzzzz.sdk.oss.s3.support.DateTimeHelper;
import io.github.surezzzzzz.sdk.oss.s3.test.S3ClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3Client 支持层纯逻辑测试
 * 不依赖真实 S3 端点，覆盖 ContentTypeHelper / FileDisposition / DateTimeHelper / 工具方法
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = S3ClientTestApplication.class)
class S3ClientSupportTest {

    @Autowired
    private S3Client s3Client;

    // ==================== ContentTypeHelper ====================

    @Test
    @DisplayName("ContentTypeHelper - jpeg")
    void testContentTypeJpeg() {
        String result = ContentTypeHelper.getContentType("photo.jpg");
        log.info("getContentType(photo.jpg) = {}", result);
        assertEquals("image/jpeg", result, "jpg 应返回 image/jpeg");
    }

    @Test
    @DisplayName("ContentTypeHelper - pdf")
    void testContentTypePdf() {
        String result = ContentTypeHelper.getContentType("doc.pdf");
        log.info("getContentType(doc.pdf) = {}", result);
        assertEquals("application/pdf", result, "pdf 应返回 application/pdf");
    }

    @Test
    @DisplayName("ContentTypeHelper - docx")
    void testContentTypeDocx() {
        String result = ContentTypeHelper.getContentType("report.docx");
        log.info("getContentType(report.docx) = {}", result);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result, "docx 应返回 word MIME 类型");
    }

    @Test
    @DisplayName("ContentTypeHelper - mp4")
    void testContentTypeMp4() {
        String result = ContentTypeHelper.getContentType("video.mp4");
        log.info("getContentType(video.mp4) = {}", result);
        assertEquals("video/mp4", result, "mp4 应返回 video/mp4");
    }

    @Test
    @DisplayName("ContentTypeHelper - 未知扩展名")
    void testContentTypeUnknownExtension() {
        String result = ContentTypeHelper.getContentType("archive.xyz");
        log.info("getContentType(archive.xyz) = {}", result);
        assertEquals("application/octet-stream", result, "未知扩展名应返回 application/octet-stream");
    }

    @Test
    @DisplayName("ContentTypeHelper - 无扩展名")
    void testContentTypeNoExtension() {
        String result = ContentTypeHelper.getContentType("README");
        log.info("getContentType(README) = {}", result);
        assertEquals("application/octet-stream", result, "无扩展名应返回 application/octet-stream");
    }

    @Test
    @DisplayName("ContentTypeHelper - null")
    void testContentTypeNull() {
        String result = ContentTypeHelper.getContentType(null);
        log.info("getContentType(null) = {}", result);
        assertEquals("application/octet-stream", result, "null 应返回 application/octet-stream");
    }

    // ==================== FileDisposition ====================

    @Test
    @DisplayName("FileDisposition.fromCode - DOWNLOAD")
    void testFileDispositionFromCodeDownload() {
        FileDisposition result = FileDisposition.fromCode("DOWNLOAD");
        log.info("fromCode(DOWNLOAD) = {}", result);
        assertEquals(FileDisposition.DOWNLOAD, result, "DOWNLOAD 字符串应返回 DOWNLOAD 枚举");
    }

    @Test
    @DisplayName("FileDisposition.fromCode - INLINE")
    void testFileDispositionFromCodeInline() {
        FileDisposition result = FileDisposition.fromCode("INLINE");
        log.info("fromCode(INLINE) = {}", result);
        assertEquals(FileDisposition.INLINE, result, "INLINE 字符串应返回 INLINE 枚举");
    }

    @Test
    @DisplayName("FileDisposition.fromCode - 大小写不敏感")
    void testFileDispositionFromCodeCaseInsensitive() {
        FileDisposition result = FileDisposition.fromCode("download");
        log.info("fromCode(download) = {}", result);
        assertEquals(FileDisposition.DOWNLOAD, result, "小写 download 应返回 DOWNLOAD 枚举");
    }

    @Test
    @DisplayName("FileDisposition.fromCode - null")
    void testFileDispositionFromCodeNull() {
        FileDisposition result = FileDisposition.fromCode(null);
        log.info("fromCode(null) = {}", result);
        assertNull(result, "null 应返回 null");
    }

    @Test
    @DisplayName("FileDisposition.fromCode - 无效值")
    void testFileDispositionFromCodeInvalid() {
        FileDisposition result = FileDisposition.fromCode("invalid");
        log.info("fromCode(invalid) = {}", result);
        assertNull(result, "无效值应返回 null");
    }

    @Test
    @DisplayName("FileDisposition.isValid - 有效")
    void testFileDispositionIsValid() {
        boolean result = FileDisposition.isValid("DOWNLOAD");
        log.info("isValid(DOWNLOAD) = {}", result);
        assertTrue(result, "DOWNLOAD 应为有效值");
    }

    @Test
    @DisplayName("FileDisposition.isValid - 无效")
    void testFileDispositionIsInvalid() {
        boolean result = FileDisposition.isValid("bad");
        log.info("isValid(bad) = {}", result);
        assertFalse(result, "bad 应为无效值");
    }

    @Test
    @DisplayName("FileDisposition.getContentDisposition - DOWNLOAD")
    void testFileDispositionContentDispositionDownload() {
        String result = FileDisposition.DOWNLOAD.getContentDisposition("report.pdf");
        log.info("DOWNLOAD.getContentDisposition(report.pdf) = {}", result);
        assertEquals("attachment; filename=\"report.pdf\"", result, "DOWNLOAD disposition 格式不正确");
    }

    @Test
    @DisplayName("FileDisposition.getContentDisposition - INLINE")
    void testFileDispositionContentDispositionInline() {
        String result = FileDisposition.INLINE.getContentDisposition("img.png");
        log.info("INLINE.getContentDisposition(img.png) = {}", result);
        assertEquals("inline; filename=\"img.png\"", result, "INLINE disposition 格式不正确");
    }

    @Test
    @DisplayName("FileDisposition.toString")
    void testFileDispositionToString() {
        String result = FileDisposition.DOWNLOAD.toString();
        log.info("DOWNLOAD.toString() = {}", result);
        assertEquals("DOWNLOAD", result, "toString 应返回枚举名称");
    }

    // ==================== DateTimeHelper ====================

    @Test
    @DisplayName("DateTimeHelper.toOffsetDateTime - 非 null")
    void testDateTimeHelperNotNull() {
        Date date = new Date();
        OffsetDateTime result = DateTimeHelper.toOffsetDateTime(date);
        log.info("toOffsetDateTime({}) = {}", date, result);
        assertNotNull(result, "非 null Date 应返回非 null OffsetDateTime");
    }

    @Test
    @DisplayName("DateTimeHelper.toOffsetDateTime - 时刻一致")
    void testDateTimeHelperInstantConsistency() {
        Date date = new Date(1700000000000L);
        OffsetDateTime result = DateTimeHelper.toOffsetDateTime(date);
        log.info("toOffsetDateTime({}) = {}", date, result);
        assertNotNull(result);
        assertEquals(date.toInstant(), result.toInstant(), "转换前后 Instant 应一致");
    }

    @Test
    @DisplayName("DateTimeHelper.toOffsetDateTime - null 入参")
    void testDateTimeHelperNull() {
        OffsetDateTime result = DateTimeHelper.toOffsetDateTime(null);
        log.info("toOffsetDateTime(null) = {}", result);
        assertNull(result, "null Date 应返回 null");
    }

    // ==================== S3Client 工具方法 ====================

    @Test
    @DisplayName("generateBucketName - 32位纯小写十六进制")
    void testGenerateBucketName() {
        String result = s3Client.generateBucketName("x");
        log.info("generateBucketName(x) = {}", result);
        assertNotNull(result, "桶名不应为 null");
        assertEquals(32, result.length(), "桶名应为 32 位");
        assertTrue(result.matches("[0-9a-f]{32}"), "桶名应为纯小写十六进制（UUID 去除连字符）");
    }

    @Test
    @DisplayName("generateBucketName - 带前缀")
    void testGenerateBucketNameWithPrefix() {
        String prefix = "pfx-";
        String result = s3Client.generateBucketName(prefix, "x");
        log.info("generateBucketName({}, x) = {}", prefix, result);
        assertNotNull(result);
        assertTrue(result.startsWith(prefix), "桶名应以前缀开头");
        assertEquals(prefix.length() + 32, result.length(), "桶名总长度应为前缀长度 + 32");
    }
}
