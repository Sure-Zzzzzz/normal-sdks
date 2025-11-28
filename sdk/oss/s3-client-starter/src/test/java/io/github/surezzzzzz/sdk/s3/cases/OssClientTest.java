package io.github.surezzzzzz.sdk.s3.cases;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.securitytoken.model.Credentials;
import io.github.surezzzzzz.sdk.s3.OssApplication;
import io.github.surezzzzzz.sdk.s3.client.OssClient;
import io.github.surezzzzzz.sdk.s3.configuration.OssProperties;
import io.github.surezzzzzz.sdk.s3.exception.client.S3ObjectNotExistException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/5/31 11:36
 */
@Slf4j
@SpringBootTest(classes = OssApplication.class)
public class OssClientTest {

    @Autowired
    private OssClient ossClient;

    @Autowired
    private OssProperties ossProperties;

    @Test
    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void smokeTest() throws Exception {
        // 获取当前日期
        LocalDate currentDate = LocalDate.now();

        // 格式化日期
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String id = currentDate.format(formatter);
        // 测试 getStsCredentials 方法
        Credentials normalCredentials = ossClient.getNormalStsCredentials();
        assertNotNull(normalCredentials);
        log.info("STS Credentials: {}", normalCredentials);
        log.info("STS Credentials Expiration: {}", normalCredentials.getExpiration());

        // 测试 createBucket 方法
        String bucketName = id + "-" + "test-bucket";
        Bucket bucket = ossClient.createVersioningAndDefaultLifecycleBucket(bucketName);
        assertNotNull(bucket);
        log.info("Created bucket: {}", bucket.getName());

        // 测试 创建半年的桶
        String halfYear = id + "-" + "half-year";
        ossClient.createHalfYearBucket(halfYear);
//        assertNotNull(halfYearBucket);

        // 测试 带有特殊符号的字符串 生成桶的名字
        String specialName = "!@#$%^&*()~:<>?";
        ossClient.createHalfYearBucket(id + "-" + ossClient.generator32CharactersBucketName(specialName));
//        assertNotNull(specialBucket);
//        log.info("Created specialBucket: {}", specialBucket.getName());

        String objectKey = "test-object.txt";

        // 从类路径中获取文件资源
        InputStream inputStream = getClass().getResourceAsStream("/md5.txt");
        if (inputStream == null) {
            throw new RuntimeException("File not found in classpath");
        }

        // 创建临时文件
        File tempFile;
        try {
            tempFile = File.createTempFile("temp-file", ".txt");
            tempFile.deleteOnExit(); // 程序结束时删除临时文件
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary file", e);
        }
        ossClient.uploadObject(bucketName, objectKey, tempFile);
        ossClient.uploadObjectWithExpirationPrefix(bucketName, objectKey, tempFile);

        // 测试 getFullObject 方法
        try {
            S3Object s3Object = ossClient.getFullObject(bucketName, objectKey);
            assertNotNull(s3Object);
            log.info("Full object: {} from bucket: {}", objectKey, bucketName);
        } catch (Exception e) {
            log.error("Error getting full object: {}", objectKey, e);
        }
        // 测试 getFullObject 方法
        try {
            S3Object s3Object = ossClient.getFullObject(bucketName, new OssProperties().getBucketExpirationPrefix() + objectKey);
            assertNotNull(s3Object);
            log.info("Full object: {} from bucket: {}", objectKey, bucketName);
        } catch (Exception e) {
            log.error("Error getting full object: {}", objectKey, e);
        }

        // 测试 downloadObject 方法
        try {
            ossClient.downloadObject(bucketName, objectKey, null);
            log.info("Downloaded object: {} from bucket: {}", objectKey, bucketName);
        } catch (S3ObjectNotExistException e) {
            log.error("S3 object does not exist: {}", objectKey);
        } catch (Exception e) {
            log.error("Error downloading object: {}", objectKey, e);
        } finally {
            File delete = new File(bucketName + File.separator + objectKey);
            delete.deleteOnExit();
        }


        // 测试 downloadObject 方法
        try {
            ossClient.downloadObjectWithExpirationPrefix(bucketName, objectKey, null);
            log.info("Downloaded object: {} from bucket: {}", objectKey, bucketName);
        } catch (S3ObjectNotExistException e) {
            log.error("S3 object does not exist: {}", objectKey);
        } catch (Exception e) {
            log.error("Error downloading object: {}", objectKey, e);
        } finally {
            File delete = new File(bucketName + File.separator + new OssProperties().getBucketExpirationPrefix() + objectKey);
            delete.deleteOnExit();
        }

        //测试获取受限aksk
        Credentials bucketOnlyPutCredentials = ossClient.getBucketOnlyPutStsCredentials(bucketName);
        assertNotNull(bucketOnlyPutCredentials);
        log.info("bucketOnlyPutCredentials: {}", bucketOnlyPutCredentials);
        log.info("bucketOnlyPutCredentials Expiration: {}", bucketOnlyPutCredentials.getExpiration());

        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicSessionCredentials(
                                bucketOnlyPutCredentials.getAccessKeyId(),
                                bucketOnlyPutCredentials.getSecretAccessKey(),
                                bucketOnlyPutCredentials.getSessionToken())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(ossProperties.getEndpoint(), null))
                .build();

        //基于受限aksk测试往允许的bucket里上传
        PutObjectResult allowPut = amazonS3.putObject(bucketName, "sts-" + objectKey, tempFile);
        assertNotNull(allowPut);

        //基于受限aksk测试从允许的bucket里获取对象
        S3Object allowGet = amazonS3.getObject(bucketName, objectKey);
        assertNotNull(allowGet);

        //基于受限aksk测试往不允许的bucket里上传
        AmazonS3Exception denyPutException = assertThrows(AmazonS3Exception.class,
                () -> {
                    amazonS3.putObject(halfYear, "sts-" + objectKey, tempFile);
                });
        log.info("denyPutException:", denyPutException);
        assertEquals(403, denyPutException.getStatusCode());

        //基于受限aksk测试获取对象,因为上面没传进去，所以是404
        AmazonS3Exception denyGetException = assertThrows(AmazonS3Exception.class,
                () -> {
                    amazonS3.getObject(halfYear, "sts-" + objectKey);
                });
        log.info("denyGetException:", denyGetException);
        assertEquals(404, denyGetException.getStatusCode());
        amazonS3.shutdown();

        String existUrl = ossClient.generatePresignedUrl(bucketName, objectKey, 15759360L);
        log.info("existUrl:{}", existUrl);
        assertNotNull(existUrl);

        //能生成，但是404
        String notExistUrl = ossClient.generatePresignedUrl(bucketName, "666", 36000L);
        log.info("notExistUrl:{}", notExistUrl);
        assertNotNull(notExistUrl);

        //半年
        String permanentUrl = ossClient.customPresignedUrl(bucketName, objectKey, 15759360L);
        log.info("permanentUrl:{}", permanentUrl);
        assertNotNull(permanentUrl);

        //测试获取桶+路径的受限aksk
        Credentials dirCredentials = ossClient.getDirStsCredentials(bucketName, bucketName);
        assertNotNull(dirCredentials);
        log.info("dirCredentials: {}", dirCredentials);
        log.info("dirCredentials Expiration: {}", dirCredentials.getExpiration());

        AmazonS3 dirCredentialsAmazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicSessionCredentials(
                                dirCredentials.getAccessKeyId(),
                                dirCredentials.getSecretAccessKey(),
                                dirCredentials.getSessionToken())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(ossProperties.getEndpoint(), null))
                .build();

        //基于受限aksk测试往允许的bucket里上传
        PutObjectResult dirAllowPut = dirCredentialsAmazonS3.putObject(bucketName, bucketName + "/sts-" + objectKey, tempFile);
        assertNotNull(dirAllowPut);

        //基于受限aksk测试从允许的bucket里获取对象
        S3Object dirAllowGet = dirCredentialsAmazonS3.getObject(bucketName, bucketName + "/sts-" + objectKey);
        assertNotNull(dirAllowGet);

        //基于受限aksk测试往不允许的bucket里上传
        AmazonS3Exception dirDenyPutException = assertThrows(AmazonS3Exception.class,
                () -> {
                    dirCredentialsAmazonS3.putObject(bucketName, "dir-" + objectKey, tempFile);
                });
        log.info("dirDenyPutException:", dirDenyPutException);
        assertEquals(403, dirDenyPutException.getStatusCode());

        //基于受限aksk测试获取对象,因为上面没传进去，所以是404
        AmazonS3Exception dirNotFoundException = assertThrows(AmazonS3Exception.class,
                () -> {
                    dirCredentialsAmazonS3.getObject(bucketName, "dir-" + objectKey);
                });
        log.info("dirNotFoundException:", dirNotFoundException);
        assertEquals(404, dirNotFoundException.getStatusCode());
        //基于受限aksk测试获取已存在对象
        AmazonS3Exception dirDenyGetException = assertThrows(AmazonS3Exception.class,
                () -> {
                    dirCredentialsAmazonS3.getObject(bucketName, "sts-" + objectKey);
                });
        log.info("dirDenyGetException:", dirDenyGetException);
        assertEquals(403, dirDenyGetException.getStatusCode());
        dirCredentialsAmazonS3.shutdown();

    }
}
