package io.github.surezzzzzz.sdk.s3.client.test.cases;

import com.amazonaws.HttpMethod;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.s3.client.configuration.SimpleS3ClientProperties;
import io.github.surezzzzzz.sdk.s3.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.client.exception.*;
import io.github.surezzzzzz.sdk.s3.client.model.MultipartUploadList;
import io.github.surezzzzzz.sdk.s3.client.model.MultipartUploadPartList;
import io.github.surezzzzzz.sdk.s3.client.model.S3Event;
import io.github.surezzzzzz.sdk.s3.client.template.S3ClientTemplate;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S3 Client 门面测试：语义方法委托与参数透传、自动 contentLength 两路、
 * 重试执行器接桥、断点续传 Range 语义、幂等语义（删除/中止/建桶/建文件夹）、
 * 分片 ETag 校验、tagging 参数校验、预签名 Disposition 与网关前缀、
 * STS 前置校验、异常翻译映射与参数校验。
 *
 * @author surezzzzzz
 */
@Slf4j
class S3ClientTemplateTest {

    private static final String TARGET = "test-main";

    private AmazonS3 client;
    private S3RouteTemplate routeTemplate;
    private TaskRetryExecutor retryExecutor;
    private SimpleS3RouteProperties routeProperties;
    private SimpleS3ClientProperties properties;
    private S3ClientTemplate template;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        client = mock(AmazonS3.class);
        routeTemplate = mock(S3RouteTemplate.class);
        retryExecutor = mock(TaskRetryExecutor.class);
        when(retryExecutor.executeWithFixedDelay(any(), anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    Callable<Object> task = invocation.getArgument(0);
                    return task.call();
                });
        when(routeTemplate.execute(anyString(), any()))
                .thenAnswer(invocation -> {
                    Function<AmazonS3, Object> callback = invocation.getArgument(1);
                    return callback.apply(client);
                });
        routeProperties = new SimpleS3RouteProperties();
        properties = new SimpleS3ClientProperties();
        template = new S3ClientTemplate(routeTemplate, routeProperties, properties, retryExecutor);
    }

    // ==================== 上传 ====================

    @Test
    void putObjectFileDelegatesWithFile() throws Exception {
        File file = File.createTempFile("s3-client-test", ".txt");
        Files.write(file.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        PutObjectResult expected = new PutObjectResult();
        when(client.putObject(anyString(), anyString(), any(File.class))).thenReturn(expected);

        PutObjectResult actual = template.putObject(TARGET, "bucket-a", "key-a", file);

        assertThat(actual).isSameAs(expected);
        verify(client).putObject("bucket-a", "key-a", file);
        verify(retryExecutor).executeWithFixedDelay(any(), anyInt(), anyLong());
        log.info("File 上传参数逐项透传且走配置化重试");
    }

    @Test
    void putObjectMemoryStreamAutoFillsContentLength() throws Exception {
        byte[] bytes = "stream-content".getBytes(StandardCharsets.UTF_8);
        PutObjectResult expected = new PutObjectResult();
        when(client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenReturn(expected);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);

        PutObjectResult actual = template.putObject(TARGET, "bucket-a", "key-a", input);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("bucket-a"), eq("key-a"), eq(input), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(bytes.length);
        verify(retryExecutor, never()).executeWithFixedDelay(any(), anyInt(), anyLong());
        log.info("内存流无 metadata 时自动补 contentLength 且流式路径不重试: {}", bytes.length);
    }

    @Test
    void putObjectNonMemoryStreamPassesNullMetadata() {
        when(client.putObject(anyString(), anyString(), any(InputStream.class), isNull()))
                .thenReturn(new PutObjectResult());
        InputStream input = mock(InputStream.class);

        template.putObject(TARGET, "bucket-a", "key-a", input);

        verify(client).putObject(eq("bucket-a"), eq("key-a"), eq(input), isNull());
        log.info("不可无副作用探测的流不缓冲不探测, metadata 按 null 走 SDK 原生");
    }

    @Test
    void putObjectStreamRespectsExplicitMetadata() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(99L);
        when(client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenReturn(new PutObjectResult());
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[8]);

        template.putObject(TARGET, "bucket-a", "key-a", input, metadata);

        verify(client).putObject("bucket-a", "key-a", input, metadata);
        log.info("显式 metadata 时尊重调用方, 不做探测覆盖");
    }

    @Test
    void putObjectWithExpirationPrefixPrependsConfiguredPrefix() throws Exception {
        File file = File.createTempFile("s3-client-exp", ".txt");
        Files.write(file.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        when(client.putObject(anyString(), anyString(), any(File.class))).thenReturn(new PutObjectResult());

        template.putObjectWithExpirationPrefix(TARGET, "bucket-a", "key-a", file);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).putObject(eq("bucket-a"), keyCaptor.capture(), eq(file));
        assertThat(keyCaptor.getValue()).isEqualTo("expiration-key-a");
        log.info("过期前缀上传拼接默认前缀: {}", keyCaptor.getValue());
    }

    // ==================== 下载 / 断点续传 ====================

    @Test
    void getObjectAppliesRangeFromOffset() {
        S3Object expected = mock(S3Object.class);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(expected);

        S3Object actual = template.getObject(TARGET, "bucket-a", "key-a", 1024L);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRange()[0]).isEqualTo(1024L);
        log.info("getObject offset 透传为 Range 起点: 1024");
    }

    @Test
    void getObjectAppliesRangeAndVersionId() {
        S3Object expected = mock(S3Object.class);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(expected);

        S3Object actual = template.getObject(TARGET, "bucket-a", "key-a", 512L, "v-9");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRange()[0]).isEqualTo(512L);
        assertThat(requestCaptor.getValue().getVersionId()).isEqualTo("v-9");
        log.info("getObject offset 与 versionId 同时透传");
    }

    @Test
    void downloadToFileOverwritesExistingFile() throws Exception {
        S3Object object = mock(S3Object.class);
        when(object.getObjectContent()).thenReturn(new S3ObjectInputStream(
                new ByteArrayInputStream("new-content".getBytes(StandardCharsets.UTF_8)), null));
        when(client.getObject("bucket-a", "key-a")).thenReturn(object);
        File target = File.createTempFile("s3-client-download", ".txt");
        Files.write(target.toPath(), "old-content".getBytes(StandardCharsets.UTF_8));

        File result = template.downloadToFile(TARGET, "bucket-a", "key-a", target);

        assertThat(result).isSameAs(target);
        assertThat(new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("new-content");
        log.info("downloadToFile 目标已存在时覆盖写");
    }

    @Test
    void downloadObjectResumesFromExistingBytes() throws Exception {
        byte[] fullContent = "0123456789".getBytes(StandardCharsets.UTF_8);
        File existing = File.createTempFile("s3-client-resume", ".bin");
        Files.write(existing.toPath(), Arrays.copyOfRange(fullContent, 0, 4));
        S3Object object = mock(S3Object.class);
        when(object.getObjectContent()).thenReturn(new S3ObjectInputStream(
                new ByteArrayInputStream(Arrays.copyOfRange(fullContent, 4, fullContent.length)), null));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);

        File result = template.downloadObject(TARGET, "bucket-a", "key-a", existing.getPath());

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRange()[0]).isEqualTo(4L);
        assertThat(new String(Files.readAllBytes(result.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("0123456789");
        log.info("断点续传从已下载 4 字节处 Range 追加, 结果与全量一致");
    }

    @Test
    void downloadObjectTreatsInvalidRangeAsComplete() throws Exception {
        File existing = File.createTempFile("s3-client-complete", ".bin");
        Files.write(existing.toPath(), "full".getBytes(StandardCharsets.UTF_8));
        com.amazonaws.services.s3.model.AmazonS3Exception invalidRange =
                serviceFailure("InvalidRange", 416);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(invalidRange);

        File result = template.downloadObject(TARGET, "bucket-a", "key-a", existing.getPath());

        assertThat(new String(Files.readAllBytes(result.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("full");
        log.info("InvalidRange 视为已下载完成, 幂等返回本地文件");
    }

    @Test
    void downloadObjectPassesVersionIdToRequest() throws Exception {
        byte[] content = "versioned".getBytes(StandardCharsets.UTF_8);
        File target = File.createTempFile("s3-client-versioned", ".bin");
        Files.write(target.toPath(), new byte[0]);
        S3Object object = mock(S3Object.class);
        when(object.getObjectContent()).thenReturn(new S3ObjectInputStream(
                new ByteArrayInputStream(content), null));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);

        File result = template.downloadObject(TARGET, "bucket-a", "key-a", target.getPath(), "v-2");

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getVersionId()).isEqualTo("v-2");
        assertThat(requestCaptor.getValue().getRange()[0]).isZero();
        assertThat(new String(Files.readAllBytes(result.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("versioned");
        log.info("带版本号下载透传 versionId 且 Range 起点为本地已下载字节");
    }

    @Test
    void downloadObjectWithExpirationPrefixPrependsPrefix() throws Exception {
        byte[] content = "expiration-download".getBytes(StandardCharsets.UTF_8);
        File target = File.createTempFile("s3-client-exp-dl", ".bin");
        Files.write(target.toPath(), new byte[0]);
        S3Object object = mock(S3Object.class);
        when(object.getObjectContent()).thenReturn(new S3ObjectInputStream(
                new ByteArrayInputStream(content), null));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);

        File result = template.downloadObjectWithExpirationPrefix(TARGET, "bucket-a", "key-a", target.getPath());

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getKey()).isEqualTo("expiration-key-a");
        assertThat(new String(Files.readAllBytes(result.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("expiration-download");
        log.info("过期前缀下载拼接默认前缀并落盘");
    }

    // ==================== 删除 / 存在性 ====================

    @Test
    void deleteObjectDelegates() {
        template.deleteObject(TARGET, "bucket-a", "key-a");

        verify(client).deleteObject("bucket-a", "key-a");
        log.info("deleteObject 参数透传");
    }

    @Test
    void deleteObjectTreatsNoSuchKeyAsSuccess() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure("NoSuchKey", 404);
        doThrow(failure).when(client).deleteObject("bucket-a", "key-a");

        template.deleteObject(TARGET, "bucket-a", "key-a");

        log.info("NoSuchKey 删除视为成功（幂等语义）");
    }

    @Test
    void deleteObjectWithExpirationPrefixPrependsPrefix() {
        template.deleteObjectWithExpirationPrefix(TARGET, "bucket-a", "key-a");

        verify(client).deleteObject("bucket-a", "expiration-key-a");
        log.info("过期前缀删除拼接默认前缀");
    }

    @Test
    void doesObjectExistDelegates() {
        when(client.doesObjectExist("bucket-a", "key-a")).thenReturn(true);

        assertThat(template.doesObjectExist(TARGET, "bucket-a", "key-a")).isTrue();
        log.info("doesObjectExist 委托并返回布尔结果");
    }

    // ==================== 列举 / 元数据 / 复制 ====================

    @Test
    void listObjectsDelegatesWithPrefix() {
        ObjectListing expected = new ObjectListing();
        when(client.listObjects("bucket-a", "prefix/")).thenReturn(expected);

        ObjectListing actual = template.listObjects(TARGET, "bucket-a", "prefix/");

        assertThat(actual).isSameAs(expected);
        log.info("listObjects 前缀参数透传");
    }

    @Test
    void listObjectsWithoutPrefixListsAll() {
        ObjectListing expected = new ObjectListing();
        when(client.listObjects("bucket-a", null)).thenReturn(expected);

        ObjectListing actual = template.listObjects(TARGET, "bucket-a");

        assertThat(actual).isSameAs(expected);
        log.info("listObjects 无前缀时列举全部");
    }

    @Test
    void listObjectsAppliesMaxKeys() {
        ObjectListing expected = new ObjectListing();
        when(client.listObjects(any(com.amazonaws.services.s3.model.ListObjectsRequest.class)))
                .thenReturn(expected);

        ObjectListing actual = template.listObjects(TARGET, "bucket-a", "prefix/", 7);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<com.amazonaws.services.s3.model.ListObjectsRequest> requestCaptor =
                ArgumentCaptor.forClass(com.amazonaws.services.s3.model.ListObjectsRequest.class);
        verify(client).listObjects(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMaxKeys()).isEqualTo(7);
        assertThat(requestCaptor.getValue().getPrefix()).isEqualTo("prefix/");
        log.info("listObjects maxKeys 与 prefix 透传: {}", 7);
    }

    @Test
    void listVersionsAppliesPrefix() {
        VersionListing expected = new VersionListing();
        when(client.listVersions(any(ListVersionsRequest.class))).thenReturn(expected);

        VersionListing actual = template.listVersions(TARGET, "bucket-a", "v/");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<ListVersionsRequest> requestCaptor = ArgumentCaptor.forClass(ListVersionsRequest.class);
        verify(client).listVersions(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPrefix()).isEqualTo("v/");
        log.info("listVersions 前缀参数透传");
    }

    @Test
    void getObjectMetadataDelegates() {
        ObjectMetadata expected = new ObjectMetadata();
        when(client.getObjectMetadata(any(com.amazonaws.services.s3.model.GetObjectMetadataRequest.class)))
                .thenReturn(expected);

        assertThat(template.getObjectMetadata(TARGET, "bucket-a", "key-a")).isSameAs(expected);
        ArgumentCaptor<com.amazonaws.services.s3.model.GetObjectMetadataRequest> requestCaptor =
                ArgumentCaptor.forClass(com.amazonaws.services.s3.model.GetObjectMetadataRequest.class);
        verify(client).getObjectMetadata(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBucketName()).isEqualTo("bucket-a");
        assertThat(requestCaptor.getValue().getKey()).isEqualTo("key-a");
        log.info("getObjectMetadata 委托并返回元数据");
    }

    @Test
    void getObjectMetadataAppliesVersionId() {
        ObjectMetadata expected = new ObjectMetadata();
        when(client.getObjectMetadata(any(com.amazonaws.services.s3.model.GetObjectMetadataRequest.class)))
                .thenReturn(expected);

        assertThat(template.getObjectMetadata(TARGET, "bucket-a", "key-a", "v1")).isSameAs(expected);
        ArgumentCaptor<com.amazonaws.services.s3.model.GetObjectMetadataRequest> requestCaptor =
                ArgumentCaptor.forClass(com.amazonaws.services.s3.model.GetObjectMetadataRequest.class);
        verify(client).getObjectMetadata(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getVersionId()).isEqualTo("v1");
        log.info("getObjectMetadata versionId 透传");
    }

    @Test
    void copyObjectDelegates() {
        when(client.copyObject("from-bucket", "from-key", "to-bucket", "to-key"))
                .thenReturn(new com.amazonaws.services.s3.model.CopyObjectResult());

        template.copyObject(TARGET, "from-bucket", "from-key", "to-bucket", "to-key");

        verify(client).copyObject("from-bucket", "from-key", "to-bucket", "to-key");
        log.info("copyObject 四参数逐项透传");
    }

    // ==================== 对象标签 ====================

    @Test
    void setObjectTaggingDelegatesTags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("env", "e2e");

        template.setObjectTagging(TARGET, "bucket-a", "key-a", tags);

        ArgumentCaptor<com.amazonaws.services.s3.model.SetObjectTaggingRequest> requestCaptor =
                ArgumentCaptor.forClass(com.amazonaws.services.s3.model.SetObjectTaggingRequest.class);
        verify(client).setObjectTagging(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTagging().getTagSet())
                .extracting(com.amazonaws.services.s3.model.Tag::getKey,
                        com.amazonaws.services.s3.model.Tag::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("env", "e2e"));
        log.info("setObjectTagging 标签键值透传");
    }

    @Test
    void setObjectTaggingValidatesArguments() {
        Map<String, String> nullTags = null;
        assertThatThrownBy(() -> template.setObjectTagging(TARGET, "bucket-a", "key-a", nullTags))
                .isInstanceOf(TaggingFailedException.class);
        Map<String, String> blankKey = new HashMap<>();
        blankKey.put(" ", "value");
        assertThatThrownBy(() -> template.setObjectTagging(TARGET, "bucket-a", "key-a", blankKey))
                .isInstanceOf(TaggingFailedException.class);
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("k", null);
        assertThatThrownBy(() -> template.setObjectTagging(TARGET, "bucket-a", "key-a", nullValue))
                .isInstanceOf(TaggingFailedException.class);
        Map<String, String> tooMany = new HashMap<>();
        for (int i = 0; i < 11; i++) {
            tooMany.put("k" + i, "v");
        }
        assertThatThrownBy(() -> template.setObjectTagging(TARGET, "bucket-a", "key-a", tooMany))
                .isInstanceOf(TaggingFailedException.class);
        Map<String, String> longKey = new HashMap<>();
        longKey.put(new String(new byte[129]), "v");
        assertThatThrownBy(() -> template.setObjectTagging(TARGET, "bucket-a", "key-a", longKey))
                .isInstanceOf(TaggingFailedException.class);
        Map<String, String> longValue = new HashMap<>();
        longValue.put("k", new String(new byte[129]));
        assertThatThrownBy(() -> template.setObjectTagging(TARGET, "bucket-a", "key-a", longValue))
                .isInstanceOf(TaggingFailedException.class);
        log.info("标签校验全场景拒绝: null/blank key/null value/超量/超长 key/超长 value");
    }

    @Test
    void getObjectTaggingReturnsMap() {
        when(client.getObjectTagging(any(com.amazonaws.services.s3.model.GetObjectTaggingRequest.class)))
                .thenReturn(new com.amazonaws.services.s3.model.GetObjectTaggingResult(
                        Arrays.asList(new com.amazonaws.services.s3.model.Tag("a", "1"),
                                new com.amazonaws.services.s3.model.Tag("b", "2"))));

        Map<String, String> actual = template.getObjectTagging(TARGET, "bucket-a", "key-a");

        assertThat(actual).containsEntry("a", "1").containsEntry("b", "2").hasSize(2);
        log.info("getObjectTagging 返回键值 Map: {}", actual.size());
    }

    @Test
    void deleteObjectTaggingDelegates() {
        template.deleteObjectTagging(TARGET, "bucket-a", "key-a");

        verify(client).deleteObjectTagging(
                any(com.amazonaws.services.s3.model.DeleteObjectTaggingRequest.class));
        log.info("deleteObjectTagging 委托执行");
    }

    // ==================== 预签名 URL ====================

    @Test
    void generatePresignedUrlReturnsFullUrlWithoutPrefix() throws Exception {
        URL generated = new URL("http", "storage.internal", 9000,
                "/bucket-a/key-a.txt?X-Amz-Signature=abc");
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(generated);

        String actual = template.generatePresignedUrl(TARGET, "bucket-a", "key-a.txt", 60L);

        assertThat(actual).isEqualTo(generated.toString());
        ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getResponseHeaders().getContentDisposition())
                .contains("attachment").contains("key-a.txt");
        assertThat(request.getResponseHeaders().getContentType()).isEqualTo("text/plain");
        log.info("无 url-prefix 时返回完整 URL 且默认 DOWNLOAD Disposition + Content-Type 推断");
    }

    @Test
    void generatePresignedUrlAppliesGatewayPrefixWhenConfigured() throws Exception {
        properties.getPresignedUrl().setUrlPrefix("https://gw.internal");
        URL generated = new URL("http", "storage.internal", 9000,
                "/bucket-a/key-a?X-Amz-Signature=abc");
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(generated);

        String actual = template.generatePresignedUrl(TARGET, "bucket-a", "key-a", 60L);

        assertThat(actual).isEqualTo("https://gw.internal/bucket-a/key-a?X-Amz-Signature=abc");
        log.info("url-prefix 配置后返回「前缀 + 签名路径与查询串」");
    }

    @Test
    void generatePresignedUrlHonorsNullExpirationAndDisposition() throws Exception {
        URL generated = new URL("http://storage.internal/bucket-a/key-a");
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(generated);
        long beforeMillis = System.currentTimeMillis();

        template.generatePresignedUrl(TARGET, "bucket-a", "key-a", null,
                io.github.surezzzzzz.sdk.s3.client.constant.FileDisposition.INLINE);

        ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertThat(request.getExpiration().getTime()).isBetween(
                beforeMillis + 86400_000L - 5_000L, System.currentTimeMillis() + 86400_000L + 5_000L);
        assertThat(request.getResponseHeaders().getContentDisposition()).contains("inline");
        log.info("expiration 为 null 时用配置默认 86400 秒, INLINE Disposition 透传");
    }

    @Test
    void generateUploadPresignedUrlAppliesPutAndContentType() throws Exception {
        URL generated = new URL("http://storage.internal/bucket-a/key-a");
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(generated);

        template.generateUploadPresignedUrl(TARGET, "bucket-a", "key-a", 60L, "application/json");

        ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.PUT);
        assertThat(request.getContentType()).isEqualTo("application/json");
        log.info("PUT 预签名方法与 Content-Type 签名头透传");
    }

    @Test
    void generatePresignedUrlRejectsIllegalArguments() {
        assertThatThrownBy(() -> template.generatePresignedUrl(TARGET, "bucket-a", "key-a", 0L))
                .isInstanceOf(S3ClientException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REQUEST_ILLEGAL));
        assertThatThrownBy(() -> template.generatePresignedUrl(TARGET, "bucket-a", "key-a", 60L, null))
                .isInstanceOf(S3ClientException.class);
        log.info("有效时长非正或 disposition 为 null 时以请求非法拒绝");
    }

    // ==================== 分片四步 ====================

    @Test
    void multipartFourStepsDelegate() throws Exception {
        InitiateMultipartUploadResult initiateResult = new InitiateMultipartUploadResult();
        initiateResult.setUploadId("upload-1");
        when(client.initiateMultipartUpload(any(com.amazonaws.services.s3.model.InitiateMultipartUploadRequest.class)))
                .thenReturn(initiateResult);

        String uploadId = template.initiateMultipartUpload(TARGET, "bucket-a", "key-a");

        assertThat(uploadId).isEqualTo("upload-1");

        File partFile = File.createTempFile("s3-client-part", ".bin");
        Files.write(partFile.toPath(), new byte[10]);
        when(client.uploadPart(any(UploadPartRequest.class))).thenReturn(new UploadPartResult());

        template.uploadPart(TARGET, "bucket-a", "key-a", uploadId, 1, partFile);

        ArgumentCaptor<UploadPartRequest> partCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(client).uploadPart(partCaptor.capture());
        UploadPartRequest partRequest = partCaptor.getValue();
        assertThat(partRequest.getBucketName()).isEqualTo("bucket-a");
        assertThat(partRequest.getKey()).isEqualTo("key-a");
        assertThat(partRequest.getUploadId()).isEqualTo("upload-1");
        assertThat(partRequest.getPartNumber()).isEqualTo(1);
        assertThat(partRequest.getFile()).isEqualTo(partFile);
        assertThat(partRequest.getPartSize()).isEqualTo(partFile.length());

        List<PartETag> unordered = Arrays.asList(new PartETag(2, "etag-2"), new PartETag(1, "etag-1"));
        when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(new com.amazonaws.services.s3.model.CompleteMultipartUploadResult());

        template.completeMultipartUpload(TARGET, "bucket-a", "key-a", uploadId, unordered);

        ArgumentCaptor<CompleteMultipartUploadRequest> completeCaptor =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(client).completeMultipartUpload(completeCaptor.capture());
        assertThat(completeCaptor.getValue().getUploadId()).isEqualTo("upload-1");
        assertThat(completeCaptor.getValue().getPartETags().get(0).getPartNumber()).isEqualTo(1);
        assertThat(completeCaptor.getValue().getPartETags().get(1).getPartNumber()).isEqualTo(2);

        template.abortMultipartUpload(TARGET, "bucket-a", "key-a", uploadId);

        ArgumentCaptor<AbortMultipartUploadRequest> abortCaptor =
                ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
        verify(client).abortMultipartUpload(abortCaptor.capture());
        assertThat(abortCaptor.getValue().getUploadId()).isEqualTo("upload-1");
        log.info("分片四步透传且乱序 ETag 按编号升序提交: uploadId={}", uploadId);
    }

    @Test
    void completeMultipartUploadValidatesPartETags() {
        assertThatThrownBy(() -> template.completeMultipartUpload(
                TARGET, "bucket-a", "key-a", "u", null))
                .isInstanceOf(UploadFailedException.class);
        assertThatThrownBy(() -> template.completeMultipartUpload(
                TARGET, "bucket-a", "key-a", "u", new ArrayList<>()))
                .isInstanceOf(UploadFailedException.class);
        List<PartETag> duplicated = Arrays.asList(new PartETag(1, "a"), new PartETag(1, "b"));
        assertThatThrownBy(() -> template.completeMultipartUpload(
                TARGET, "bucket-a", "key-a", "u", duplicated))
                .isInstanceOf(UploadFailedException.class);
        List<PartETag> blankEtag = Arrays.asList(new PartETag(1, " "));
        assertThatThrownBy(() -> template.completeMultipartUpload(
                TARGET, "bucket-a", "key-a", "u", blankEtag))
                .isInstanceOf(UploadFailedException.class);
        List<PartETag> outOfRange = Arrays.asList(new PartETag(10001, "a"));
        assertThatThrownBy(() -> template.completeMultipartUpload(
                TARGET, "bucket-a", "key-a", "u", outOfRange))
                .isInstanceOf(UploadFailedException.class);
        log.info("complete 校验拒绝: null/空/重复编号/空 ETag/编号越界");
    }

    @Test
    void abortMultipartUploadTreatsNoSuchUploadAsSuccess() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure("NoSuchUpload", 404);
        doThrow(failure).when(client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));

        template.abortMultipartUpload(TARGET, "bucket-a", "key-a", "u");

        log.info("NoSuchUpload 中止视为成功（幂等语义）");
    }

    @Test
    void uploadPartStreamVariantAppliesLength() {
        when(client.uploadPart(any(UploadPartRequest.class))).thenReturn(new UploadPartResult());
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[16]);

        template.uploadPart(TARGET, "bucket-a", "key-a", "u", 2, input, 16L);

        ArgumentCaptor<UploadPartRequest> requestCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(client).uploadPart(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getInputStream()).isEqualTo(input);
        assertThat(requestCaptor.getValue().getPartSize()).isEqualTo(16L);
        assertThat(requestCaptor.getValue().getPartNumber()).isEqualTo(2);
        log.info("uploadPart 流式变体透传流与分段长度");
    }

    @Test
    void listPartsAggregatesPages() {
        PartListing pageOne = new PartListing();
        pageOne.setTruncated(true);
        pageOne.setNextPartNumberMarker(2);
        pageOne.setParts(Arrays.asList(part(1, "e1", 5L), part(2, "e2", 5L)));
        PartListing pageTwo = new PartListing();
        pageTwo.setTruncated(false);
        pageTwo.setParts(Arrays.asList(part(3, "e3", 3L)));
        when(client.listParts(any(ListPartsRequest.class))).thenReturn(pageOne, pageTwo);

        MultipartUploadPartList actual = template.listParts(TARGET, "bucket-a", "key-a", "u");

        assertThat(actual.getParts()).hasSize(3);
        assertThat(actual.getNextPartNumberMarker()).isEqualTo(0);
        verify(client, times(2)).listParts(any(ListPartsRequest.class));
        log.info("listParts 分页聚合两页共 {} 段", actual.getParts().size());
    }

    @Test
    void listMultipartUploadsAggregatesPages() {
        MultipartUploadListing pageOne = new MultipartUploadListing();
        pageOne.setTruncated(true);
        pageOne.setNextKeyMarker("k1");
        pageOne.setNextUploadIdMarker("u1");
        com.amazonaws.services.s3.model.MultipartUpload first =
                new com.amazonaws.services.s3.model.MultipartUpload();
        first.setUploadId("u1");
        first.setKey("k1");
        first.setInitiated(new Date());
        pageOne.setMultipartUploads(Arrays.asList(first));
        MultipartUploadListing pageTwo = new MultipartUploadListing();
        pageTwo.setTruncated(false);
        pageTwo.setMultipartUploads(new ArrayList<>());
        when(client.listMultipartUploads(any(ListMultipartUploadsRequest.class)))
                .thenReturn(pageOne, pageTwo);

        MultipartUploadList actual = template.listMultipartUploads(TARGET, "bucket-a");

        assertThat(actual.getUploads()).hasSize(1);
        assertThat(actual.isTruncated()).isFalse();
        assertThat(actual.getNextKeyMarker()).isNull();
        verify(client, times(2)).listMultipartUploads(any(ListMultipartUploadsRequest.class));
        log.info("listMultipartUploads 分页聚合并返回终止标记语义");
    }

    // ==================== 自动分片一把梭 ====================

    @Test
    void uploadObjectMultipartDirectPutsUnderThreshold() throws Exception {
        properties.getMultipart().setThresholdMb(1);
        File file = File.createTempFile("s3-client-mp", ".bin");
        Files.write(file.toPath(), "small".getBytes(StandardCharsets.UTF_8));
        when(client.putObject(anyString(), anyString(), any(File.class))).thenReturn(new PutObjectResult());

        template.uploadObjectMultipart(TARGET, "bucket-a", "key-a", file);

        verify(client).putObject(eq("bucket-a"), eq("key-a"), eq(file));
        verify(client, never()).initiateMultipartUpload(
                any(com.amazonaws.services.s3.model.InitiateMultipartUploadRequest.class));
        log.info("阈值内文件直传不分片");
    }

    @Test
    void uploadObjectMultipartSplitsAndCompletesOverThreshold() throws Exception {
        properties.getMultipart().setThresholdMb(1);
        properties.getMultipart().setPartSizeMb(5);
        byte[] data = new byte[6 * 1024 * 1024];
        File file = File.createTempFile("s3-client-mp-big", ".bin");
        Files.write(file.toPath(), data);
        InitiateMultipartUploadResult initiateResult = new InitiateMultipartUploadResult();
        initiateResult.setUploadId("u-multipart");
        when(client.initiateMultipartUpload(any(com.amazonaws.services.s3.model.InitiateMultipartUploadRequest.class)))
                .thenReturn(initiateResult);
        UploadPartResult partResult = new UploadPartResult();
        partResult.setETag("e1");
        partResult.setPartNumber(1);
        UploadPartResult partResultTwo = new UploadPartResult();
        partResultTwo.setETag("e2");
        partResultTwo.setPartNumber(2);
        when(client.uploadPart(any(UploadPartRequest.class))).thenReturn(partResult, partResultTwo);
        when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(new com.amazonaws.services.s3.model.CompleteMultipartUploadResult());

        template.uploadObjectMultipart(TARGET, "bucket-a", "key-a", file);
        template.destroy();

        ArgumentCaptor<UploadPartRequest> partCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(client, times(2)).uploadPart(partCaptor.capture());
        List<UploadPartRequest> requests = new ArrayList<>(partCaptor.getAllValues());
        requests.sort((left, right) -> Integer.compare(left.getPartNumber(), right.getPartNumber()));
        assertThat(requests.get(0).getFileOffset()).isEqualTo(0L);
        assertThat(requests.get(0).getPartSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(requests.get(1).getFileOffset()).isEqualTo(5L * 1024 * 1024);
        assertThat(requests.get(1).getPartSize()).isEqualTo(1L * 1024 * 1024);
        ArgumentCaptor<CompleteMultipartUploadRequest> completeCaptor =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(client).completeMultipartUpload(completeCaptor.capture());
        assertThat(completeCaptor.getValue().getPartETags())
                .extracting(PartETag::getPartNumber).containsExactly(1, 2);
        log.info("6MB 文件按 5MB 阈值外分片为 2 段: 5MB + 1MB");
    }

    @Test
    void uploadObjectMultipartRejectsSmallPartSize() throws Exception {
        File file = File.createTempFile("s3-client-mp", ".bin");
        Files.write(file.toPath(), new byte[10]);

        assertThatThrownBy(() -> template.uploadObjectMultipart(TARGET, "bucket-a", "key-a", file, 4))
                .isInstanceOf(UploadFailedException.class);
        log.info("分段大小小于 5MB 时拒绝");
    }

    // ==================== STS ====================

    @Test
    void getPathStsCredentialsRequiresRoleArn() {
        assertThatThrownBy(() -> template.getPathStsCredentials(TARGET, "bucket-a"))
                .isInstanceOf(StsCredentialsFailedException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).contains("role-arn"));
        log.info("role-arn 未配置时路径级 STS 以受控消息拒绝");
    }

    @Test
    void stsRejectsUnregisteredTarget() {
        properties.getSts().setRoleArn("arn:aws:iam::1:role/r");
        routeProperties.setTargets(new HashMap<>());

        assertThatThrownBy(() -> template.getPathStsCredentials(TARGET, "bucket-a"))
                .isInstanceOf(StsCredentialsFailedException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).contains("target 未登记"));
        log.info("target 未登记时 STS 以受控消息拒绝");
    }

    // ==================== 桶管理 ====================

    @Test
    void createBucketIsIdempotentWhenBucketExists() {
        when(client.doesBucketExistV2("bucket-a")).thenReturn(true);
        Bucket existing = new Bucket();
        existing.setName("bucket-a");
        when(client.listBuckets()).thenReturn(Arrays.asList(existing));

        Bucket actual = template.createBucket(TARGET, "bucket-a");

        assertThat(actual).isSameAs(existing);
        verify(client, never()).createBucket(anyString());
        log.info("桶已存在时幂等找回既有桶不重复创建");
    }

    @Test
    void bucketManagementDelegates() {
        when(client.doesBucketExistV2("bucket-a")).thenReturn(false);
        Bucket created = new Bucket();
        created.setName("bucket-a");
        when(client.createBucket(anyString())).thenReturn(created);

        Bucket actual = template.createBucket(TARGET, "bucket-a");

        assertThat(actual).isSameAs(created);

        template.deleteBucket(TARGET, "bucket-a");
        verify(client).deleteBucket("bucket-a");

        when(client.doesBucketExist("bucket-a")).thenReturn(true);
        assertThat(template.doesBucketExist(TARGET, "bucket-a")).isTrue();
        log.info("桶管理参数透传");
    }

    @Test
    void createVersioningBucketConfiguresVersioningAndLifecycle() {
        when(client.doesBucketExistV2("bucket-a")).thenReturn(false);
        when(client.createBucket(anyString())).thenReturn(new Bucket());

        template.createVersioningBucket(TARGET, "bucket-a");

        verify(client).setBucketVersioningConfiguration(
                any(com.amazonaws.services.s3.model.SetBucketVersioningConfigurationRequest.class));
        ArgumentCaptor<com.amazonaws.services.s3.model.SetBucketLifecycleConfigurationRequest> lifecycleCaptor =
                ArgumentCaptor.forClass(com.amazonaws.services.s3.model.SetBucketLifecycleConfigurationRequest.class);
        verify(client).setBucketLifecycleConfiguration(lifecycleCaptor.capture());
        com.amazonaws.services.s3.model.BucketLifecycleConfiguration.Rule rule =
                lifecycleCaptor.getValue().getLifecycleConfiguration().getRules().get(0);
        assertThat(rule.getExpirationInDays()).isEqualTo(180);
        assertThat(rule.getId()).startsWith("expiration-");
        log.info("版本化桶组合调用: 建桶 + 版本化 + 生命周期(前缀 expiration-, 180 天)");
    }

    @Test
    void createFolderAppendsSeparatorAndIsIdempotent() {
        when(client.doesObjectExist("bucket-a", "logs/")).thenReturn(true);

        template.createFolder(TARGET, "bucket-a", "logs");

        verify(client, never()).putObject(anyString(), anyString(), any(InputStream.class),
                any(ObjectMetadata.class));
        when(client.doesObjectExist("bucket-a", "newdir/")).thenReturn(false);
        template.createFolder(TARGET, "bucket-a", "newdir");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).putObject(eq("bucket-a"), keyCaptor.capture(), any(InputStream.class),
                any(ObjectMetadata.class));
        assertThat(keyCaptor.getValue()).isEqualTo("newdir/");
        log.info("createFolder 补 / 结尾且已存在幂等跳过");
    }

    // ==================== 异常翻译 ====================

    @Test
    void noSuchKeyTranslatesToObjectNotExist() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure("NoSuchKey", 404);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> template.getObject(TARGET, "bucket-a", "key-a"))
                .isInstanceOf(ObjectNotExistException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.OBJECT_NOT_EXIST))
                .hasCause(failure);
        log.info("NoSuchKey 翻译为 ObjectNotExist 且 cause 保留原异常");
    }

    @Test
    void noSuchBucketTranslatesToBucketNotExist() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure("NoSuchBucket", 404);
        when(client.listObjects(anyString(), any())).thenThrow(failure);

        assertThatThrownBy(() -> template.listObjects(TARGET, "bucket-a"))
                .isInstanceOf(BucketNotExistException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.BUCKET_NOT_EXIST))
                .hasCause(failure);
        log.info("NoSuchBucket 优先于 404 泛化映射为 BucketNotExist");
    }

    @Test
    void plain404TranslatesToObjectNotExist() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure(null, 404);
        when(client.getObjectMetadata(any(com.amazonaws.services.s3.model.GetObjectMetadataRequest.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> template.getObjectMetadata(TARGET, "bucket-a", "key-a"))
                .isInstanceOf(ObjectNotExistException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.OBJECT_NOT_EXIST));
        log.info("无标准错误码的 404 泛化映射为 ObjectNotExist");
    }

    @Test
    void bucketAlreadyExistsTranslates() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure("BucketAlreadyExists", 409);
        when(client.doesBucketExistV2("bucket-a")).thenReturn(false);
        when(client.createBucket(anyString())).thenThrow(failure);

        assertThatThrownBy(() -> template.createBucket(TARGET, "bucket-a"))
                .isInstanceOf(BucketAlreadyExistException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.BUCKET_ALREADY_EXIST));
        log.info("BucketAlreadyExists 翻译为 BucketAlreadyExist");
    }

    @Test
    void accessDeniedTranslates() {
        com.amazonaws.services.s3.model.AmazonS3Exception byCode = serviceFailure("AccessDenied", 403);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(byCode);
        assertThatThrownBy(() -> template.getObject(TARGET, "bucket-a", "key-a"))
                .isInstanceOf(AccessDeniedException.class);

        com.amazonaws.services.s3.model.AmazonS3Exception byStatus = serviceFailure(null, 403);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(byStatus);
        assertThatThrownBy(() -> template.getObject(TARGET, "bucket-a", "key-b"))
                .isInstanceOf(AccessDeniedException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));
        log.info("AccessDenied 与裸 403 均翻译为 AccessDenied");
    }

    @Test
    void unmappedServiceExceptionFallsBackByOperation() {
        com.amazonaws.services.s3.model.AmazonS3Exception failure = serviceFailure("InternalError", 500);
        when(client.putObject(anyString(), anyString(), any(File.class))).thenThrow(failure);
        File file = new File("nonexistent-e2e.txt");

        assertThatThrownBy(() -> template.putObject(TARGET, "bucket-a", "key-a", file))
                .isInstanceOf(UploadFailedException.class)
                .satisfies(exception -> {
                    assertThat(((S3ClientException) exception).getErrorCode()).isEqualTo(ErrorCode.UPLOAD_FAILED);
                    assertThat(exception.getMessage()).contains("InternalError");
                })
                .hasCause(failure);
        log.info("未映射的服务端异常按操作归类为 UploadFailed, 文案携带存储侧错误码");
    }

    @Test
    void sdkClientExceptionFallsBackByOperation() {
        SdkClientException failure = new SdkClientException("connection refused");
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> template.getObject(TARGET, "bucket-a", "key-a"))
                .isInstanceOf(DownloadFailedException.class)
                .hasCause(failure);
        log.info("网络层异常按操作归类为 DownloadFailed");
    }

    @Test
    void routeExceptionPassesThroughUnchanged() {
        S3RouteException failure = new S3RouteException(
                io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode.TARGET_NOT_REGISTERED, "target");
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> template.getObject(TARGET, "bucket-a", "key-a"))
                .isSameAs(failure);
        log.info("Route 连接层异常原样透传不包装");
    }

    @Test
    void amazonS3DelegatesToRoute() {
        when(routeTemplate.amazonS3(TARGET)).thenReturn(client);

        assertThat(template.amazonS3(TARGET)).isSameAs(client);
        verify(routeTemplate).amazonS3(TARGET);
        log.info("amazonS3 直通 Route 门面");
    }

    @Test
    void executeDelegatesCallbackToRoute() {
        String actual = template.execute(TARGET, amazonS3 -> "executed");

        assertThat(actual).isEqualTo("executed");
        verify(routeTemplate).execute(eq(TARGET), any());
        log.info("execute 兜底回调经 Route 执行并透传返回值");
    }

    // ==================== 参数校验 / 事件解析 ====================

    @Test
    void blankArgumentsRejected() {
        assertThatThrownBy(() -> template.getObject(null, "bucket-a", "key-a"))
                .isInstanceOf(S3ClientException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REQUEST_ILLEGAL));
        assertThatThrownBy(() -> template.getObject(TARGET, "  ", "key-a"))
                .isInstanceOf(S3ClientException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REQUEST_ILLEGAL));
        assertThatThrownBy(() -> template.getObject(TARGET, "bucket-a", ""))
                .isInstanceOf(S3ClientException.class)
                .satisfies(exception -> assertThat(((S3ClientException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REQUEST_ILLEGAL));
        log.info("targetKey/bucket/key 空白时以请求非法拒绝");
    }

    @Test
    void parseEventDelegatesToHelper() {
        S3Event event = template.parseEvent("{\"Records\":[{\"eventName\":\"ObjectCreated:Put\"}]}");

        assertThat(event.getRecords()).hasSize(1);
        assertThat(event.getRecords().get(0).getEventName()).isEqualTo("ObjectCreated:Put");
        log.info("parseEvent 委托 Helper 解析事件 JSON");
    }

    // ==================== 工具 ====================

    private com.amazonaws.services.s3.model.AmazonS3Exception serviceFailure(String errorCode, int status) {
        com.amazonaws.services.s3.model.AmazonS3Exception failure =
                new com.amazonaws.services.s3.model.AmazonS3Exception("service failure");
        failure.setErrorCode(errorCode);
        failure.setStatusCode(status);
        return failure;
    }

    private PartSummary part(int number, String etag, long size) {
        PartSummary summary = new PartSummary();
        summary.setPartNumber(number);
        summary.setETag(etag);
        summary.setSize(size);
        summary.setLastModified(new Date());
        return summary;
    }
}
