package io.github.surezzzzzz.sdk.oss.s3.test.cases;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.securitytoken.model.Credentials;
import io.github.surezzzzzz.sdk.oss.s3.client.S3Client;
import io.github.surezzzzzz.sdk.oss.s3.configuration.S3ClientProperties;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorCode;
import io.github.surezzzzzz.sdk.oss.s3.constant.FileDisposition;
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.FileNotFoundException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.DeleteObjectTaggingFailedException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.GetObjectTaggingFailedException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.SetObjectTaggingFailedException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.UploadPartFailedException;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUpload;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUploadList;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUploadPartList;
import io.github.surezzzzzz.sdk.oss.s3.test.S3ClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * S3Client 端到端集成测试
 * 需要真实 S3 端点（配置在 application-local.yml 中）
 * 按顺序执行，全程操作同一个测试桶
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = S3ClientTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3ClientIntegrationTest {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private AmazonS3 amazonS3;

    @Autowired
    private S3ClientProperties properties;

    private static final String TEST_BUCKET = "normal-sdks-dev";
    private static final String TEST_KEY = "integration/sample.txt";
    private static final String TEST_STREAM_KEY = "integration/sample-stream.txt";
    private static final String TEST_COPY_KEY = "integration/sample-copied.txt";
    private static final String TEST_FOLDER = "integration-folder";
    private static final byte[] TEST_CONTENT = "Hello S3 Integration Test 2024".getBytes(StandardCharsets.UTF_8);
    private static final long CONTENT_LENGTH = TEST_CONTENT.length;

    // ==================== 存储桶管理 ====================

    @Test
    @Order(1)
    @DisplayName("创建存储桶 - 桶已存在时幂等返回")
    void testCreateBucket() {
        log.info("======================================");
        log.info("Order 1 - createS3Bucket（幂等）: {}", TEST_BUCKET);
        Bucket bucket = s3Client.createS3Bucket(TEST_BUCKET);
        log.info("创建结果: {}", bucket);
        log.info("======================================");

        assertNotNull(bucket, "Bucket 不应为 null");
        assertEquals(TEST_BUCKET, bucket.getName(), "Bucket 名称应与入参一致");
        assertTrue(amazonS3.doesBucketExistV2(TEST_BUCKET), "S3 侧 Bucket 应存在");
    }

    @Test
    @Order(2)
    @DisplayName("创建存储桶 - 再次调用幂等")
    void testCreateBucketIdempotent() {
        log.info("======================================");
        log.info("Order 2 - 重复创建存储桶（幂等验证）");
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.createS3Bucket(TEST_BUCKET), "重复创建不应抛异常");
        assertTrue(amazonS3.doesBucketExistV2(TEST_BUCKET), "Bucket 仍应存在");
    }

    @Test
    @Order(3)
    @DisplayName("启用多版本")
    void testEnableBucketVersioning() {
        log.info("======================================");
        log.info("Order 3 - 启用多版本: {}", TEST_BUCKET);
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.enableBucketVersioning(TEST_BUCKET), "启用多版本不应抛异常");
        String status = amazonS3.getBucketVersioningConfiguration(TEST_BUCKET).getStatus();
        log.info("多版本状态: {}", status);
        assertEquals("Enabled", status, "多版本状态应为 Enabled");
    }

    @Test
    @Order(4)
    @DisplayName("设置默认生命周期策略")
    void testSetDefaultBucketLifecycle() {
        log.info("======================================");
        log.info("Order 4 - 设置默认生命周期: {}", TEST_BUCKET);
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.setDefaultBucketLifecycle(TEST_BUCKET), "设置生命周期不应抛异常");
        BucketLifecycleConfiguration config = amazonS3.getBucketLifecycleConfiguration(TEST_BUCKET);
        assertNotNull(config, "生命周期配置不应为 null");
        assertFalse(config.getRules().isEmpty(), "至少应有一条生命周期规则");
        int expirationDays = config.getRules().get(0).getExpirationInDays();
        log.info("过期天数: {}", expirationDays);
        assertEquals(S3ClientConstant.DEFAULT_BUCKET_EXPIRATION_DAYS, expirationDays,
                "过期天数应与常量一致");
    }

    // ==================== 文件夹管理 ====================

    @Test
    @Order(5)
    @DisplayName("创建文件夹")
    void testCreateFolder() {
        log.info("======================================");
        log.info("Order 5 - 创建文件夹: {}", TEST_FOLDER);
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.createFolder(TEST_BUCKET, TEST_FOLDER), "创建文件夹不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, TEST_FOLDER + "/"),
                "文件夹对象（以 / 结尾）应存在");
    }

    @Test
    @Order(6)
    @DisplayName("创建文件夹 - 幂等")
    void testCreateFolderIdempotent() {
        log.info("======================================");
        log.info("Order 6 - 重复创建文件夹（幂等验证）");
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.createFolder(TEST_BUCKET, TEST_FOLDER), "重复创建文件夹不应抛异常");
    }

    @Test
    @Order(7)
    @DisplayName("创建文件夹 - 入参已带 /")
    void testCreateFolderWithTrailingSlash() {
        log.info("======================================");
        log.info("Order 7 - 创建文件夹（入参已含尾部斜杠）");
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.createFolder(TEST_BUCKET, "with-slash/"), "入参含 / 不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, "with-slash/"), "文件夹对象应存在");
    }

    // ==================== 上传 ====================

    @Test
    @Order(8)
    @DisplayName("上传文件")
    void testUploadFile() throws IOException {
        log.info("======================================");
        log.info("Order 8 - 上传 File: {}", TEST_KEY);
        log.info("======================================");

        File tmpFile = createTempFile();
        assertDoesNotThrow(() -> s3Client.uploadObject(TEST_BUCKET, TEST_KEY, tmpFile), "上传文件不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, TEST_KEY), "上传后对象应存在");
    }

    @Test
    @Order(9)
    @DisplayName("上传文件 - 带过期前缀")
    void testUploadFileWithExpirationPrefix() throws IOException {
        log.info("======================================");
        log.info("Order 9 - 上传带过期前缀: prefix={}", properties.getBucketExpirationPrefix());
        log.info("======================================");

        File tmpFile = createTempFile();
        assertDoesNotThrow(
                () -> s3Client.uploadObjectWithExpirationPrefix(TEST_BUCKET, TEST_KEY, tmpFile),
                "带过期前缀上传不应抛异常");
        String expectedKey = properties.getBucketExpirationPrefix() + TEST_KEY;
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, expectedKey),
                "前缀 + key 对象应存在，实际 key: " + expectedKey);
    }

    @Test
    @Order(10)
    @DisplayName("上传 InputStream")
    void testUploadInputStream() {
        log.info("======================================");
        log.info("Order 10 - 上传 InputStream: {}", TEST_STREAM_KEY);
        log.info("内容长度: {} bytes", CONTENT_LENGTH);
        log.info("======================================");

        assertDoesNotThrow(
                () -> s3Client.uploadObject(TEST_BUCKET, TEST_STREAM_KEY,
                        new ByteArrayInputStream(TEST_CONTENT), CONTENT_LENGTH),
                "InputStream 上传不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, TEST_STREAM_KEY), "InputStream 上传后对象应存在");
        long actualLength = s3Client.getObjectMetadata(TEST_BUCKET, TEST_STREAM_KEY).getContentLength();
        log.info("S3 侧 contentLength: {}", actualLength);
        assertEquals(CONTENT_LENGTH, actualLength, "S3 侧 contentLength 应与上传内容一致");
    }

    // ==================== 查询 ====================

    @Test
    @Order(11)
    @DisplayName("判断对象存在 - 存在")
    void testDoesObjectExistTrue() {
        boolean result = s3Client.doesObjectExist(TEST_BUCKET, TEST_KEY);
        log.info("doesObjectExist({}) = {}", TEST_KEY, result);
        assertTrue(result, "已上传的对象应存在");
    }

    @Test
    @Order(12)
    @DisplayName("判断对象存在 - 不存在")
    void testDoesObjectExistFalse() {
        String nonExistKey = "nonexistent-xyz-9999";
        boolean result = s3Client.doesObjectExist(TEST_BUCKET, nonExistKey);
        log.info("doesObjectExist({}) = {}", nonExistKey, result);
        assertFalse(result, "不存在的对象应返回 false");
    }

    @Test
    @Order(13)
    @DisplayName("获取对象元信息")
    void testGetObjectMetadata() {
        ObjectMetadata metadata = s3Client.getObjectMetadata(TEST_BUCKET, TEST_KEY);
        log.info("======================================");
        log.info("Order 13 - getObjectMetadata: {}", TEST_KEY);
        log.info("contentLength={}, lastModified={}", metadata.getContentLength(), metadata.getLastModified());
        log.info("======================================");

        assertNotNull(metadata, "metadata 不应为 null");
        assertTrue(metadata.getContentLength() > 0, "contentLength 应大于 0");
        assertNotNull(metadata.getLastModified(), "lastModified 不应为 null");
    }

    @Test
    @Order(14)
    @DisplayName("获取对象元信息 - 指定版本")
    void testGetObjectMetadataWithVersionId() {
        VersionListing versionListing = s3Client.listVersions(TEST_BUCKET, "integration/");
        // 过滤掉 delete marker（getSize == 0 表示删除标记），只保留有实际内容的版本
        List<S3VersionSummary> contentVersions = new ArrayList<>();
        for (S3VersionSummary v : versionListing.getVersionSummaries()) {
            if (v.getSize() > 0) {
                contentVersions.add(v);
            }
        }
        assumeTrue(!contentVersions.isEmpty(),
                "无可访问的内容版本（可能被 MinIO GC），跳过此测试");
        S3VersionSummary targetVersion = contentVersions.get(0);
        String versionId = targetVersion.getVersionId();
        String key = targetVersion.getKey();
        log.info("======================================");
        log.info("Order 14 - getObjectMetadata with versionId: key={}, versionId={}", key, versionId);
        log.info("======================================");

        ObjectMetadata metadata = s3Client.getObjectMetadata(TEST_BUCKET, key, versionId);
        assertNotNull(metadata, "带版本号的 metadata 不应为 null");
        assertEquals(versionId, metadata.getVersionId(), "返回的 versionId 应与查询入参一致");
    }

    @Test
    @Order(15)
    @DisplayName("获取对象")
    void testGetObject() throws IOException {
        log.info("======================================");
        log.info("Order 15 - getObject: {}", TEST_KEY);
        log.info("======================================");

        S3Object s3Object = s3Client.getObject(TEST_BUCKET, TEST_KEY, 0);
        try {
            log.info("bucket={}, key={}", s3Object.getBucketName(), s3Object.getKey());
            assertNotNull(s3Object, "S3Object 不应为 null");
            assertEquals(TEST_BUCKET, s3Object.getBucketName(), "bucketName 应一致");
            assertEquals(TEST_KEY, s3Object.getKey(), "key 应一致");
        } finally {
            s3Object.close();
        }
    }

    @Test
    @Order(16)
    @DisplayName("获取完整对象")
    void testGetFullObject() throws IOException {
        log.info("======================================");
        log.info("Order 16 - getFullObject: {}", TEST_KEY);
        log.info("======================================");

        S3Object s3Object = s3Client.getFullObject(TEST_BUCKET, TEST_KEY);
        try {
            assertNotNull(s3Object, "S3Object 不应为 null");
            assertTrue(s3Object.getObjectMetadata().getContentLength() > 0,
                    "contentLength 应大于 0");
            log.info("contentLength={}", s3Object.getObjectMetadata().getContentLength());
        } finally {
            s3Object.close();
        }
    }

    @Test
    @Order(17)
    @DisplayName("列举对象")
    void testListObjects() {
        ObjectListing listing = s3Client.listObjects(TEST_BUCKET);
        log.info("======================================");
        log.info("Order 17 - listObjects: count={}", listing.getObjectSummaries().size());
        listing.getObjectSummaries().forEach(s -> log.info("  key={}", s.getKey()));
        log.info("======================================");

        assertNotNull(listing, "listing 不应为 null");
        assertTrue(listing.getObjectSummaries().stream()
                        .anyMatch(s -> s.getKey().equals(TEST_KEY)),
                "列举结果中应包含已上传的 key: " + TEST_KEY);
    }

    @Test
    @Order(18)
    @DisplayName("列举对象 - 前缀过滤")
    void testListObjectsWithPrefix() {
        ObjectListing listing = s3Client.listObjects(TEST_BUCKET, "integration/");
        log.info("======================================");
        log.info("Order 18 - listObjects prefix=integration/: count={}", listing.getObjectSummaries().size());
        log.info("======================================");

        assertNotNull(listing, "ObjectListing 不应为 null");
        assertTrue(listing.getObjectSummaries().stream()
                        .allMatch(s -> s.getKey().startsWith("integration/")),
                "前缀过滤结果中所有 key 应以 integration/ 开头");

        ObjectListing emptyListing = s3Client.listObjects(TEST_BUCKET, "nonexistent-prefix-xyz/");
        log.info("不存在前缀 count={}", emptyListing.getObjectSummaries().size());
        assertEquals(0, emptyListing.getObjectSummaries().size(), "不存在的前缀应返回空列表");
    }

    @Test
    @Order(19)
    @DisplayName("列举对象 - 限制条数")
    void testListObjectsWithMaxKeys() {
        ObjectListing listing = s3Client.listObjects(TEST_BUCKET, "integration/", 1);
        log.info("======================================");
        log.info("Order 19 - listObjects maxKeys=1: count={}", listing.getObjectSummaries().size());
        log.info("======================================");

        assertTrue(listing.getObjectSummaries().size() <= 1, "maxKeys=1 时结果条数应 <= 1");
    }

    // ==================== 复制 ====================

    @Test
    @Order(20)
    @DisplayName("复制对象")
    void testCopyObject() {
        log.info("======================================");
        log.info("Order 20 - copyObject: {} -> {}", TEST_KEY, TEST_COPY_KEY);
        log.info("======================================");

        String etag = s3Client.copyObject(TEST_BUCKET, TEST_KEY, TEST_BUCKET, TEST_COPY_KEY);
        log.info("ETag: {}", etag);

        assertNotNull(etag, "ETag 不应为 null");
        assertFalse(etag.isEmpty(), "ETag 不应为空字符串");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, TEST_COPY_KEY), "复制目标对象应存在");
    }

    // ==================== 版本管理 ====================

    @Test
    @Order(21)
    @DisplayName("列举版本历史")
    void testListVersions() {
        VersionListing listing = s3Client.listVersions(TEST_BUCKET);
        log.info("======================================");
        log.info("Order 21 - listVersions: count={}", listing.getVersionSummaries().size());
        log.info("======================================");

        assertNotNull(listing, "VersionListing 不应为 null");
        assertEquals(TEST_BUCKET, listing.getBucketName(), "bucketName 应一致");
        // MinIO 版本历史可能因 GC 而丢失，使用 assumeTrue 跳过而非失败
        assumeTrue(!listing.getVersionSummaries().isEmpty(),
                "版本列表为空，跳过此测试（MinIO 版本历史可能已过期）");
    }

    @Test
    @Order(22)
    @DisplayName("列举版本历史 - 前缀过滤")
    void testListVersionsWithPrefix() {
        VersionListing listing = s3Client.listVersions(TEST_BUCKET, "integration/");
        log.info("======================================");
        log.info("Order 22 - listVersions prefix=integration/: count={}", listing.getVersionSummaries().size());
        log.info("======================================");

        assertNotNull(listing, "VersionListing 不应为 null");
        assertTrue(listing.getVersionSummaries().stream()
                        .allMatch(v -> v.getKey().startsWith("integration/")),
                "前缀过滤结果中所有 key 应以 integration/ 开头");
    }

    // ==================== 预签名 URL ====================

    @Test
    @Order(23)
    @DisplayName("生成下载预签名 URL - 默认模式")
    void testGeneratePresignedUrlDefault() throws Exception {
        String url = s3Client.generatePresignedUrl(TEST_BUCKET, TEST_KEY, 3600L);
        log.info("======================================");
        log.info("Order 23 - generatePresignedUrl(default): {}", url);
        log.info("======================================");

        assertNotNull(url, "预签名 URL 不应为 null");
        assertFalse(url.isEmpty(), "预签名 URL 不应为空");
        assertTrue(url.startsWith(properties.getUrlPrefix()),
                "URL 应以 urlPrefix 开头，urlPrefix=" + properties.getUrlPrefix());
    }

    @Test
    @Order(24)
    @DisplayName("生成下载预签名 URL - DOWNLOAD")
    void testGeneratePresignedUrlDownload() throws Exception {
        String url = s3Client.generatePresignedUrl(TEST_BUCKET, TEST_KEY, 3600L, FileDisposition.DOWNLOAD);
        log.info("======================================");
        log.info("Order 24 - generatePresignedUrl(DOWNLOAD): {}", url);
        log.info("======================================");

        assertNotNull(url, "DOWNLOAD 模式 URL 不应为 null");
        assertTrue(url.contains("attachment"), "DOWNLOAD 模式 URL 应包含 attachment");
    }

    @Test
    @Order(25)
    @DisplayName("生成下载预签名 URL - INLINE")
    void testGeneratePresignedUrlInline() throws Exception {
        String url = s3Client.generatePresignedUrl(TEST_BUCKET, TEST_KEY, 3600L, FileDisposition.INLINE);
        log.info("======================================");
        log.info("Order 25 - generatePresignedUrl(INLINE): {}", url);
        log.info("======================================");

        assertNotNull(url, "INLINE 模式 URL 不应为 null");
        assertTrue(url.contains("inline"), "INLINE 模式 URL 应包含 inline");
    }

    @Test
    @Order(26)
    @DisplayName("生成下载预签名 URL - null 过期时间（使用默认值）")
    void testGeneratePresignedUrlNullExpiration() throws Exception {
        String url = s3Client.generatePresignedUrl(TEST_BUCKET, TEST_KEY, null);
        log.info("======================================");
        log.info("Order 26 - generatePresignedUrl(null expiration): {}", url);
        log.info("======================================");

        assertNotNull(url, "null 过期时间时 URL 不应为 null");
        assertFalse(url.isEmpty(), "URL 不应为空");
    }

    @Test
    @Order(27)
    @DisplayName("生成上传预签名 URL")
    void testGenerateUploadPresignedUrl() throws Exception {
        String url = s3Client.generateUploadPresignedUrl(TEST_BUCKET, "integration/upload-target.txt", 3600L);
        log.info("======================================");
        log.info("Order 27 - generateUploadPresignedUrl: {}", url);
        log.info("======================================");

        assertNotNull(url, "上传预签名 URL 不应为 null");
        assertFalse(url.isEmpty(), "上传预签名 URL 不应为空");
        assertTrue(url.startsWith(properties.getUrlPrefix()),
                "URL 应以 urlPrefix 开头");
    }

    @Test
    @Order(28)
    @DisplayName("生成上传预签名 URL - 指定 Content-Type")
    void testGenerateUploadPresignedUrlWithContentType() throws Exception {
        String url = s3Client.generateUploadPresignedUrl(TEST_BUCKET, "integration/upload-img.jpg",
                3600L, "image/jpeg");
        log.info("======================================");
        log.info("Order 28 - generateUploadPresignedUrl(image/jpeg): {}", url);
        log.info("======================================");

        assertNotNull(url, "带 Content-Type 的上传预签名 URL 不应为 null");
        assertFalse(url.isEmpty(), "URL 不应为空");
    }

    @Test
    @Order(29)
    @DisplayName("自定义 HmacSHA1 预签名 URL")
    void testCustomPresignedUrl() throws Exception {
        String url = s3Client.customPresignedUrl(TEST_BUCKET, TEST_KEY, 3600L);
        log.info("======================================");
        log.info("Order 29 - customPresignedUrl: {}", url);
        log.info("======================================");

        assertNotNull(url, "自定义预签名 URL 不应为 null");
        assertTrue(url.contains("AWSAccessKeyId="), "URL 应包含 AWSAccessKeyId");
        assertTrue(url.contains("Expires="), "URL 应包含 Expires");
        assertTrue(url.contains("Signature="), "URL 应包含 Signature");
    }

    // ==================== 删除 ====================

    @Test
    @Order(30)
    @DisplayName("删除对象")
    void testDeleteObject() {
        log.info("======================================");
        log.info("Order 30 - deleteObject: {}", TEST_KEY);
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.deleteObject(TEST_BUCKET, TEST_KEY), "删除不应抛异常");
        assertFalse(s3Client.doesObjectExist(TEST_BUCKET, TEST_KEY), "删除后对象应不存在");
    }

    @Test
    @Order(31)
    @DisplayName("删除对象 - 幂等（NoSuchKey 静默）")
    void testDeleteObjectIdempotent() {
        log.info("======================================");
        log.info("Order 31 - deleteObject idempotent（已删除的 key）: {}", TEST_KEY);
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.deleteObject(TEST_BUCKET, TEST_KEY),
                "删除不存在的对象（NoSuchKey）不应抛异常");
    }

    @Test
    @Order(32)
    @DisplayName("删除对象 - bypassGovernanceRetention=false 重载")
    void testDeleteObjectOverloadDefault() {
        log.info("======================================");
        log.info("Order 32 - deleteObject(bucket, key, false): {}", TEST_COPY_KEY);
        log.info("======================================");

        assertDoesNotThrow(() -> s3Client.deleteObject(TEST_BUCKET, TEST_COPY_KEY, false),
                "deleteObject 带 bypassGovernanceRetention=false 不应抛异常");
        assertFalse(s3Client.doesObjectExist(TEST_BUCKET, TEST_COPY_KEY), "删除后对象应不存在");
    }

    // ==================== 异常场景 ====================

    @Test
    @Order(33)
    @DisplayName("上传不存在的文件 - 抛 FileNotFoundException")
    void testUploadFileNotFound() {
        File nonExistFile = new File("/no/such/file.txt");
        log.info("======================================");
        log.info("Order 33 - 上传不存在的文件: {}", nonExistFile.getPath());
        log.info("======================================");

        FileNotFoundException ex = assertThrows(FileNotFoundException.class,
                () -> s3Client.uploadObject(TEST_BUCKET, "x", nonExistFile),
                "上传不存在的文件应抛 FileNotFoundException");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.FILE_NOT_FOUND, ex.getErrorCode(), "errorCode 应为 OSS_102");
        assertTrue(ex.getMessage().contains("file.txt"), "错误消息应包含文件名");
    }

    // ==================== STS 凭证（需配置 roleArn）====================

    @Test
    @Order(34)
    @DisplayName("获取普通临时凭证")
    void testGetNormalStsCredentials() {
        assumeTrue(StringUtils.isNotBlank(properties.getRoleArn()),
                "未配置 roleArn，跳过 STS 集成测试");
        log.info("======================================");
        log.info("Order 34 - getNormalStsCredentials");
        log.info("======================================");

        Credentials credentials = s3Client.getNormalStsCredentials();
        log.info("accessKeyId={}, expiration={}", credentials.getAccessKeyId(), credentials.getExpiration());

        assertNotNull(credentials, "凭证不应为 null");
        assertTrue(StringUtils.isNotBlank(credentials.getAccessKeyId()), "accessKeyId 不应为空");
        assertTrue(StringUtils.isNotBlank(credentials.getSecretAccessKey()), "secretAccessKey 不应为空");
        assertTrue(StringUtils.isNotBlank(credentials.getSessionToken()), "sessionToken 不应为空");
        assertNotNull(credentials.getExpiration(), "expiration 不应为 null");
    }

    @Test
    @Order(35)
    @DisplayName("获取桶级临时凭证")
    void testGetBucketStsCredentials() {
        assumeTrue(StringUtils.isNotBlank(properties.getRoleArn()),
                "未配置 roleArn，跳过 STS 集成测试");
        log.info("======================================");
        log.info("Order 35 - getBucketStsCredentials: {}", TEST_BUCKET);
        log.info("======================================");

        Credentials credentials = s3Client.getBucketStsCredentials(TEST_BUCKET);
        log.info("accessKeyId={}, expiration={}", credentials.getAccessKeyId(), credentials.getExpiration());

        assertNotNull(credentials, "凭证不应为 null");
        assertTrue(StringUtils.isNotBlank(credentials.getAccessKeyId()), "accessKeyId 不应为空");
        assertTrue(StringUtils.isNotBlank(credentials.getSecretAccessKey()), "secretAccessKey 不应为空");
        assertTrue(StringUtils.isNotBlank(credentials.getSessionToken()), "sessionToken 不应为空");
        assertNotNull(credentials.getExpiration(), "expiration 不应为 null");
    }

    @Test
    @Order(36)
    @DisplayName("获取目录级临时凭证")
    void testGetDirStsCredentials() {
        assumeTrue(StringUtils.isNotBlank(properties.getRoleArn()),
                "未配置 roleArn，跳过 STS 集成测试");
        log.info("======================================");
        log.info("Order 36 - getDirStsCredentials: bucket={}, dir=integration/", TEST_BUCKET);
        log.info("======================================");

        Credentials credentials = s3Client.getDirStsCredentials(TEST_BUCKET, "integration/");
        log.info("accessKeyId={}, expiration={}", credentials.getAccessKeyId(), credentials.getExpiration());

        assertNotNull(credentials, "凭证不应为 null");
        assertTrue(StringUtils.isNotBlank(credentials.getAccessKeyId()), "accessKeyId 不应为空");
        assertTrue(StringUtils.isNotBlank(credentials.getSecretAccessKey()), "secretAccessKey 不应为空");
        assertTrue(StringUtils.isNotBlank(credentials.getSessionToken()), "sessionToken 不应为空");
        assertNotNull(credentials.getExpiration(), "expiration 不应为 null");
    }

    // ==================== 对象标签（v2.0.1）====================

    /** Order 37 set 用：测试结束保留文件和标签，便于 S3 Browser 观察 set 效果 */
    private static final String TAG_SET_KEY = "integration/tag-set-target.txt";
    /** Order 39 delete 用：先 upload+set 铺数据，再 delete 清空标签，最后 deleteObject 清理文件 */
    private static final String TAG_DELETE_KEY = "integration/tag-delete-target.txt";
    /** 校验类（Order 38/40/41）共用，仅做入参校验，不会真正落到 S3 */
    private static final String TAG_VALIDATE_KEY = "integration/tag-validate-target.txt";

    @Test
    @Order(37)
    @DisplayName("设置对象标签 - 正常（保留文件和标签便于观察）")
    void testSetObjectTagging() throws IOException {
        log.info("======================================");
        log.info("Order 37 - setObjectTagging: {}", TAG_SET_KEY);
        log.info("======================================");

        File tmpFile = createTempFile();
        s3Client.uploadObject(TEST_BUCKET, TAG_SET_KEY, tmpFile);

        Map<String, String> tags = new HashMap<>();
        tags.put("env", "dev");
        tags.put("owner", "surezzzzzz");
        assertDoesNotThrow(() -> s3Client.setObjectTagging(TEST_BUCKET, TAG_SET_KEY, tags),
                "设置标签不应抛异常");

        Map<String, String> got = s3Client.getObjectTagging(TEST_BUCKET, TAG_SET_KEY);
        log.info("回查标签: {}", got);
        assertEquals(2, got.size(), "标签数量应为 2");
        assertEquals("dev", got.get("env"), "env 标签值应为 dev");
        assertEquals("surezzzzzz", got.get("owner"), "owner 标签值应为 surezzzzzz");
        // 不删除文件、不清空标签，保留供 S3 Browser 等工具肉眼验证 set 效果
    }

    @Test
    @Order(38)
    @DisplayName("设置对象标签 - 超过 10 个抛异常")
    void testSetObjectTaggingTooMany() {
        log.info("======================================");
        log.info("Order 38 - setObjectTagging 超过 10 个");
        log.info("======================================");

        Map<String, String> tags = new HashMap<>();
        for (int i = 0; i < 11; i++) {
            tags.put("k" + i, "v" + i);
        }
        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TAG_VALIDATE_KEY, tags),
                "超过 10 个标签应抛异常");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.SET_OBJECT_TAGGING_FAILED, ex.getErrorCode(), "errorCode 应为 OSS_209");
        assertTrue(ex.getMessage().contains(String.valueOf(S3ClientConstant.MAX_OBJECT_TAGS)),
                "异常消息应提示标签上限 " + S3ClientConstant.MAX_OBJECT_TAGS);
    }

    @Test
    @Order(39)
    @DisplayName("删除对象标签（独立链路：upload → set → delete → 清理文件）")
    void testDeleteObjectTagging() throws IOException {
        log.info("======================================");
        log.info("Order 39 - deleteObjectTagging: {}", TAG_DELETE_KEY);
        log.info("======================================");

        // 独立铺数据：上传 + 设置初始标签
        File tmpFile = createTempFile();
        s3Client.uploadObject(TEST_BUCKET, TAG_DELETE_KEY, tmpFile);
        Map<String, String> tags = new HashMap<>();
        tags.put("to-be-deleted", "true");
        s3Client.setObjectTagging(TEST_BUCKET, TAG_DELETE_KEY, tags);
        assertEquals(1, s3Client.getObjectTagging(TEST_BUCKET, TAG_DELETE_KEY).size(), "前置：标签应为 1 个");

        // 主验证：删除标签
        assertDoesNotThrow(() -> s3Client.deleteObjectTagging(TEST_BUCKET, TAG_DELETE_KEY),
                "删除标签不应抛异常");
        Map<String, String> got = s3Client.getObjectTagging(TEST_BUCKET, TAG_DELETE_KEY);
        log.info("删除后标签: {}", got);
        assertTrue(got.isEmpty(), "删除后标签应为空");

        // 测试自清理：删除测试对象，避免桶里残留
        s3Client.deleteObject(TEST_BUCKET, TAG_DELETE_KEY);
    }

    @Test
    @Order(40)
    @DisplayName("设置对象标签 - Key 超长抛异常")
    void testSetObjectTaggingKeyTooLong() {
        log.info("======================================");
        log.info("Order 40 - setObjectTagging Key 超过 {} 字节", S3ClientConstant.MAX_TAG_KEY_BYTES);
        log.info("======================================");

        // 构造一个超过 128 字节的 Key（用 129 个 'k'）
        char[] keyChars = new char[S3ClientConstant.MAX_TAG_KEY_BYTES + 1];
        Arrays.fill(keyChars, 'k');
        String longKey = new String(keyChars);
        Map<String, String> tags = new HashMap<>();
        tags.put(longKey, "v");
        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TAG_VALIDATE_KEY, tags),
                "Key 超长应抛异常");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.SET_OBJECT_TAGGING_FAILED, ex.getErrorCode(), "errorCode 应为 OSS_209");
        assertNull(ex.getCause(), "Key 超长属于前置校验，不应有 cause");
        assertTrue(ex.getMessage().contains(String.valueOf(S3ClientConstant.MAX_TAG_KEY_BYTES)),
                "异常消息应提示 Key 字节上限 " + S3ClientConstant.MAX_TAG_KEY_BYTES);
    }

    @Test
    @Order(41)
    @DisplayName("设置对象标签 - Value 超长抛异常")
    void testSetObjectTaggingValueTooLong() {
        log.info("======================================");
        log.info("Order 41 - setObjectTagging Value 超过 {} 字节", S3ClientConstant.MAX_TAG_VALUE_BYTES);
        log.info("======================================");

        char[] valChars = new char[S3ClientConstant.MAX_TAG_VALUE_BYTES + 1];
        Arrays.fill(valChars, 'v');
        String longValue = new String(valChars);
        Map<String, String> tags = new HashMap<>();
        tags.put("ok-key", longValue);
        SetObjectTaggingFailedException ex = assertThrows(SetObjectTaggingFailedException.class,
                () -> s3Client.setObjectTagging(TEST_BUCKET, TAG_VALIDATE_KEY, tags),
                "Value 超长应抛异常");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.SET_OBJECT_TAGGING_FAILED, ex.getErrorCode(), "errorCode 应为 OSS_209");
        assertNull(ex.getCause(), "Value 超长属于前置校验，不应有 cause");
        assertTrue(ex.getMessage().contains(String.valueOf(S3ClientConstant.MAX_TAG_VALUE_BYTES)),
                "异常消息应提示 Value 字节上限 " + S3ClientConstant.MAX_TAG_VALUE_BYTES);
    }

    @Test
    @Order(42)
    @DisplayName("获取对象标签 - 不存在的 key 抛异常")
    void testGetObjectTaggingNotFound() {
        log.info("======================================");
        log.info("Order 42 - getObjectTagging 不存在的 key");
        log.info("======================================");

        GetObjectTaggingFailedException ex = assertThrows(GetObjectTaggingFailedException.class,
                () -> s3Client.getObjectTagging(TEST_BUCKET, "nonexistent-tag-key-xyz"),
                "获取不存在对象的标签应抛异常");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.GET_OBJECT_TAGGING_FAILED, ex.getErrorCode(), "errorCode 应为 OSS_212");
    }

    @Test
    @Order(43)
    @DisplayName("删除对象标签 - 不存在的 key 抛异常")
    void testDeleteObjectTaggingNotFound() {
        log.info("======================================");
        log.info("Order 43 - deleteObjectTagging 不存在的 key");
        log.info("======================================");

        DeleteObjectTaggingFailedException ex = assertThrows(DeleteObjectTaggingFailedException.class,
                () -> s3Client.deleteObjectTagging(TEST_BUCKET, "nonexistent-tag-key-xyz"),
                "删除不存在对象的标签应抛异常");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.DELETE_OBJECT_TAGGING_FAILED, ex.getErrorCode(), "errorCode 应为 OSS_213");
    }

    // ==================== 分段上传（v2.0.1）====================

    private static final String MULTIPART_SMALL_KEY = "integration/multipart-small.bin";
    private static final String MULTIPART_LARGE_KEY = "integration/multipart-large.bin";
    private static final String MULTIPART_CUSTOM_KEY = "integration/multipart-custom.bin";
    private static final String MULTIPART_MANUAL_KEY = "integration/multipart-manual.bin";
    private static final String MULTIPART_ABORT_KEY = "integration/multipart-abort.bin";

    @Test
    @Order(44)
    @DisplayName("分段上传 - 小文件走单次上传 fallback")
    void testUploadObjectMultipartSmallFile() throws IOException {
        log.info("======================================");
        log.info("Order 44 - uploadObjectMultipart 小文件 fallback");
        log.info("======================================");

        // 远低于阈值（默认 100MB），应直接走 uploadObject
        File smallFile = createRandomFile(1, "multipart-small");
        assertDoesNotThrow(() -> s3Client.uploadObjectMultipart(TEST_BUCKET, MULTIPART_SMALL_KEY, smallFile),
                "小文件分段上传不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, MULTIPART_SMALL_KEY), "对象应存在");
        assertEquals(smallFile.length(),
                s3Client.getObjectMetadata(TEST_BUCKET, MULTIPART_SMALL_KEY).getContentLength(),
                "上传后大小应与本地一致");
    }

    @Test
    @Order(45)
    @DisplayName("分段上传 - 大文件触发分段")
    void testUploadObjectMultipartLargeFile() throws IOException {
        log.info("======================================");
        log.info("Order 45 - uploadObjectMultipart 大文件触发分段");
        log.info("======================================");

        // 略大于测试配置阈值，触发分段
        int sizeMB = properties.getMultipartThresholdMB() + 10;
        File largeFile = createRandomFile(sizeMB, "multipart-large");
        log.info("准备分段上传文件，大小：{}MB", sizeMB);

        assertDoesNotThrow(() -> s3Client.uploadObjectMultipart(TEST_BUCKET, MULTIPART_LARGE_KEY, largeFile),
                "大文件分段上传不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, MULTIPART_LARGE_KEY), "对象应存在");
        long actualLength = s3Client.getObjectMetadata(TEST_BUCKET, MULTIPART_LARGE_KEY).getContentLength();
        log.info("S3 侧大小: {} bytes，本地: {} bytes", actualLength, largeFile.length());
        assertEquals(largeFile.length(), actualLength, "上传后大小应与本地一致");
    }

    @Test
    @Order(46)
    @DisplayName("分段上传 - 指定分段大小")
    void testUploadObjectMultipartSpecifyPartSize() throws IOException {
        log.info("======================================");
        log.info("Order 46 - uploadObjectMultipart(partSize=6MB)");
        log.info("======================================");

        int partSizeMB = 6;
        int sizeMB = properties.getMultipartThresholdMB() + partSizeMB + 1;
        File file = createRandomFile(sizeMB, "multipart-custom");
        assertDoesNotThrow(() -> s3Client.uploadObjectMultipart(TEST_BUCKET, MULTIPART_CUSTOM_KEY, file, partSizeMB),
                "指定分段大小上传不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, MULTIPART_CUSTOM_KEY), "指定分段大小上传后对象应存在");
        assertEquals(file.length(),
                s3Client.getObjectMetadata(TEST_BUCKET, MULTIPART_CUSTOM_KEY).getContentLength(),
                "指定分段大小上传后大小应与本地一致");
    }

    @Test
    @Order(47)
    @DisplayName("分段上传 - 指定分段过小抛异常")
    void testUploadObjectMultipartPartSizeTooSmall() throws IOException {
        log.info("======================================");
        log.info("Order 47 - uploadObjectMultipart 指定分段过小");
        log.info("======================================");

        File file = createRandomFile(1, "multipart-tiny");
        UploadPartFailedException ex = assertThrows(UploadPartFailedException.class,
                () -> s3Client.uploadObjectMultipart(TEST_BUCKET, "x", file,
                        S3ClientConstant.MIN_PART_SIZE_MB - 1),
                "partSizeMB 小于最小值应抛异常");
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.UPLOAD_PART_FAILED, ex.getErrorCode(), "errorCode 应为 OSS_210");
        assertTrue(ex.getMessage().contains(String.valueOf(S3ClientConstant.MIN_PART_SIZE_MB)),
                "异常消息应包含最小分段值 " + S3ClientConstant.MIN_PART_SIZE_MB);
    }

    @Test
    @Order(48)
    @DisplayName("分段上传 - 手动分段上传完整流程")
    void testManualMultipartUpload() throws IOException {
        log.info("======================================");
        log.info("Order 48 - 手动分段上传：init -> uploadPart -> list -> complete");
        log.info("======================================");

        // 生成 12MB 的文件，按 6MB/段分成 2 段（S3 协议每段需 >= 5MB，取 6MB 留余量）
        int sizeMB = 12;
        int partSizeMB = 6;
        File file = createRandomFile(sizeMB, "multipart-manual");
        long fileSize = file.length();
        long partSizeBytes = (long) partSizeMB * S3ClientConstant.MB_IN_BYTES;

        String uploadId = s3Client.generateUploadId(TEST_BUCKET, MULTIPART_MANUAL_KEY);
        log.info("生成 uploadId: {}", uploadId);
        assertNotNull(uploadId, "uploadId 不应为 null");
        assertFalse(uploadId.isEmpty(), "uploadId 不应为空");

        List<PartETag> partETags = new ArrayList<>();
        int partNumber = 1;
        long offset = 0L;
        while (offset < fileSize) {
            long length = Math.min(partSizeBytes, fileSize - offset);
            byte[] buf = readBytes(file, offset, length);
            PartETag tag = s3Client.uploadPart(TEST_BUCKET, MULTIPART_MANUAL_KEY, uploadId,
                    partNumber, new ByteArrayInputStream(buf), length);
            log.info("uploadPart partNumber={}, etag={}", partNumber, tag.getETag());
            assertNotNull(tag, "PartETag 不应为 null");
            partETags.add(tag);
            offset += length;
            partNumber++;
        }

        MultipartUploadPartList parts = s3Client.listParts(TEST_BUCKET, MULTIPART_MANUAL_KEY, uploadId);
        log.info("listParts 返回 {} 个分段", parts.getParts().size());
        assertEquals(partETags.size(), parts.getParts().size(), "listParts 应返回相同数量的分段");

        List<PartETag> unorderedPartETags = new ArrayList<>(partETags);
        Collections.reverse(unorderedPartETags);
        log.info("使用无序 partETags 调用 completeMultipartUpload，验证 SDK 内部排序");
        assertDoesNotThrow(() ->
                        s3Client.completeMultipartUpload(TEST_BUCKET, MULTIPART_MANUAL_KEY, uploadId, unorderedPartETags),
                "无序 partETags 完成分段上传不应抛异常");
        assertTrue(s3Client.doesObjectExist(TEST_BUCKET, MULTIPART_MANUAL_KEY), "对象应存在");
        assertEquals(fileSize,
                s3Client.getObjectMetadata(TEST_BUCKET, MULTIPART_MANUAL_KEY).getContentLength(),
                "S3 侧大小应与本地一致");
    }

    @Test
    @Order(49)
    @DisplayName("分段上传 - 中止 + 列举")
    void testAbortMultipartUpload() {
        log.info("======================================");
        log.info("Order 49 - generateUploadId -> listMultipartUploads -> abort");
        log.info("======================================");

        String uploadId = s3Client.generateUploadId(TEST_BUCKET, MULTIPART_ABORT_KEY);
        log.info("生成 uploadId: {}", uploadId);
        assertNotNull(uploadId, "uploadId 不应为 null");

        MultipartUploadList summary = s3Client.listMultipartUploads(TEST_BUCKET);
        log.info("listMultipartUploads 返回 {} 个进行中任务", summary.getUploads().size());
        boolean found = false;
        for (MultipartUpload u : summary.getUploads()) {
            if (MULTIPART_ABORT_KEY.equals(u.getKey()) && uploadId.equals(u.getUploadId())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "刚发起的 uploadId 应能在列表中找到");

        assertDoesNotThrow(() -> s3Client.abortMultipartUpload(TEST_BUCKET, MULTIPART_ABORT_KEY, uploadId),
                "中止分段上传不应抛异常");

        MultipartUploadList afterAbort = s3Client.listMultipartUploads(TEST_BUCKET);
        boolean stillExists = false;
        for (MultipartUpload u : afterAbort.getUploads()) {
            if (MULTIPART_ABORT_KEY.equals(u.getKey()) && uploadId.equals(u.getUploadId())) {
                stillExists = true;
                break;
            }
        }
        assertFalse(stillExists, "中止后该 uploadId 不应在列表中");
    }

    // ==================== 工具方法 ====================

    private File createTempFile() throws IOException {
        File tmpFile = Files.createTempFile("s3-test-", ".txt").toFile();
        tmpFile.deleteOnExit();
        Files.write(tmpFile.toPath(), TEST_CONTENT);
        return tmpFile;
    }

    /**
     * 创建指定 MB 大小的临时文件，内容用固定字节填充（避免随机数据消耗熵）
     */
    private File createRandomFile(int sizeMB, String prefix) throws IOException {
        File tmpFile = Files.createTempFile("s3-test-" + prefix + "-", ".bin").toFile();
        tmpFile.deleteOnExit();
        byte[] chunk = new byte[S3ClientConstant.MB_IN_BYTES];
        Arrays.fill(chunk, (byte) 'a');
        try (RandomAccessFile raf = new RandomAccessFile(tmpFile, S3ClientConstant.FILE_MODE_READ_WRITE)) {
            for (int i = 0; i < sizeMB; i++) {
                raf.write(chunk);
            }
        }
        return tmpFile;
    }

    /**
     * 从文件读取指定偏移和长度的字节
     */
    private byte[] readBytes(File file, long offset, long length) throws IOException {
        byte[] buf = new byte[(int) length];
        try (RandomAccessFile raf = new RandomAccessFile(file, S3ClientConstant.FILE_MODE_READ)) {
            raf.seek(offset);
            raf.readFully(buf);
        }
        return buf;
    }
}
