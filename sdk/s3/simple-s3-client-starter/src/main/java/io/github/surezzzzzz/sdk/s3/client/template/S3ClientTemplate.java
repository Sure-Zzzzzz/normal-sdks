package io.github.surezzzzzz.sdk.s3.client.template;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter;
import com.amazonaws.services.s3.model.lifecycle.LifecyclePrefixPredicate;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.s3.client.annotation.SimpleS3ClientComponent;
import io.github.surezzzzzz.sdk.s3.client.configuration.SimpleS3ClientProperties;
import io.github.surezzzzzz.sdk.s3.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.s3.client.constant.FileDisposition;
import io.github.surezzzzzz.sdk.s3.client.constant.SimpleS3ClientConstant;
import io.github.surezzzzzz.sdk.s3.client.exception.*;
import io.github.surezzzzzz.sdk.s3.client.model.*;
import io.github.surezzzzzz.sdk.s3.client.model.MultipartUpload;
import io.github.surezzzzzz.sdk.s3.client.model.S3Event;
import io.github.surezzzzzz.sdk.s3.client.support.ContentTypeHelper;
import io.github.surezzzzzz.sdk.s3.client.support.PolicyDocumentHelper;
import io.github.surezzzzzz.sdk.s3.client.support.S3EventParseHelper;
import io.github.surezzzzzz.sdk.s3.client.support.StsPolicyNamingStrategy;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteAuthenticationType;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * S3 Client 语义门面：在 Route 连接治理之上封装对象操作语义，功能面对齐老
 * s3-client-starter 的 S3Client——上传（File/流/自动分片一把梭）、下载
 * （回读/落盘/Range 断点续传）、删除（幂等）、存在性、列举（对象/版本/分片）、
 * 元数据、复制、对象标签、预签名（GET/PUT、Disposition 响应头、网关前缀）、
 * 分片显式四步、STS 临时凭证、桶治理（幂等建桶/版本化/生命周期）、事件解析。
 *
 * <p>SDK 操作异常（{@code AmazonServiceException}）按 S3 错误码翻译为语义异常，
 * cause 保留原异常；无精确映射的按操作归类包装。Route 连接层异常
 * （{@code S3RouteException}）原样透传。</p>
 *
 * <p>InputStream 上传未提供 metadata 时，仅对可无副作用获知长度的内存流
 * （{@code ByteArrayInputStream}）自动补 contentLength；其余流不缓冲不探测。
 * File 上传、删除、断点续传下载与分段上传按配置化重试执行；流式上传因流
 * 不可重放不参与重试。</p>
 *
 * <p>生命周期：自动分片线程池懒创建并在容器关闭时显式关停；STS 客户端
 * per-target 懒加载缓存并在容器关闭时统一 shutdown。</p>
 *
 * <p>DEBUG 日志只输出操作名、targetKey、耗时与异常类型等受控元数据，
 * 不输出 bucket、对象 key 或对象内容。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleS3ClientComponent
public class S3ClientTemplate implements DisposableBean {

    private static final long MILLIS_PER_SECOND = SimpleS3ClientConstant.MILLIS_PER_SECOND;

    private final S3RouteTemplate routeTemplate;

    private final SimpleS3RouteProperties routeProperties;

    private final SimpleS3ClientProperties properties;

    private final TaskRetryExecutor retryExecutor;

    private final ObjectMapper policyObjectMapper;

    private final ConcurrentHashMap<String, AWSSecurityTokenService> stsClients = new ConcurrentHashMap<>();

    private final Object multipartExecutorLock = new Object();

    private volatile ExecutorService multipartExecutor;
    /**
     * per-target STS 客户端构建函数；包私有注入点供测试替换（生产走默认静态构建链）。
     */
    private volatile Function<SimpleS3RouteProperties.TargetConfig, AWSSecurityTokenService> stsClientFactory
            = this::buildDefaultStsClient;

    /**
     * 创建 Client 语义门面。
     *
     * @param routeTemplate   Route 连接门面
     * @param routeProperties Route 配置（读取 target 的 endpoint/region/凭据构建 STS 客户端）
     * @param properties      Client 配置
     * @param retryExecutor   重试执行器
     */
    public S3ClientTemplate(S3RouteTemplate routeTemplate, SimpleS3RouteProperties routeProperties,
                            SimpleS3ClientProperties properties, TaskRetryExecutor retryExecutor) {
        this.routeTemplate = routeTemplate;
        this.routeProperties = routeProperties;
        this.properties = properties;
        this.retryExecutor = retryExecutor;
        this.policyObjectMapper = new ObjectMapper();
        this.policyObjectMapper.setPropertyNamingStrategy(new StsPolicyNamingStrategy());
    }

    // ==================== 直通 Route ====================

    private static String storageErrorCode(Throwable cause) {
        if (cause instanceof AmazonServiceException) {
            return String.valueOf(((AmazonServiceException) cause).getErrorCode());
        }
        return cause.getMessage();
    }

    private static Function<Throwable, S3ClientException> uploadFailure() {
        return cause -> new UploadFailedException(
                ErrorCode.UPLOAD_FAILED, String.format(ErrorMessage.UPLOAD_FAILED, storageErrorCode(cause)), cause);
    }

    // ==================== 上传 ====================

    private static Function<Throwable, S3ClientException> downloadFailure() {
        return cause -> new DownloadFailedException(
                ErrorCode.DOWNLOAD_FAILED, String.format(ErrorMessage.DOWNLOAD_FAILED, storageErrorCode(cause)), cause);
    }

    private static Function<Throwable, S3ClientException> deleteFailure() {
        return cause -> new DeleteFailedException(
                ErrorCode.DELETE_FAILED, String.format(ErrorMessage.DELETE_FAILED, storageErrorCode(cause)), cause);
    }

    private static Function<Throwable, S3ClientException> listFailure() {
        return cause -> new ListFailedException(
                ErrorCode.LIST_FAILED, String.format(ErrorMessage.LIST_FAILED, storageErrorCode(cause)), cause);
    }

    private static Function<Throwable, S3ClientException> getMetadataFailure() {
        return cause -> new GetMetadataFailedException(
                ErrorCode.GET_METADATA_FAILED, String.format(ErrorMessage.GET_METADATA_FAILED, storageErrorCode(cause)), cause);
    }

    // ==================== 下载 ====================

    private static Function<Throwable, S3ClientException> copyFailure() {
        return cause -> new CopyFailedException(
                ErrorCode.COPY_FAILED, String.format(ErrorMessage.COPY_FAILED, storageErrorCode(cause)), cause);
    }

    private static Function<Throwable, S3ClientException> taggingFailure() {
        return cause -> new TaggingFailedException(
                String.format(ErrorMessage.TAGGING_FAILED, storageErrorCode(cause)), cause);
    }

    private static Function<Throwable, S3ClientException> stsFailure() {
        return cause -> new StsCredentialsFailedException(
                String.format(ErrorMessage.STS_CREDENTIALS_FAILED, storageErrorCode(cause)), cause);
    }

    private static Function<Throwable, S3ClientException> bucketFailure() {
        return cause -> new S3ClientException(
                ErrorCode.BUCKET_OPERATION_FAILED, String.format(ErrorMessage.BUCKET_OPERATION_FAILED, storageErrorCode(cause)), cause);
    }

    @Override
    public void destroy() {
        synchronized (multipartExecutorLock) {
            if (multipartExecutor != null && !multipartExecutor.isShutdown()) {
                multipartExecutor.shutdown();
            }
        }
        for (AWSSecurityTokenService stsClient : stsClients.values()) {
            stsClient.shutdown();
        }
        stsClients.clear();
    }

    /**
     * 获取 target 客户端引用，用于执行门面未覆盖的长尾操作。
     * 客户端生命周期归 Route 管理，调用方不得调用 {@code shutdown()}；
     * 返回引用不参与 in-flight 记账，长耗时操作建议改用 {@link #execute}。
     *
     * @param targetKey 已登记 target key
     * @return target 客户端
     * @throws S3RouteException target 或 Route 状态不符合约束时抛出
     */
    public AmazonS3 amazonS3(String targetKey) {
        return routeTemplate.amazonS3(targetKey);
    }

    /**
     * 在 Route 控制的 in-flight 生命周期内以 target 客户端执行回调。
     * 回调内的 SDK 异常不经本门面翻译，原样抛出。
     *
     * @param targetKey 已登记 target key
     * @param callback  以客户端执行的回调
     * @param <T>       回调返回类型
     * @return 回调返回值
     * @throws S3RouteException target、请求或 Route 状态不符合约束时抛出
     */
    public <T> T execute(String targetKey, Function<AmazonS3, T> callback) {
        return routeTemplate.execute(targetKey, callback);
    }

    /**
     * 上传本地文件（contentLength 由 SDK 依据文件长度自动携带），按配置重试。
     * 大文件自动分片请用 {@link #uploadObjectMultipart(String, String, String, File)}。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PutObjectResult putObject(String targetKey, String bucket, String key, File file) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonNull(file, "file");
        return executeClient("putObject", targetKey,
                () -> retryWithFixedDelay(
                        () -> routeTemplate.execute(targetKey, client -> client.putObject(bucket, key, file)),
                        properties.getRetry().getUploadTimes(), properties.getRetry().getUploadIntervalMs()),
                uploadFailure());
    }

    // ==================== 删除 / 存在性 ====================

    /**
     * 以流上传。可无副作用获知长度的内存流自动补 contentLength；
     * 其余流按 SDK 原生行为执行（建议调用方显式传 {@link #putObject(String, String, String, InputStream, ObjectMetadata)}）。
     * 流不可重放，本方法不参与重试。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PutObjectResult putObject(String targetKey, String bucket, String key, InputStream input) {
        return putObject(targetKey, bucket, key, input, null);
    }

    /**
     * 以流上传并显式携带元数据（metadata 为 null 时按长度探测规则自动补 contentLength）。
     * 流不可重放，本方法不参与重试。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PutObjectResult putObject(String targetKey, String bucket, String key, InputStream input, ObjectMetadata metadata) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonNull(input, "input");
        ObjectMetadata effectiveMetadata = metadata != null ? metadata : buildStreamMetadata(input);
        return executeClient("putObject", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.putObject(bucket, key, input, effectiveMetadata)),
                uploadFailure());
    }

    /**
     * 上传本地文件到生命周期过期前缀路径（桶须已配置对应前缀的过期规则）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PutObjectResult putObjectWithExpirationPrefix(String targetKey, String bucket, String key, File file) {
        return putObject(targetKey, bucket, expirationKey(key), file);
    }

    // ==================== 列举 / 元数据 / 复制 ====================

    /**
     * 获取对象（流式）。对象内容流由调用方负责关闭。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载失败
     */
    public S3Object getObject(String targetKey, String bucket, String key) {
        return getObject(targetKey, bucket, key, 0L, null);
    }

    /**
     * 获取对象（Range 起点 offset，字节偏移 0 表示全量）。
     *
     * @param offset 下载起始字节偏移
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载失败
     */
    public S3Object getObject(String targetKey, String bucket, String key, long offset) {
        return getObject(targetKey, bucket, key, offset, null);
    }

    /**
     * 获取对象（Range 起点 + 指定版本）。
     *
     * @param offset    下载起始字节偏移
     * @param versionId 对象版本号（null 表示最新版本）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载失败
     */
    public S3Object getObject(String targetKey, String bucket, String key, long offset, String versionId) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        if (offset < 0) {
            throw requestIllegal();
        }
        return executeClient("getObject", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    GetObjectRequest request = versionId == null
                            ? new GetObjectRequest(bucket, key)
                            : new GetObjectRequest(bucket, key, versionId);
                    request.setRange(offset);
                    return client.getObject(request);
                }),
                downloadFailure());
    }

    /**
     * 下载对象到本地文件；目标文件已存在时覆盖写。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载或写文件失败
     */
    public File downloadToFile(String targetKey, String bucket, String key, File target) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonNull(target, "target");
        return executeClient("downloadToFile", targetKey, () ->
                        retryWithFixedDelay(() -> {
                            S3Object object = routeTemplate.execute(targetKey, client -> client.getObject(bucket, key));
                            try (InputStream content = object.getObjectContent()) {
                                Files.copy(content, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException exception) {
                                throw downloadFailure().apply(exception);
                            }
                            return target;
                        }, properties.getRetry().getDownloadTimes(), properties.getRetry().getDownloadIntervalMs()),
                downloadFailure());
    }

    /**
     * 断点续传下载：本地已存在部分内容时从已下载字节处 Range 续传追加；
     * 服务端返回 InvalidRange 视为已下载完成（幂等）。
     * saveTo 为空时按 download-directory/bucket/key 组装路径。
     *
     * @param saveTo 本地保存路径（null/空白时使用默认目录）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载或写文件失败
     */
    public File downloadObject(String targetKey, String bucket, String key, String saveTo) {
        return downloadObject(targetKey, bucket, key, saveTo, null);
    }

    /**
     * 带版本号的断点续传下载：版本化桶内下载指定版本；
     * saveTo 为空时按 download-directory/bucket/key 组装路径。
     *
     * <p>同一 versionId 的续传安全（Range 起点对同一版本续传）；
     * 不同 versionId 复用同一 saveTo 的行为不可预期（同断点续传风险条款）。</p>
     *
     * @param saveTo    本地保存路径（null/空白时使用默认目录）
     * @param versionId 版本号（版本化桶内对象版本）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象或版本不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载或写文件失败
     */
    public File downloadObject(String targetKey, String bucket, String key, String saveTo, String versionId) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        String filePath = (saveTo == null || saveTo.trim().isEmpty())
                ? properties.getDownloadDirectory() + bucket + SimpleS3ClientConstant.PATH_SEPARATOR + key
                : saveTo;
        return executeClient("downloadObject", targetKey, () ->
                        retryWithFixedDelay(
                                () -> doResumeDownload(targetKey, bucket, key, filePath, versionId),
                                properties.getRetry().getDownloadTimes(), properties.getRetry().getDownloadIntervalMs()),
                downloadFailure());
    }

    /**
     * 从生命周期过期前缀路径断点续传下载（桶须已配置对应前缀的过期规则）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DownloadFailedException 下载或写文件失败
     */
    public File downloadObjectWithExpirationPrefix(String targetKey, String bucket, String key, String saveTo) {
        return downloadObject(targetKey, bucket, expirationKey(key), saveTo);
    }

    /**
     * 断点续传执行体：Range(本地已下载字节) + RandomAccessFile 追加写；
     * InvalidRange 表示本地已完整，幂等返回；versionId 非空时按指定版本读取。
     */
    private File doResumeDownload(String targetKey, String bucket, String key, String filePath, String versionId)
            throws IOException {
        File localFile = new File(filePath);
        File parent = localFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        long offset = localFile.exists() ? localFile.length() : 0L;
        S3Object object;
        try {
            GetObjectRequest request = new GetObjectRequest(bucket, key);
            request.setRange(offset);
            if (versionId != null) {
                request.setVersionId(versionId);
            }
            object = routeTemplate.execute(targetKey, client -> client.getObject(request));
        } catch (AmazonServiceException exception) {
            if (SimpleS3ClientConstant.S3_ERROR_INVALID_RANGE.equals(exception.getErrorCode())) {
                log.debug("断点续传对象已完整, 跳过下载, 本地字节数: {}", offset);
                return localFile;
            }
            throw exception;
        }
        try (InputStream content = object.getObjectContent();
             RandomAccessFile output = new RandomAccessFile(localFile, SimpleS3ClientConstant.FILE_MODE_READ_WRITE)) {
            output.seek(offset);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = content.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
        return localFile;
    }

    // ==================== 对象标签 ====================

    /**
     * 删除对象（幂等：NoSuchKey 视为成功），按配置重试。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DeleteFailedException 删除失败
     */
    public void deleteObject(String targetKey, String bucket, String key) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        executeClient("deleteObject", targetKey,
                () -> retryWithFixedDelay(
                        () -> routeTemplate.execute(targetKey, client -> {
                            try {
                                client.deleteObject(bucket, key);
                            } catch (AmazonServiceException exception) {
                                if (SimpleS3ClientConstant.S3_ERROR_NO_SUCH_KEY.equals(exception.getErrorCode())) {
                                    return null;
                                }
                                throw exception;
                            }
                            return null;
                        }),
                        properties.getRetry().getUploadTimes(), properties.getRetry().getUploadIntervalMs()),
                deleteFailure());
    }

    /**
     * 删除生命周期过期前缀路径下的对象（幂等）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.DeleteFailedException 删除失败
     */
    public void deleteObjectWithExpirationPrefix(String targetKey, String bucket, String key) {
        deleteObject(targetKey, bucket, expirationKey(key));
    }

    /**
     * 判断对象是否存在。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.BucketNotExistException 桶不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException       存在性检查失败
     */
    public boolean doesObjectExist(String targetKey, String bucket, String key) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        return executeClient("doesObjectExist", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.doesObjectExist(bucket, key)),
                deleteFailure());
    }

    /**
     * 列举桶内全部对象。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.BucketNotExistException 桶不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException     列举失败
     */
    public ObjectListing listObjects(String targetKey, String bucket) {
        return listObjects(targetKey, bucket, null);
    }

    // ==================== 预签名 URL ====================

    /**
     * 按前缀列举桶内对象（prefix 为 null 时列举全部）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.BucketNotExistException 桶不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException     列举失败
     */
    public ObjectListing listObjects(String targetKey, String bucket, String prefix) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        return executeClient("listObjects", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.listObjects(bucket, prefix)),
                listFailure());
    }

    /**
     * 按前缀与最大条数列举桶内对象。
     *
     * @param maxKeys 单次返回最大条数
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.BucketNotExistException 桶不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException     列举失败
     */
    public ObjectListing listObjects(String targetKey, String bucket, String prefix, int maxKeys) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        if (maxKeys < 1) {
            throw requestIllegal();
        }
        return executeClient("listObjects", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.listObjects(
                        new ListObjectsRequest().withBucketName(bucket).withPrefix(prefix).withMaxKeys(maxKeys))),
                listFailure());
    }

    /**
     * 列举桶内对象版本历史。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException 列举失败
     */
    public VersionListing listVersions(String targetKey, String bucket) {
        return listVersions(targetKey, bucket, null);
    }

    /**
     * 按前缀列举桶内对象版本历史。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException 列举失败
     */
    public VersionListing listVersions(String targetKey, String bucket, String prefix) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        return executeClient("listVersions", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.listVersions(
                        new ListVersionsRequest().withBucketName(bucket).withPrefix(prefix))),
                listFailure());
    }

    /**
     * 获取对象元数据。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException    对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.GetMetadataFailedException 元数据获取失败
     */
    public ObjectMetadata getObjectMetadata(String targetKey, String bucket, String key) {
        return getObjectMetadata(targetKey, bucket, key, null);
    }

    /**
     * 获取对象元数据（指定版本）。
     *
     * @param versionId 对象版本号（null 表示最新版本）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException    对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.GetMetadataFailedException 元数据获取失败
     */
    public ObjectMetadata getObjectMetadata(String targetKey, String bucket, String key, String versionId) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        return executeClient("getObjectMetadata", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.getObjectMetadata(
                        new GetObjectMetadataRequest(bucket, key, versionId))),
                getMetadataFailure());
    }

    /**
     * 同 target 内复制对象。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ObjectNotExistException 源对象不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.CopyFailedException     复制失败
     */
    public CopyObjectResult copyObject(String targetKey, String fromBucket, String fromKey, String toBucket, String toKey) {
        requireNonBlank(targetKey);
        requireNonBlank(fromBucket);
        requireNonBlank(fromKey);
        requireNonBlank(toBucket);
        requireNonBlank(toKey);
        return executeClient("copyObject", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.copyObject(fromBucket, fromKey, toBucket, toKey)),
                copyFailure());
    }

    // ==================== 分片上传（显式四步） ====================

    /**
     * 设置对象标签（覆盖已有标签）。
     *
     * @param tags 标签键值对；null 拒绝、空 Map 允许（清空语义）、Key 不可空、
     *             Key/Value UTF-8 字节长度与数量受 S3 协议限制约束
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.TaggingFailedException 参数非法或设置失败
     */
    public void setObjectTagging(String targetKey, String bucket, String key, Map<String, String> tags) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        validateObjectTagging(tags);
        executeClient("setObjectTagging", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    List<Tag> tagList = new ArrayList<>();
                    for (Map.Entry<String, String> entry : tags.entrySet()) {
                        tagList.add(new Tag(entry.getKey(), entry.getValue()));
                    }
                    client.setObjectTagging(new SetObjectTaggingRequest(bucket, key, new ObjectTagging(tagList)));
                    return null;
                }),
                taggingFailure());
    }

    /**
     * 校验对象标签参数（数量/Key 空白/Value null/Key 与 Value 字节长度）。
     */
    private void validateObjectTagging(Map<String, String> tags) {
        if (tags == null) {
            throw new TaggingFailedException(ErrorMessage.TAGGING_NULL);
        }
        if (tags.size() > SimpleS3ClientConstant.MAX_OBJECT_TAGS) {
            throw new TaggingFailedException(String.format(ErrorMessage.TAGGING_TOO_MANY,
                    SimpleS3ClientConstant.MAX_OBJECT_TAGS, tags.size()));
        }
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tagKey = entry.getKey();
            if (tagKey == null || tagKey.trim().isEmpty()) {
                throw new TaggingFailedException(ErrorMessage.TAGGING_KEY_EMPTY);
            }
            String value = entry.getValue();
            if (value == null) {
                throw new TaggingFailedException(String.format(ErrorMessage.TAGGING_VALUE_NULL, tagKey));
            }
            if (tagKey.getBytes(StandardCharsets.UTF_8).length
                    > SimpleS3ClientConstant.MAX_TAG_KEY_BYTES) {
                throw new TaggingFailedException(String.format(ErrorMessage.TAGGING_KEY_TOO_LONG,
                        tagKey, SimpleS3ClientConstant.MAX_TAG_KEY_BYTES));
            }
            if (value.getBytes(StandardCharsets.UTF_8).length
                    > SimpleS3ClientConstant.MAX_TAG_VALUE_BYTES) {
                throw new TaggingFailedException(String.format(ErrorMessage.TAGGING_VALUE_TOO_LONG,
                        tagKey, SimpleS3ClientConstant.MAX_TAG_VALUE_BYTES));
            }
        }
    }

    /**
     * 获取对象标签。
     *
     * @return 标签键值对（无标签时为空 Map）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.TaggingFailedException 获取失败
     */
    public Map<String, String> getObjectTagging(String targetKey, String bucket, String key) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        return executeClient("getObjectTagging", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    List<Tag> tagList = client.getObjectTagging(new GetObjectTaggingRequest(bucket, key)).getTagSet();
                    Map<String, String> result = new HashMap<>();
                    for (Tag tag : tagList) {
                        result.put(tag.getKey(), tag.getValue());
                    }
                    return result;
                }),
                taggingFailure());
    }

    /**
     * 删除对象全部标签。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.TaggingFailedException 删除失败
     */
    public void deleteObjectTagging(String targetKey, String bucket, String key) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        executeClient("deleteObjectTagging", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    client.deleteObjectTagging(new DeleteObjectTaggingRequest(bucket, key));
                    return null;
                }),
                taggingFailure());
    }

    /**
     * 生成下载预签名 URL（附件下载 Disposition + Content-Type 响应头）。
     *
     * @param expirationSecondsNullable 有效时长（秒），null 时用配置默认值
     * @return 配置了 url-prefix 时返回「前缀 + 签名路径」，否则返回完整 URL
     * @throws S3ClientException 有效时长非法
     */
    public String generatePresignedUrl(String targetKey, String bucket, String key, Long expirationSecondsNullable) {
        return generatePresignedUrl(targetKey, bucket, key, expirationSecondsNullable, FileDisposition.DOWNLOAD);
    }

    /**
     * 生成下载预签名 URL（指定 Disposition + Content-Type 响应头）。
     *
     * @param disposition Content-Disposition 模式（下载/内联预览）
     * @return 配置了 url-prefix 时返回「前缀 + 签名路径」，否则返回完整 URL
     * @throws S3ClientException disposition 为 null 或有效时长非法
     */
    public String generatePresignedUrl(String targetKey, String bucket, String key,
                                       Long expirationSecondsNullable, FileDisposition disposition) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        if (disposition == null) {
            throw requestIllegal();
        }
        long expirationSeconds = resolveExpirationSeconds(expirationSecondsNullable);
        return executeClient("generatePresignedUrl", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                            .withMethod(HttpMethod.GET)
                            .withResponseHeaders(new ResponseHeaderOverrides()
                                    .withContentDisposition(disposition.getContentDisposition(key))
                                    .withContentType(ContentTypeHelper.getContentType(key)))
                            .withExpiration(expirationDate(expirationSeconds));
                    return applyUrlPrefix(client.generatePresignedUrl(request));
                }),
                copyFailure());
    }

    /**
     * 生成上传预签名 URL（PUT）。
     *
     * @param expirationSecondsNullable 有效时长（秒），null 时用配置默认值
     * @return 配置了 url-prefix 时返回「前缀 + 签名路径」，否则返回完整 URL
     * @throws S3ClientException 有效时长非法
     */
    public String generateUploadPresignedUrl(String targetKey, String bucket, String key, Long expirationSecondsNullable) {
        return generateUploadPresignedUrl(targetKey, bucket, key, expirationSecondsNullable, null);
    }

    /**
     * 生成上传预签名 URL（PUT，可携带 Content-Type 签名头）。
     *
     * @param contentType 上传请求须携带的 Content-Type（null 表示不限定）
     * @return 配置了 url-prefix 时返回「前缀 + 签名路径」，否则返回完整 URL
     * @throws S3ClientException 有效时长非法
     */
    public String generateUploadPresignedUrl(String targetKey, String bucket, String key,
                                             Long expirationSecondsNullable, String contentType) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        long expirationSeconds = resolveExpirationSeconds(expirationSecondsNullable);
        return executeClient("generateUploadPresignedUrl", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                            .withMethod(HttpMethod.PUT)
                            .withExpiration(expirationDate(expirationSeconds));
                    if (contentType != null) {
                        request.setContentType(contentType);
                    }
                    return applyUrlPrefix(client.generatePresignedUrl(request));
                }),
                copyFailure());
    }

    // ==================== 分片上传（自动一把梭） ====================

    /**
     * url-prefix 配置了（非空）返回「前缀 + 签名路径与查询串」，否则返回完整 URL。
     */
    private String applyUrlPrefix(URL url) {
        String urlPrefix = properties.getPresignedUrl().getUrlPrefix();
        if (urlPrefix == null || urlPrefix.isEmpty()) {
            return url.toString();
        }
        return urlPrefix + url.getFile();
    }

    private long resolveExpirationSeconds(Long expirationSecondsNullable) {
        if (expirationSecondsNullable == null) {
            return properties.getPresignedUrl().getExpirationSeconds();
        }
        if (expirationSecondsNullable <= 0) {
            throw requestIllegal();
        }
        return expirationSecondsNullable;
    }

    private Date expirationDate(long expirationSeconds) {
        return new Date(System.currentTimeMillis() + expirationSeconds * MILLIS_PER_SECOND);
    }

    /**
     * 发起分片上传。
     *
     * @return uploadId
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 发起失败
     */
    public String initiateMultipartUpload(String targetKey, String bucket, String key) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        return executeClient("initiateMultipartUpload", targetKey,
                () -> routeTemplate.execute(targetKey, client ->
                        client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key)).getUploadId()),
                uploadFailure());
    }

    /**
     * 上传分片（File 变体，SDK 按文件长度携带分段大小），按配置重试。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public UploadPartResult uploadPart(String targetKey, String bucket, String key,
                                       String uploadId, int partNumber, File part) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonBlank(uploadId);
        requirePartNumber(partNumber);
        requireNonNull(part, "part");
        return executeClient("uploadPart", targetKey,
                () -> retryWithFixedDelay(
                        () -> routeTemplate.execute(targetKey, client -> client.uploadPart(
                                new UploadPartRequest()
                                        .withBucketName(bucket)
                                        .withKey(key)
                                        .withUploadId(uploadId)
                                        .withPartNumber(partNumber)
                                        .withFile(part)
                                        .withPartSize(part.length()))),
                        properties.getRetry().getUploadTimes(), properties.getRetry().getUploadIntervalMs()),
                uploadFailure());
    }

    /**
     * 上传分片（InputStream 变体，须显式携带分段长度）。流不可重放，不参与重试。
     *
     * @param input         分段内容流
     * @param contentLength 分段内容长度（字节）
     * @return 分段 ETag
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PartETag uploadPart(String targetKey, String bucket, String key,
                               String uploadId, int partNumber, InputStream input, long contentLength) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonBlank(uploadId);
        requirePartNumber(partNumber);
        requireNonNull(input, "input");
        if (contentLength <= 0) {
            throw requestIllegal();
        }
        return executeClient("uploadPart", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.uploadPart(
                        new UploadPartRequest()
                                .withBucketName(bucket)
                                .withKey(key)
                                .withUploadId(uploadId)
                                .withPartNumber(partNumber)
                                .withInputStream(input)
                                .withPartSize(contentLength)).getPartETag()),
                uploadFailure());
    }

    /**
     * 完成分片上传。partNumber 须在 1~10000 且不重复、ETag 非空；
     * 校验通过后按 partNumber 升序副本提交。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 完成失败
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String targetKey, String bucket, String key,
                                                                 String uploadId, List<PartETag> partETags) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonBlank(uploadId);
        List<PartETag> sortedPartETags = validatePartETags(partETags);
        return executeClient("completeMultipartUpload", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.completeMultipartUpload(
                        new CompleteMultipartUploadRequest(bucket, key, uploadId, sortedPartETags))),
                uploadFailure());
    }

    /**
     * 校验分段 ETag 列表并返回按 partNumber 升序的提交副本。
     */
    private List<PartETag> validatePartETags(List<PartETag> partETags) {
        if (partETags == null) {
            throw uploadFailed(ErrorMessage.PART_ETAGS_NULL, null);
        }
        if (partETags.isEmpty()) {
            throw uploadFailed(ErrorMessage.PART_ETAGS_EMPTY, null);
        }
        Set<Integer> partNumbers = new HashSet<>();
        for (PartETag partETag : partETags) {
            if (partETag == null) {
                throw uploadFailed(ErrorMessage.PART_ETAG_NULL, null);
            }
            int partNumber = partETag.getPartNumber();
            if (partNumber < 1 || partNumber > SimpleS3ClientConstant.MAX_MULTIPART_PARTS) {
                throw uploadFailed(String.format(ErrorMessage.PART_ETAG_PART_NUMBER_INVALID,
                        SimpleS3ClientConstant.MAX_MULTIPART_PARTS, partNumber), null);
            }
            if (partETag.getETag() == null || partETag.getETag().trim().isEmpty()) {
                throw uploadFailed(String.format(ErrorMessage.PART_ETAG_ETAG_EMPTY, partNumber), null);
            }
            if (!partNumbers.add(partNumber)) {
                throw uploadFailed(String.format(ErrorMessage.PART_ETAG_PART_NUMBER_DUPLICATE, partNumber), null);
            }
        }
        List<PartETag> sorted = new ArrayList<>(partETags);
        sorted.sort((left, right) -> Integer.compare(left.getPartNumber(), right.getPartNumber()));
        return sorted;
    }

    // ==================== STS 临时凭证 ====================

    /**
     * 中止分片上传，清理已上传分段（幂等：NoSuchUpload 视为成功）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 中止失败
     */
    public void abortMultipartUpload(String targetKey, String bucket, String key, String uploadId) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonBlank(uploadId);
        executeClient("abortMultipartUpload", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    try {
                        client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, uploadId));
                    } catch (AmazonServiceException exception) {
                        if (SimpleS3ClientConstant.S3_ERROR_NO_SUCH_UPLOAD.equals(exception.getErrorCode())) {
                            return null;
                        }
                        throw exception;
                    }
                    return null;
                }),
                uploadFailure());
    }

    /**
     * 列举已上传分段（内部分页聚合，单页 1000）。
     *
     * @return 聚合后的分段集合
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException 列举失败
     */
    public MultipartUploadPartList listParts(String targetKey, String bucket, String key, String uploadId) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonBlank(uploadId);
        return executeClient("listParts", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    List<MultipartUploadPart> allParts = new ArrayList<>();
                    int nextMarker = 0;
                    while (true) {
                        PartListing result = client.listParts(new ListPartsRequest(bucket, key, uploadId)
                                .withPartNumberMarker(nextMarker)
                                .withMaxParts(SimpleS3ClientConstant.LIST_PARTS_PAGE_SIZE));
                        for (PartSummary part : result.getParts()) {
                            allParts.add(new MultipartUploadPart(part.getPartNumber(),
                                    part.getETag(), part.getSize(), part.getLastModified()));
                        }
                        if (!result.isTruncated()) {
                            break;
                        }
                        nextMarker = result.getNextPartNumberMarker();
                    }
                    return new MultipartUploadPartList(allParts, 0);
                }),
                listFailure());
    }

    /**
     * 列举进行中的分段上传（内部分页聚合，单页 1000）。
     *
     * @return 聚合后的进行中上传集合
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.ListFailedException 列举失败
     */
    public MultipartUploadList listMultipartUploads(String targetKey, String bucket) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        return executeClient("listMultipartUploads", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    List<MultipartUpload> allUploads = new ArrayList<>();
                    String nextKeyMarker = null;
                    String nextUploadIdMarker = null;
                    while (true) {
                        MultipartUploadListing result = client.listMultipartUploads(
                                new ListMultipartUploadsRequest(bucket)
                                        .withKeyMarker(nextKeyMarker)
                                        .withUploadIdMarker(nextUploadIdMarker)
                                        .withMaxUploads(SimpleS3ClientConstant.LIST_UPLOADS_PAGE_SIZE));
                        for (com.amazonaws.services.s3.model.MultipartUpload upload : result.getMultipartUploads()) {
                            allUploads.add(new MultipartUpload(upload.getUploadId(),
                                    upload.getKey(), upload.getInitiated()));
                        }
                        if (!result.isTruncated()) {
                            break;
                        }
                        nextKeyMarker = result.getNextKeyMarker();
                        nextUploadIdMarker = result.getNextUploadIdMarker();
                    }
                    return new MultipartUploadList(allUploads, false, null, null);
                }),
                listFailure());
    }

    /**
     * 大文件自动分片上传：文件不超过配置阈值且不超单次上传上限时直传，
     * 否则并发分段上传（批间并发，失败自动 abort 清理已传分段）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PutObjectResult uploadObjectMultipart(String targetKey, String bucket, String key, File file) {
        return uploadObjectMultipart(targetKey, bucket, key, file, properties.getMultipart().getPartSizeMb());
    }

    /**
     * 大文件自动分片上传（指定分段大小 MB）。
     *
     * @param partSizeMB 分段大小（MB，最小 5）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 上传失败
     */
    public PutObjectResult uploadObjectMultipart(String targetKey, String bucket, String key,
                                                 File file, int partSizeMB) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(key);
        requireNonNull(file, "file");
        if (!file.exists()) {
            throw uploadFailed(String.format(ErrorMessage.UPLOAD_FAILED, "file not found: " + file.getPath()), null);
        }
        if (partSizeMB < SimpleS3ClientConstant.MIN_PART_SIZE_MB) {
            throw uploadFailed(String.format(ErrorMessage.PART_SIZE_TOO_SMALL,
                    SimpleS3ClientConstant.MIN_PART_SIZE_MB, partSizeMB), null);
        }
        validateMultipartProperties();
        long fileSize = file.length();
        long thresholdBytes = (long) properties.getMultipart().getThresholdMb()
                * SimpleS3ClientConstant.MB_IN_BYTES;
        if (fileSize <= thresholdBytes && fileSize <= SimpleS3ClientConstant.MAX_SINGLE_UPLOAD_BYTES) {
            return putObject(targetKey, bucket, key, file);
        }
        return executeClient("uploadObjectMultipart", targetKey,
                () -> doUploadMultipart(targetKey, bucket, key, file, partSizeMB, fileSize),
                uploadFailure());
    }

    // ==================== 桶管理 ====================

    /**
     * 分段上传执行体：按批并发提交分段任务，任一分段失败取消同批任务并 abort 清理。
     */
    private PutObjectResult doUploadMultipart(String targetKey, String bucket, String key,
                                              File file, int partSizeMB, long fileSize) {
        long partSizeBytes = (long) partSizeMB * SimpleS3ClientConstant.MB_IN_BYTES;
        long partCount = (fileSize + partSizeBytes - 1) / partSizeBytes;
        if (partCount > SimpleS3ClientConstant.MAX_MULTIPART_PARTS) {
            throw uploadFailed(String.format(ErrorMessage.MULTIPART_PART_COUNT_EXCEEDED,
                    SimpleS3ClientConstant.MAX_MULTIPART_PARTS, partCount), null);
        }
        String uploadId = initiateMultipartUpload(targetKey, bucket, key);
        try {
            List<PartETag> allPartETags = Collections.synchronizedList(new ArrayList<>());
            int concurrency = properties.getMultipart().getConcurrency();
            for (int batchStart = 0; batchStart < partCount; batchStart += concurrency) {
                List<Future<PartETag>> futures = new ArrayList<>();
                for (int offsetInBatch = 0; offsetInBatch < concurrency
                        && batchStart + offsetInBatch < partCount; offsetInBatch++) {
                    int partNumber = batchStart + offsetInBatch + 1;
                    long offset = (long) (partNumber - 1) * partSizeBytes;
                    long length = Math.min(partSizeBytes, fileSize - offset);
                    futures.add(multipartExecutor().submit(() ->
                            uploadPartFromFileWithRetry(targetKey, bucket, key, uploadId, partNumber, file, offset, length)));
                }
                for (Future<PartETag> future : futures) {
                    try {
                        allPartETags.add(future.get());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        cancelFutures(futures);
                        throw uploadFailed(exception.getMessage(), exception);
                    } catch (ExecutionException | RuntimeException exception) {
                        cancelFutures(futures);
                        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                                ? exception.getCause() : exception;
                        throw uploadFailed(cause.getMessage(), cause);
                    }
                }
            }
            allPartETags.sort((left, right) -> Integer.compare(left.getPartNumber(), right.getPartNumber()));
            CompleteMultipartUploadResult completeResult =
                    completeMultipartUpload(targetKey, bucket, key, uploadId, allPartETags);
            PutObjectResult result = new PutObjectResult();
            result.setETag(completeResult.getETag());
            log.debug("分段上传完成, 分段数: {}", partCount);
            return result;
        } catch (RuntimeException exception) {
            abortQuietly(targetKey, bucket, key, uploadId);
            throw exception;
        }
    }

    /**
     * 从文件指定偏移上传分段；每次重试由 SDK 重新打开文件片段，重试安全。
     */
    private PartETag uploadPartFromFileWithRetry(String targetKey, String bucket, String key,
                                                 String uploadId, int partNumber, File file, long offset, long length) {
        try {
            return retryWithFixedDelay(
                    () -> routeTemplate.execute(targetKey, client -> client.uploadPart(
                            new UploadPartRequest()
                                    .withBucketName(bucket)
                                    .withKey(key)
                                    .withUploadId(uploadId)
                                    .withPartNumber(partNumber)
                                    .withFile(file)
                                    .withFileOffset(offset)
                                    .withPartSize(length)).getPartETag()),
                    properties.getRetry().getUploadTimes(), properties.getRetry().getUploadIntervalMs());
        } catch (RuntimeException exception) {
            throw uploadFailed(exception.getMessage(), exception);
        }
    }

    private void cancelFutures(List<Future<PartETag>> futures) {
        for (Future<PartETag> future : futures) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    /**
     * 分段上传失败后的清理（幂等语义，清理失败仅告警不覆盖原始异常）。
     */
    private void abortQuietly(String targetKey, String bucket, String key, String uploadId) {
        try {
            abortMultipartUpload(targetKey, bucket, key, uploadId);
        } catch (RuntimeException exception) {
            log.warn("分段上传失败清理未成功, 异常类型: {}", exception.getClass().getName());
        }
    }

    /**
     * 分段上传线程池：首次自动分片时懒创建（固定并发度），容器关闭时统一关停。
     */
    private ExecutorService multipartExecutor() {
        ExecutorService executor = multipartExecutor;
        if (executor == null) {
            synchronized (multipartExecutorLock) {
                executor = multipartExecutor;
                if (executor == null) {
                    executor = Executors.newFixedThreadPool(properties.getMultipart().getConcurrency());
                    multipartExecutor = executor;
                }
            }
        }
        return executor;
    }

    /**
     * 启动期之外的运行期配置校验（自动分片路径），配置非法直接失败暴露矛盾。
     */
    private void validateMultipartProperties() {
        if (properties.getMultipart().getPartSizeMb() < SimpleS3ClientConstant.MIN_PART_SIZE_MB
                || properties.getMultipart().getConcurrency() < SimpleS3ClientConstant.MIN_MULTIPART_CONCURRENCY
                || properties.getMultipart().getThresholdMb() < SimpleS3ClientConstant.MIN_MULTIPART_THRESHOLD_MB) {
            throw requestIllegal();
        }
    }

    /**
     * 生成普通 STS 临时凭证（当前凭据降时效，不限定资源范围）。
     *
     * @return STS 临时凭证
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.StsCredentialsFailedException 凭证获取失败
     */
    public Credentials getNormalStsCredentials(String targetKey) {
        requireNonBlank(targetKey);
        return executeClient("getNormalStsCredentials", targetKey, () ->
                        stsClient(targetKey).getSessionToken(new GetSessionTokenRequest()
                                        .withDurationSeconds(properties.getSts().getDurationSeconds()))
                                .getCredentials(),
                stsFailure());
    }

    // ==================== 事件解析 ====================

    /**
     * 生成桶级降权 STS 凭证（NotResource 限定桶外资源不可操作）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.StsCredentialsFailedException 凭证获取失败
     */
    public Credentials getBucketStsCredentials(String targetKey, String bucket) {
        requireNonBlank(bucket);
        return getPathStsCredentials(targetKey, bucket);
    }

    // ==================== 私有骨架 ====================

    /**
     * 生成目录级降权 STS 凭证（NotResource 限定桶/目录外资源不可操作）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.StsCredentialsFailedException 凭证获取失败
     */
    public Credentials getDirStsCredentials(String targetKey, String bucket, String dir) {
        requireNonBlank(bucket);
        requireNonBlank(dir);
        return getPathStsCredentials(targetKey, bucket + SimpleS3ClientConstant.PATH_SEPARATOR + dir);
    }

    /**
     * 生成路径级降权 STS 凭证（assumeRole + NotResource 策略）。
     * 需要 target 为 ACCESS_KEY 凭据且已配置 sts.role-arn。
     *
     * @param path 资源路径（桶名或 桶名/目录）
     * @return STS 临时凭证
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.StsCredentialsFailedException 凭证获取失败
     */
    public Credentials getPathStsCredentials(String targetKey, String path) {
        requireNonBlank(targetKey);
        requireNonBlank(path);
        String roleArn = properties.getSts().getRoleArn();
        if (roleArn == null || roleArn.trim().isEmpty()) {
            throw stsFailed(String.format(ErrorMessage.STS_CREDENTIALS_FAILED, "sts.role-arn 未配置"), null);
        }
        return executeClient("getPathStsCredentials", targetKey, () -> {
            try {
                String sessionName = path.contains(SimpleS3ClientConstant.PATH_SEPARATOR)
                        ? path.split(SimpleS3ClientConstant.PATH_SEPARATOR)[0] : path;
                String policy = policyObjectMapper.writeValueAsString(
                        PolicyDocumentHelper.builder().statement(Collections.singletonList(
                                PolicyDocumentHelper.Statement.builder()
                                        .notResource(Arrays.asList(
                                                String.format(SimpleS3ClientConstant.RESOURCE_POLICY_ARN_TEMPLATE, path),
                                                String.format(SimpleS3ClientConstant.BUCKET_POLICY_ARN_TEMPLATE, path)))
                                        .build())).build());
                Credentials credentials = stsClient(targetKey).assumeRole(new AssumeRoleRequest()
                                .withDurationSeconds(properties.getSts().getDurationSeconds())
                                .withRoleArn(roleArn)
                                .withRoleSessionName(String.format(
                                        SimpleS3ClientConstant.STS_SESSION_NAME_TEMPLATE, sessionName))
                                .withPolicy(policy))
                        .getCredentials();
                if (credentials == null) {
                    throw stsFailed(String.format(ErrorMessage.STS_CREDENTIALS_FAILED, "credentials 为空"), null);
                }
                return credentials;
            } catch (JsonProcessingException exception) {
                throw stsFailed(String.format(ErrorMessage.STS_CREDENTIALS_FAILED, exception.getMessage()), exception);
            }
        }, stsFailure());
    }

    /**
     * per-target STS 客户端：从 Route 配置读取 target 的 endpoint/region/凭据构建，
     * 懒加载缓存；target 未登记或凭据形态不支持 STS 时抛语义异常。
     */
    private AWSSecurityTokenService stsClient(String targetKey) {
        return stsClients.computeIfAbsent(targetKey, key -> {
            SimpleS3RouteProperties.TargetConfig target = routeProperties.getTargets().get(key);
            if (target == null) {
                throw stsFailed(String.format(ErrorMessage.STS_CREDENTIALS_FAILED, "target 未登记"), null);
            }
            SimpleS3RouteProperties.AuthenticationConfig authentication = target.getAuthentication();
            if (authentication == null || S3RouteAuthenticationType.ACCESS_KEY != authentication.getType()) {
                throw stsFailed(String.format(ErrorMessage.STS_CREDENTIALS_FAILED,
                        "target 凭据形态须为 ACCESS_KEY"), null);
            }
            return stsClientFactory.apply(target);
        });
    }

    /**
     * 默认 STS 客户端构建：静态凭据 + target 的 endpoint/region。
     * 包私有供同包测试真实构建断言（不发起网络请求）。
     */
    AWSSecurityTokenService buildDefaultStsClient(SimpleS3RouteProperties.TargetConfig target) {
        SimpleS3RouteProperties.AuthenticationConfig authentication = target.getAuthentication();
        AWSCredentials credentials = authentication.getSessionToken() == null
                || authentication.getSessionToken().isEmpty()
                ? new BasicAWSCredentials(authentication.getAccessKey(), authentication.getSecretKey())
                : new BasicSessionCredentials(authentication.getAccessKey(), authentication.getSecretKey(),
                authentication.getSessionToken());
        return AWSSecurityTokenServiceClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                target.getEndpoint(), target.getRegion()))
                .build();
    }

    /**
     * 测试注入点：替换 STS 客户端构建函数（包私有，生产代码不可见）。
     */
    S3ClientTemplate overrideStsClientFactory(
            Function<SimpleS3RouteProperties.TargetConfig, AWSSecurityTokenService> factory) {
        this.stsClientFactory = Objects.requireNonNull(factory);
        return this;
    }

    /**
     * 创建桶（幂等：桶已存在时找回并返回既有桶，不抛异常）。
     *
     * @return 创建或既有的桶
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.AccessDeniedException 无权限
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException     桶操作失败
     */
    public Bucket createBucket(String targetKey, String bucket) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        return executeClient("createBucket", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    if (client.doesBucketExistV2(bucket)) {
                        log.debug("桶已存在, 幂等返回");
                        return client.listBuckets().stream()
                                .filter(existing -> existing.getName().equals(bucket))
                                .findFirst()
                                .orElseThrow(() -> bucketFailed(String.format(
                                        ErrorMessage.BUCKET_OPERATION_FAILED, "bucket exists but not listed"), null));
                    }
                    return client.createBucket(bucket);
                }),
                bucketFailure());
    }

    /**
     * 创建版本化桶并设置默认生命周期过期规则（过期前缀 + 过期天数来自配置）。
     *
     * @return 创建或既有的桶
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException 桶操作失败
     */
    public Bucket createVersioningBucket(String targetKey, String bucket) {
        Bucket created = createBucket(targetKey, bucket);
        enableBucketVersioning(targetKey, bucket);
        setBucketLifecycle(targetKey, bucket);
        return created;
    }

    /**
     * 启用桶多版本。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException 桶操作失败
     */
    public void enableBucketVersioning(String targetKey, String bucket) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        executeClient("enableBucketVersioning", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    client.setBucketVersioningConfiguration(new SetBucketVersioningConfigurationRequest(bucket,
                            new BucketVersioningConfiguration().withStatus(BucketVersioningConfiguration.ENABLED)));
                    return null;
                }),
                bucketFailure());
    }

    /**
     * 设置桶生命周期过期规则（前缀过滤 + 过期天数，来自 bucket-lifecycle 配置）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException 桶操作失败
     */
    public void setBucketLifecycle(String targetKey, String bucket) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        executeClient("setBucketLifecycle", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    String expirationPrefix = properties.getBucketLifecycle().getExpirationPrefix();
                    client.setBucketLifecycleConfiguration(new SetBucketLifecycleConfigurationRequest(bucket,
                            new BucketLifecycleConfiguration().withRules(
                                    new BucketLifecycleConfiguration.Rule()
                                            .withId(expirationPrefix + SimpleS3ClientConstant.LIFECYCLE_RULE_SUFFIX)
                                            .withFilter(new LifecycleFilter(
                                                    new LifecyclePrefixPredicate(expirationPrefix)))
                                            .withExpirationInDays(properties.getBucketLifecycle().getExpirationDays())
                                            .withStatus(BucketLifecycleConfiguration.ENABLED))));
                    return null;
                }),
                bucketFailure());
    }

    /**
     * 删除桶（桶非空时失败）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.BucketNotExistException 桶不存在
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException       桶操作失败
     */
    public void deleteBucket(String targetKey, String bucket) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        executeClient("deleteBucket", targetKey,
                () -> routeTemplate.execute(targetKey, client -> {
                    client.deleteBucket(bucket);
                    return null;
                }),
                bucketFailure());
    }

    /**
     * 判断桶是否存在。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.S3ClientException 存在性检查失败
     */
    public boolean doesBucketExist(String targetKey, String bucket) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        return executeClient("doesBucketExist", targetKey,
                () -> routeTemplate.execute(targetKey, client -> client.doesBucketExist(bucket)),
                bucketFailure());
    }

    /**
     * 创建文件夹（以 / 结尾的空对象；已存在时幂等返回），按配置重试。
     *
     * @param folderName 文件夹名（自动补 / 结尾）
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.UploadFailedException 创建失败
     */
    public void createFolder(String targetKey, String bucket, String folderName) {
        requireNonBlank(targetKey);
        requireNonBlank(bucket);
        requireNonBlank(folderName);
        String key = folderName.endsWith(SimpleS3ClientConstant.PATH_SEPARATOR)
                ? folderName : folderName + SimpleS3ClientConstant.PATH_SEPARATOR;
        executeClient("createFolder", targetKey,
                () -> retryWithFixedDelay(
                        () -> routeTemplate.execute(targetKey, client -> {
                            if (client.doesObjectExist(bucket, key)) {
                                return null;
                            }
                            ObjectMetadata metadata = new ObjectMetadata();
                            metadata.setContentLength(0);
                            client.putObject(bucket, key, new ByteArrayInputStream(new byte[0]), metadata);
                            return null;
                        }),
                        properties.getRetry().getUploadTimes(), properties.getRetry().getUploadIntervalMs()),
                uploadFailure());
    }

    /**
     * 解析 S3 事件通知 JSON（宽松解析，未知字段容错）。
     *
     * @throws io.github.surezzzzzz.sdk.s3.client.exception.EventParseFailedException JSON 为空或格式非法
     */
    public S3Event parseEvent(String eventJson) {
        return S3EventParseHelper.parse(eventJson);
    }

    /**
     * 生命周期过期前缀路径包装。
     */
    private String expirationKey(String key) {
        requireNonBlank(key);
        return properties.getBucketLifecycle().getExpirationPrefix() + key;
    }

    /**
     * 为可无副作用探测的内存流组装 contentLength 元数据；其余流（含探测失败的流）返回 null 交由 SDK 原生处理。
     */
    private ObjectMetadata buildStreamMetadata(InputStream input) {
        if (!(input instanceof ByteArrayInputStream)) {
            return null;
        }
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(input.available());
            return metadata;
        } catch (IOException exception) {
            return null;
        }
    }

    private void requireNonBlank(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw requestIllegal();
        }
    }

    private void requireNonNull(Object value, String name) {
        if (value == null) {
            throw requestIllegal();
        }
    }

    private void requirePartNumber(int partNumber) {
        if (partNumber < 1 || partNumber > SimpleS3ClientConstant.MAX_MULTIPART_PARTS) {
            throw requestIllegal();
        }
    }

    private S3ClientException requestIllegal() {
        return new S3ClientException(ErrorCode.REQUEST_ILLEGAL, ErrorMessage.REQUEST_ILLEGAL);
    }

    private UploadFailedException uploadFailed(String message, Throwable cause) {
        return new UploadFailedException(ErrorCode.UPLOAD_FAILED, message, cause);
    }

    private StsCredentialsFailedException stsFailed(String message, Throwable cause) {
        return new StsCredentialsFailedException(message, cause);
    }

    private S3ClientException bucketFailed(String message, Throwable cause) {
        return new S3ClientException(ErrorCode.BUCKET_OPERATION_FAILED, message, cause);
    }

    /**
     * 固定间隔重试执行桥接：重试执行器抛出的受检异常包装为运行时异常，
     * 运行时异常（含 SDK 异常）原样冒泡交由统一翻译。
     */
    private <T> T retryWithFixedDelay(Callable<T> task, int times, long intervalMs) {
        try {
            return retryExecutor.executeWithFixedDelay(task, times, intervalMs);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new S3ClientException(ErrorCode.REQUEST_ILLEGAL, ErrorMessage.REQUEST_ILLEGAL, exception);
        }
    }

    private <T> T executeClient(String operation, String targetKey, Supplier<T> action,
                                Function<Throwable, S3ClientException> fallback) {
        long startMillis = System.currentTimeMillis();
        try {
            T result = action.get();
            log.debug("S3 Client 操作完成, 操作: {}, targetKey: {}, 耗时ms: {}",
                    operation, targetKey, System.currentTimeMillis() - startMillis);
            return result;
        } catch (RuntimeException exception) {
            log.debug("S3 Client 操作失败, 操作: {}, targetKey: {}, 耗时ms: {}, 异常类型: {}",
                    operation, targetKey, System.currentTimeMillis() - startMillis,
                    exception.getClass().getName());
            throw translate(exception, fallback);
        }
    }

    private RuntimeException translate(RuntimeException exception, Function<Throwable, S3ClientException> fallback) {
        if (exception instanceof S3RouteException) {
            return exception;
        }
        if (exception instanceof S3ClientException) {
            return exception;
        }
        if (exception instanceof AmazonServiceException) {
            AmazonServiceException serviceException = (AmazonServiceException) exception;
            String s3ErrorCode = serviceException.getErrorCode();
            if ("NoSuchBucket".equals(s3ErrorCode)) {
                return new BucketNotExistException(ErrorCode.BUCKET_NOT_EXIST, ErrorMessage.BUCKET_NOT_EXIST, exception);
            }
            if ("NoSuchKey".equals(s3ErrorCode) || serviceException.getStatusCode() == 404) {
                return new ObjectNotExistException(ErrorCode.OBJECT_NOT_EXIST, ErrorMessage.OBJECT_NOT_EXIST, exception);
            }
            if ("BucketAlreadyExists".equals(s3ErrorCode) || "BucketAlreadyOwnedByYou".equals(s3ErrorCode)) {
                return new BucketAlreadyExistException(ErrorCode.BUCKET_ALREADY_EXIST, ErrorMessage.BUCKET_ALREADY_EXIST, exception);
            }
            if ("AccessDenied".equals(s3ErrorCode) || "InvalidAccessKeyId".equals(s3ErrorCode)
                    || "SignatureDoesNotMatch".equals(s3ErrorCode) || serviceException.getStatusCode() == 403) {
                return new AccessDeniedException(ErrorCode.ACCESS_DENIED, ErrorMessage.ACCESS_DENIED, exception);
            }
        }
        return fallback.apply(exception);
    }
}
