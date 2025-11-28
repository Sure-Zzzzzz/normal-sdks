package io.github.surezzzzzz.sdk.s3.client;

import cn.hutool.crypto.SecureUtil;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter;
import com.amazonaws.services.s3.model.lifecycle.LifecyclePrefixPredicate;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import io.github.surezzzzzz.sdk.s3.configuration.OssComponent;
import io.github.surezzzzzz.sdk.s3.configuration.OssProperties;
import io.github.surezzzzzz.sdk.s3.constant.OssFileDisposition;
import io.github.surezzzzzz.sdk.s3.exception.client.S3ObjectNotExistException;
import io.github.surezzzzzz.sdk.s3.exception.client.FileNotFoundException;
import io.github.surezzzzzz.sdk.s3.exception.server.CreateBucketFailedException;
import io.github.surezzzzzz.sdk.s3.exception.server.CreateFolderFailedException;
import io.github.surezzzzzz.sdk.s3.exception.server.UploadObjectFailedException;
import io.github.surezzzzzz.sdk.s3.schema.PolicyDocument;
import io.github.surezzzzzz.sdk.s3.util.ContentTypeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/5/31 10:10
 */
@OssComponent
@Slf4j
public class OssClient {

    @Autowired
    private AWSSecurityTokenService awsSecurityTokenService;

    @Autowired
    private AmazonS3 amazonS3;

    @Autowired
    private OssProperties ossProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * PolicyDocument的key的首字母要大写
     */
    private final ObjectMapper capitalizeObjectMapper;

    public OssClient() {
        this.capitalizeObjectMapper = new ObjectMapper();
        this.capitalizeObjectMapper.setPropertyNamingStrategy(new CapitalizeNamingStrategy());
    }

    /**
     * 生成一个临时的授权给用户，用户可以凭借该授权在期限内进行受限（仅put）操作。
     * 其中RoleArn需要用提前创建
     *
     * @return
     */
    @Deprecated
    public Credentials getBucketOnlyPutStsCredentials(String bucketName) throws Exception {
        AssumeRoleRequest req = new AssumeRoleRequest()
                .withDurationSeconds(ossProperties.getStsDurationSeconds())
                .withRoleArn(ossProperties.getRoleArn())
                .withRoleSessionName(String.format(PolicyDocument.SESSION_NAME_TEMPLATE, bucketName))
                .withPolicy(capitalizeObjectMapper.writeValueAsString(
                        PolicyDocument.builder().statement(
                                Arrays.asList(PolicyDocument.Statement.builder()
                                        .notResource(Arrays.asList(String.format(PolicyDocument.RESOURCE_POLICY_ARN_TEMPLATE, bucketName),
                                                String.format(PolicyDocument.BUCKET_POLICY_ARN_TEMPLATE, bucketName)))
                                        .build())
                        ).build()
                ));
        log.debug("获取存储桶STS凭证请求已发送");
        return awsSecurityTokenService.assumeRole(req).getCredentials();
    }

    public Credentials getBucketStsCredentials(String bucketName) throws Exception {
        return getPathStsCredentials(bucketName);
    }

    public Credentials getDirStsCredentials(String bucketName, String dir) throws Exception {
        return getPathStsCredentials(bucketName + "/" + dir);
    }

    /**
     * 生成一个临时的授权给用户，用户可以凭借该授权在期限内对路径进行受限操作。
     * 其中RoleArn需要用xsky的cli提前创建
     *
     * @param path
     * @return
     * @throws Exception
     */
    public Credentials getPathStsCredentials(String path) throws Exception {
        AssumeRoleRequest req = new AssumeRoleRequest()
                .withDurationSeconds(ossProperties.getStsDurationSeconds())
                .withRoleArn(ossProperties.getRoleArn())
                .withRoleSessionName(String.format(PolicyDocument.SESSION_NAME_TEMPLATE, path.split("/")[0]))
                .withPolicy(capitalizeObjectMapper.writeValueAsString(
                        PolicyDocument.builder().statement(
                                Arrays.asList(PolicyDocument.Statement.builder()
                                        .notResource(Arrays.asList(String.format(PolicyDocument.RESOURCE_POLICY_ARN_TEMPLATE, path),
                                                String.format(PolicyDocument.BUCKET_POLICY_ARN_TEMPLATE, path)))
                                        .build())
                        ).build()
                ));
        log.debug("获取路径STS凭证请求已发送");
        return awsSecurityTokenService.assumeRole(req).getCredentials();
    }

    /**
     * 生成一个临时的授权给用户，用户可以凭借该授权在期限内进行操作，没有额外权限控制。
     *
     * @return
     */
    public Credentials getNormalStsCredentials() {
        return awsSecurityTokenService.getSessionToken(
                new GetSessionTokenRequest().withDurationSeconds(ossProperties.getStsDurationSeconds())
        ).getCredentials();
    }

    /**
     * 将文件写入本地路径，支持断点续写
     *
     * @param bucketName
     * @param objectKey
     * @param saveTo
     * @return
     */
    public S3Object downloadObject(String bucketName, String objectKey, String saveTo) throws IOException {
        return downloadObjectWithRetry(bucketName, objectKey, saveTo, 0, 0);
    }

    /**
     * 如果桶是有前缀匹配删除规则的，需要用这个
     *
     * @param bucketName
     * @param objectKey
     * @param saveTo
     * @return
     * @throws IOException
     */
    public S3Object downloadObjectWithExpirationPrefix(String bucketName, String objectKey, String saveTo) throws IOException {
        return downloadObject(bucketName, ossProperties.getBucketExpirationPrefix() + objectKey, saveTo);
    }

    private S3Object downloadObjectWithRetry(String bucketName, String objectKey, String saveTo, int retryCount, long accumulatedRetryTime) throws IOException {
        S3Object s3Object = null;
        InputStream inputStream = null;
        RandomAccessFile outputStream = null;
        long retryInterval = 30000; // 每次重试间隔 30 秒

        long startTime = System.currentTimeMillis();
        String filePath = null;
        try {
            // 检查本地文件是否存在，并获取已下载的字节数
            filePath = StringUtils.isNotEmpty(saveTo) ? saveTo : ossProperties.getDownloadDirectory() + File.separator + bucketName + File.separator + objectKey;
            File localFile = new File(filePath);
            if (localFile.exists()) {
                localFile.delete(); // 删除已存在的文件
            }
            localFile.getParentFile().mkdirs(); // 创建必要的父目录
            long localFileSize = 0;

            // 获取对象
            s3Object = getObject(bucketName, objectKey, localFileSize);
            if (s3Object == null) {
                log.info("对象不存在");
                throw new S3ObjectNotExistException("对象不存在");
            }

            inputStream = s3Object.getObjectContent();

            // 创建 RandomAccessFile 对象，用于断点续写
            outputStream = new RandomAccessFile(localFile, "rw");
            outputStream.seek(localFileSize); // 将文件指针移到已下载的位置

            // 将对象内容写入本地文件
            byte[] buffer = new byte[8192]; // 8 KB 缓冲区
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            log.info("对象下载成功");
        } catch (Exception e) {
            log.debug("downloadObjectWithRetry e:", e);
            // 如果文件已经存在且下载完成，则直接返回 为什么在这里判断，是因为文件已经存在且下载完成时获取到的localFileSize就是最终长度，传给s3的时候会报InvalidRange
            if (e instanceof AmazonS3Exception && ((AmazonS3Exception) e).getErrorCode().equals("InvalidRange")) {
                log.info("文件已存在且已下载完成，跳过下载");
                return s3Object;
            }
            if (e instanceof AmazonS3Exception && ((AmazonS3Exception) e).getErrorCode().equals("NoSuchKey")) {
                log.info("对象不存在");
                throw new S3ObjectNotExistException("对象不存在");
            }
            // 计算当前重试的执行时间
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 处理重试逻辑
            retryCount++;
            accumulatedRetryTime += executionTime + retryInterval;
            if (retryCount < ossProperties.getMaxDownloadRetryTimes() && accumulatedRetryTime < ossProperties.getMaxDownloadRetrySeconds() * 1000) { // 转换为毫秒
                log.info("下载失败，开始重试...");
                try {
                    Thread.sleep(retryInterval); // 等待一段时间再重试
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                downloadObjectWithRetry(bucketName, objectKey, saveTo, retryCount, accumulatedRetryTime);
            } else {
                log.error("下载失败，已达到最大重试次数或累计重试时间超过限制");
                throw e;
            }
        } finally {
            // 关闭输入输出流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 关闭 S3Object 流
            if (s3Object != null) {
                try {
                    s3Object.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            log.debug("获取对象信息成功");
        } catch (JsonProcessingException e) {
            //什么都不干
        }
        return s3Object;
    }


    /**
     * 获取对象，并设置下载范围
     *
     * @param bucketName
     * @param objectKey
     * @param localFileSize
     * @return
     */
    public S3Object getObject(String bucketName, String objectKey, long localFileSize) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
        getObjectRequest.setRange(localFileSize);
        return amazonS3.getObject(getObjectRequest);
    }

    /**
     * 获取完整对象
     *
     * @param bucketName
     * @param objectKey
     * @return
     */
    public S3Object getFullObject(String bucketName, String objectKey) {
        return getObject(bucketName, objectKey, 0);
    }

    /**
     * 获取对象，并设置下载范围和版本号
     *
     * @param bucketName
     * @param objectKey
     * @param localFileSize
     * @param versionId     版本号
     * @return
     */
    public S3Object getObject(String bucketName, String objectKey, long localFileSize, String versionId) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey, versionId);
        getObjectRequest.setRange(localFileSize);
        return amazonS3.getObject(getObjectRequest);
    }

    /**
     * 创建带多版本和对象过期策略的存储桶
     *
     * @param bucketName
     * @return
     */
    public Bucket createVersioningAndDefaultLifecycleBucket(String bucketName) {
        try {
            Bucket bucket = createS3Bucket(bucketName);
            // 启用多版本
            enableBucketVersioning(bucketName);
            // 设置桶的对象过期策略
            setDefaultBucketLifecycle(bucketName);
            return bucket;
        } catch (Exception e) {
            log.error("创建存储桶失败：", e);
            throw new CreateBucketFailedException("创建存储桶失败", e);
        }
    }

    /**
     * 创建 S3 存储桶
     *
     * @param bucketName
     * @return
     */
    public Bucket createS3Bucket(String bucketName) {
        if (amazonS3.doesBucketExistV2(bucketName)) {
            log.info("存储桶已存在");
            return amazonS3.listBuckets().stream()
                    .filter(bucket -> bucket.getName().equals(bucketName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("存储桶已存在，但无法获取存储桶信息"));
        }
        // 创建存储桶
        log.info("开始创建存储桶");
        return amazonS3.createBucket(new CreateBucketRequest(bucketName));
    }

    /**
     * 启用多版本
     *
     * @param bucketName
     */
    public void enableBucketVersioning(String bucketName) {

        BucketVersioningConfiguration versioningConfiguration = new BucketVersioningConfiguration().withStatus(BucketVersioningConfiguration.ENABLED);
        SetBucketVersioningConfigurationRequest versioningRequest = new SetBucketVersioningConfigurationRequest(bucketName, versioningConfiguration);
        amazonS3.setBucketVersioningConfiguration(versioningRequest);

    }

    /**
     * 设置桶的对象过期策略
     *
     * @param bucketName
     */
    public void setDefaultBucketLifecycle(String bucketName) {
        BucketLifecycleConfiguration lifecycleConfiguration = new BucketLifecycleConfiguration().withRules(
                new BucketLifecycleConfiguration.Rule()
                        .withId(ossProperties.getBucketExpirationPrefix() + "rule")
                        .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(ossProperties.getBucketExpirationPrefix())))
                        .withExpirationInDays(ossProperties.getBucketExpirationDays())
                        .withStatus(BucketLifecycleConfiguration.ENABLED)
        );
        SetBucketLifecycleConfigurationRequest setBucketLifecycleConfiguration = new SetBucketLifecycleConfigurationRequest(bucketName, lifecycleConfiguration);
        amazonS3.setBucketLifecycleConfiguration(setBucketLifecycleConfiguration);
    }

    /**
     * 创建过期时间半年的桶
     *
     * @param bucketName
     * @return
     */
    public void createHalfYearBucket(String bucketName) {
        try {
            // 检查存储桶是否存在
            if (amazonS3.doesBucketExistV2(bucketName)) {
                log.info("存储桶已存在");
                return;
            }
            // 创建存储桶
            createS3Bucket(bucketName);
            // 启用多版本
            enableBucketVersioning(bucketName);

            // 设置桶的对象过期策略
            BucketLifecycleConfiguration lifecycleConfiguration = new BucketLifecycleConfiguration().withRules(
                    new BucketLifecycleConfiguration.Rule()
                            .withId(ossProperties.getBucketExpirationPrefix() + "rule")
                            .withExpirationInDays(180)
                            .withStatus(BucketLifecycleConfiguration.ENABLED)
            );
            SetBucketLifecycleConfigurationRequest setBucketLifecycleConfiguration = new SetBucketLifecycleConfigurationRequest(bucketName, lifecycleConfiguration);
            amazonS3.setBucketLifecycleConfiguration(setBucketLifecycleConfiguration);

        } catch (Exception e) {
            log.error("创建存储桶失败：", e);
            throw new CreateBucketFailedException("创建存储桶失败", e);
        }
    }

    /**
     * 上传对象
     *
     * @param bucketName
     * @param objectKey
     * @param file
     */
    public void uploadObject(String bucketName, String objectKey, File file) {
        uploadObjectWithRetry(bucketName, objectKey, file, 0, 0);
    }

    public void uploadObjectWithExpirationPrefix(String bucketName, String objectKey, File file) {
        uploadObject(bucketName, ossProperties.getBucketExpirationPrefix() + objectKey, file);
    }

    private void uploadObjectWithRetry(String bucketName, String objectKey, File file, int retryCount, long accumulatedRetryTime) {
        InputStream inputStream = null;
        long retryInterval = 30000; // 每次重试间隔 30 秒
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        try {
            // 检查文件是否存在
            if (!file.exists()) {
                log.error("文件不存在");
                throw new FileNotFoundException("文件不存在");
            }
            // 创建 PutObjectRequest
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, file);
            // 上传对象
            amazonS3.putObject(putObjectRequest);
            log.info("对象上传成功");
        } catch (Exception e) {
            log.debug("uploadObjectWithRetry e:", e);
            // 计算当前重试的执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            // 处理重试逻辑
            retryCount++;
            accumulatedRetryTime += executionTime + retryInterval;
            if (retryCount < ossProperties.getMaxUploadRetryTimes() && accumulatedRetryTime < ossProperties.getMaxUploadRetrySeconds() * 1000) { // 转换为毫秒
                log.info("上传失败，开始重试...");
                try {
                    Thread.sleep(retryInterval); // 等待一段时间再重试
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                uploadObjectWithRetry(bucketName, objectKey, file, retryCount, accumulatedRetryTime);
            } else {
                log.error("上传失败，已达到最大重试次数或累计重试时间超过限制");
                throw new UploadObjectFailedException("上传对象失败", e);
            }

        } finally {
            // 关闭输入流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String generator32CharactersBucketName(String symbol) {
        return SecureUtil.md5(symbol);
    }

//    private void deleteObjectWithRetry(String bucketName, String objectKey, int retryCount, long accumulatedRetryTime) {
//        long retryInterval = 30000; // 每次重试间隔 30 秒
//        // 记录开始时间
//        long startTime = System.currentTimeMillis();
//        try {
//            // 创建 DeleteObjectRequest，指定 BypassGovernanceRetention
//            DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, objectKey)
//                    .withBypassGovernanceRetention(true);
//
//            // 删除对象
//            amazonS3.deleteObject(deleteObjectRequest);
//
//            log.info("对象删除成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
//        } catch (Exception e) {
//            log.debug("deleteObjectWithRetry e:", e);
//            // 计算当前重试的执行时间
//            long executionTime = System.currentTimeMillis() - startTime;
//            // 处理重试逻辑
//            retryCount++;
//            accumulatedRetryTime += executionTime + retryInterval;
//            if (retryCount < ossProperties.getMaxDeleteRetryTimes() && accumulatedRetryTime < ossProperties.getMaxDeleteRetrySeconds() * 1000) { // 转换为毫秒
//                log.info("删除失败，重试第 {} 次...", retryCount);
//                try {
//                    Thread.sleep(retryInterval); // 等待一段时间再重试
//                } catch (InterruptedException interruptedException) {
//                    interruptedException.printStackTrace();
//                }
//                deleteObjectWithRetry(bucketName, objectKey, retryCount, accumulatedRetryTime);
//            } else {
//                log.error("删除失败，已达到最大重试次数或累计重试时间超过限制");
//                throw new DeleteObjectFailedException();
//            }
//        }
//    }

    /**
     * 创建文件夹
     *
     * @param bucketName
     * @param folderName
     */
    public void createFolder(String bucketName, String folderName) {
        createFolderWithRetry(bucketName, folderName, 0, 0);
    }

    private void createFolderWithRetry(String bucketName, String folderName, int retryCount, long accumulatedRetryTime) {
        long retryInterval = 30000; // 每次重试间隔 30 秒
        long startTime = System.currentTimeMillis();

        try {
            // 确保文件夹名称以斜杠结尾
            if (!folderName.endsWith("/")) {
                folderName = folderName + "/";
            }

            // 检查文件夹是否已存在
            if (amazonS3.doesObjectExist(bucketName, folderName)) {
                log.info("文件夹已存在");
                return; // 如果文件夹存在，直接返回
            }

            // 创建空的 ObjectMetadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(0);

            // 空内容流
            try (InputStream emptyContent = new ByteArrayInputStream(new byte[0])) {
                // 创建文件夹
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, folderName, emptyContent, metadata);
                amazonS3.putObject(putObjectRequest);

                log.info("文件夹创建成功");
            }
        } catch (Exception e) {
            log.debug("createFolderWithRetry e:", e);

            // 计算当前重试的执行时间
            long executionTime = System.currentTimeMillis() - startTime;

            // 处理重试逻辑
            retryCount++;
            accumulatedRetryTime += executionTime + retryInterval;

            if (retryCount < ossProperties.getMaxUploadRetryTimes() &&
                    accumulatedRetryTime < ossProperties.getMaxUploadRetrySeconds() * 1000) { // 转换为毫秒
                log.info("文件夹创建失败，开始重试...");

                try {
                    Thread.sleep(retryInterval); // 等待一段时间再重试
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试时线程中断", interruptedException);
                }

                createFolderWithRetry(bucketName, folderName, retryCount, accumulatedRetryTime);
            } else {
                log.error("文件夹创建失败，已达到最大重试次数或累计重试时间超过限制");
                throw new CreateFolderFailedException("创建文件夹失败", e);
            }
        }
    }

    /**
     * s3官方生成带有过期时间的预签名url，默认下载模式
     *
     * @param bucketName
     * @param objectKey
     * @param expirationSeconds
     * @return
     * @throws Exception
     */
    public String generatePresignedUrl(String bucketName, String objectKey, Long expirationSeconds) throws Exception {
        return generatePresignedUrl(bucketName, objectKey, expirationSeconds, OssFileDisposition.DOWNLOAD);
    }

    /**
     * s3官方生成带有过期时间的预签名url
     *
     * @param bucketName
     * @param objectKey
     * @param expirationSeconds
     * @param disposition       下载 or 预览
     * @return
     * @throws Exception
     */
    public String generatePresignedUrl(String bucketName, String objectKey, Long expirationSeconds, OssFileDisposition disposition) throws Exception {
        expirationSeconds = expirationSeconds == null ? ossProperties.getPresignedUrlExpirationSeconds() : expirationSeconds;
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.GET)
                .withResponseHeaders(
                        new ResponseHeaderOverrides()
                                .withContentDisposition(disposition.getContentDisposition(objectKey))
                                .withContentType(ContentTypeUtil.getContentType(objectKey))
                )
                .withExpiration(
                        new Date(System.currentTimeMillis() + expirationSeconds * 1000)
                );
        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
        log.debug("生成预签名URL成功");
        return ossProperties.getUrlPrefix() + url.getFile();
    }

    /**
     * 自定义get权限的预签名URL
     *
     * @param bucketName
     * @param objectKey
     * @param expirationSeconds
     * @return
     * @throws Exception
     */
    public String customPresignedUrl(String bucketName, String objectKey, Long expirationSeconds) throws Exception {
        expirationSeconds = expirationSeconds == null ? ossProperties.getPresignedUrlExpirationSeconds() : expirationSeconds;
        String canonicalizedResource = String.format("/%s/%s", bucketName, objectKey);
        Date expirationDate = new Date(new Date().getTime() + expirationSeconds * 1000);
        String signStr = "GET\n\n\n" + expirationDate.getTime() + "\n" + canonicalizedResource;
        String algorithm = "HmacSHA1";
        SecretKeySpec signinKey = new SecretKeySpec(ossProperties.getSecretKey().getBytes(), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(signinKey);
        byte[] rawHmac = mac.doFinal(signStr.getBytes());
        String url = String.format("%s%s?AWSAccessKeyId=%s&Expires=%s&Signature=%s", ossProperties.getUrlPrefix(), canonicalizedResource,
                ossProperties.getAccessKey(), expirationDate.getTime(), new String(Base64.getEncoder().encode(rawHmac)));
        log.debug("生成自定义预签名URL成功");
        return url;
    }

    public OffsetDateTime convertToOffsetDateTime(Date date) {
        if (date == null) {
            return null;
        }
        // 将 Date 转换为 Instant
        Instant instant = date.toInstant();
        // 获取系统默认时区
        ZoneId zoneId = ZoneId.systemDefault();
        // 将 Instant 和 ZoneId 转换为 OffsetDateTime
        return instant.atZone(zoneId).toOffsetDateTime();
    }

    /**
     * PolicyDocument的key需要首字母大写
     *
     * @return
     */
    class CapitalizeNamingStrategy extends PropertyNamingStrategy {
        private String capitalize(String name) {
            if (name == null || name.length() == 0) {
                return name;
            }
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        @Override
        public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName) {
            return capitalize(defaultName);
        }

        @Override
        public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
            return capitalize(defaultName);
        }

        @Override
        public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
            return capitalize(defaultName);
        }

        @Override
        public String nameForConstructorParameter(MapperConfig<?> config, AnnotatedParameter ctorParam, String defaultName) {
            return capitalize(defaultName);
        }
    }

}
