package io.github.surezzzzzz.sdk.s3.client.test.cases;

import com.amazonaws.services.s3.model.*;
import io.github.surezzzzzz.sdk.s3.client.model.MultipartUploadList;
import io.github.surezzzzzz.sdk.s3.client.model.MultipartUploadPartList;
import io.github.surezzzzzz.sdk.s3.client.template.S3ClientTemplate;
import io.github.surezzzzzz.sdk.s3.client.test.SimpleS3ClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3 Client 双版本 MinIO 端到端验证：语义门面全链路（建桶、File/Stream 上传、
 * 回读、下载到文件、断点续传、列举、复制、元数据、对象标签、预签名 GET/PUT、
 * 删除幂等、分片四步与列举、自动分片一把梭、版本化桶、生命周期前缀、文件夹），
 * 数据全部由测试自行创建与回读。
 *
 * <p>运行前需要先启动 simple-s3-route-starter 的
 * {@code docker-compose.s3-e2e-matrix.yml}（双 MinIO fixture 复用，容器不关）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleS3ClientTestApplication.class)
class S3ClientEndToEndTest {

    private static final int HEALTH_TIMEOUT_MS = 60000;

    private static final String TARGET_A = "minio-2023";

    private static final String TARGET_B = "minio-2025";

    @Autowired
    private S3ClientTemplate client;

    @BeforeAll
    static void awaitMinioReady() throws Exception {
        awaitHealthy("http://127.0.0.1:19000");
        awaitHealthy("http://127.0.0.1:19001");
    }

    private static void awaitHealthy(String baseUrl) throws Exception {
        long deadline = System.currentTimeMillis() + HEALTH_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + "/minio/health/live").openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                if (connection.getResponseCode() == 200) {
                    log.info("MinIO 就绪: {}", baseUrl);
                    return;
                }
            } catch (IOException ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("MinIO 未在超时内就绪: " + baseUrl);
    }

    @Test
    void semanticFacadeRoundTripsAcrossBothTargets() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucketA = "e2e-s3-client-a-" + runMarker;
        String bucketB = "e2e-s3-client-b-" + runMarker;

        client.createBucket(TARGET_A, bucketA);
        client.createBucket(TARGET_B, bucketB);
        assertTrue(client.doesBucketExist(TARGET_A, bucketA), "建桶后存在性应为真");

        String fileContent = "e2e-client-file-" + runMarker;
        File file = File.createTempFile("e2e-s3-client", ".txt");
        Files.write(file.toPath(), fileContent.getBytes(StandardCharsets.UTF_8));
        client.putObject(TARGET_A, bucketA, "reports/file-object.txt", file);

        String streamContent = "e2e-client-stream-" + runMarker;
        client.putObject(TARGET_B, bucketB, "streams/stream-object.txt",
                new ByteArrayInputStream(streamContent.getBytes(StandardCharsets.UTF_8)));

        assertEquals(fileContent, readObject(client.getObject(TARGET_A, bucketA, "reports/file-object.txt")),
                "File 路径上传内容应回读一致");
        assertEquals(streamContent, readObject(client.getObject(TARGET_B, bucketB, "streams/stream-object.txt")),
                "Stream 路径上传内容应回读一致（无 metadata 自动补 contentLength）");
        log.info("双路径上传回读一致, file 字节数: {}, stream 字节数: {}",
                fileContent.length(), streamContent.length());

        File downloaded = File.createTempFile("e2e-s3-client-download", ".txt");
        Files.write(downloaded.toPath(), "stale".getBytes(StandardCharsets.UTF_8));
        client.downloadToFile(TARGET_A, bucketA, "reports/file-object.txt", downloaded);
        assertEquals(fileContent, new String(Files.readAllBytes(downloaded.toPath()), StandardCharsets.UTF_8),
                "downloadToFile 应覆盖旧内容写入");

        ObjectListing listing = client.listObjects(TARGET_A, bucketA, "reports/");
        assertEquals(1, listing.getObjectSummaries().size(), "前缀列举应命中一个对象");
        assertEquals("reports/file-object.txt", listing.getObjectSummaries().get(0).getKey());

        client.copyObject(TARGET_A, bucketA, "reports/file-object.txt", bucketA, "archive/copied.txt");
        assertTrue(client.doesObjectExist(TARGET_A, bucketA, "archive/copied.txt"), "复制后目标对象应存在");
        assertEquals(fileContent, readObject(client.getObject(TARGET_A, bucketA, "archive/copied.txt")),
                "复制内容应与源一致");

        ObjectMetadata metadata = client.getObjectMetadata(TARGET_B, bucketB, "streams/stream-object.txt");
        assertEquals(streamContent.getBytes(StandardCharsets.UTF_8).length, metadata.getContentLength(),
                "元数据长度应与上传字节一致");

        String presigned = client.generatePresignedUrl(TARGET_A, bucketA, "reports/file-object.txt", 300L);
        assertEquals(fileContent, httpGetBodyAndDisposition(presigned, "file-object.txt"),
                "预签名 URL 应可读取同一内容且携带附件下载 Disposition");

        client.deleteObject(TARGET_A, bucketA, "reports/file-object.txt");
        assertFalse(client.doesObjectExist(TARGET_A, bucketA, "reports/file-object.txt"),
                "删除后对象不应存在");
        client.deleteObject(TARGET_A, bucketA, "reports/file-object.txt");
        log.info("重复删除幂等不抛异常");

        client.deleteObject(TARGET_A, bucketA, "archive/copied.txt");
        client.deleteBucket(TARGET_A, bucketA);
        client.deleteObject(TARGET_B, bucketB, "streams/stream-object.txt");
        client.deleteBucket(TARGET_B, bucketB);
        assertFalse(client.doesBucketExist(TARGET_B, bucketB), "删桶后存在性应为假");
        assertTrue(file.delete());
        assertTrue(downloaded.delete());
        log.info("语义门面 E2E 全链路闭环完成");
    }

    @Test
    void downloadObjectResumesFromPartialLocalFile() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucket = "e2e-s3-client-resume-" + runMarker;
        client.createBucket(TARGET_A, bucket);

        String fullContent = "0123456789abcdef-" + runMarker;
        client.putObject(TARGET_A, bucket, "resume/target.bin",
                new ByteArrayInputStream(fullContent.getBytes(StandardCharsets.UTF_8)));

        File partial = File.createTempFile("e2e-resume-partial", ".bin");
        byte[] fullBytes = fullContent.getBytes(StandardCharsets.UTF_8);
        Files.write(partial.toPath(), Arrays.copyOfRange(fullBytes, 0, 8));

        File resumed = client.downloadObject(TARGET_A, bucket, "resume/target.bin", partial.getPath());
        assertEquals(fullContent, new String(Files.readAllBytes(resumed.toPath()), StandardCharsets.UTF_8),
                "断点续传应从本地已下载 8 字节处续传拼齐");

        File complete = client.downloadObject(TARGET_A, bucket, "resume/target.bin", partial.getPath());
        assertEquals(fullContent, new String(Files.readAllBytes(complete.toPath()), StandardCharsets.UTF_8),
                "本地已完整时再次下载应幂等保持内容一致");

        client.deleteObject(TARGET_A, bucket, "resume/target.bin");
        client.deleteBucket(TARGET_A, bucket);
        assertTrue(partial.delete());
        log.info("断点续传 E2E 完成, 全量字节数: {}", fullBytes.length);
    }

    @Test
    void multipartFourStepsAndListingRoundTrip() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucket = "e2e-s3-client-mp-" + runMarker;
        client.createBucket(TARGET_A, bucket);

        byte[] partOne = new byte[5 * 1024 * 1024];
        for (int i = 0; i < partOne.length; i++) {
            partOne[i] = (byte) (i % 251);
        }
        byte[] partTwo = ("e2e-mp-part-two-" + runMarker).getBytes(StandardCharsets.UTF_8);
        File partFileOne = File.createTempFile("e2e-mp-part-1", ".bin");
        File partFileTwo = File.createTempFile("e2e-mp-part-2", ".bin");
        Files.write(partFileOne.toPath(), partOne);
        Files.write(partFileTwo.toPath(), partTwo);

        String uploadId = client.initiateMultipartUpload(TARGET_A, bucket, "mp/merged.txt");

        PartETag etagOne = client.uploadPart(TARGET_A, bucket, "mp/merged.txt", uploadId, 1, partFileOne).getPartETag();
        PartETag etagTwo = client.uploadPart(TARGET_A, bucket, "mp/merged.txt", uploadId, 2,
                new ByteArrayInputStream(partTwo), partTwo.length);

        String bucketB = "e2e-s3-client-mp-l-" + runMarker;
        client.createBucket(TARGET_B, bucketB);
        String listingUploadId = client.initiateMultipartUpload(TARGET_B, bucketB, "mp/in-progress.txt");
        MultipartUploadList inProgress = client.listMultipartUploads(TARGET_B, bucketB);
        assertTrue(inProgress.getUploads().stream()
                        .anyMatch(upload -> listingUploadId.equals(upload.getUploadId())),
                "进行中分段上传应可被列举");
        client.abortMultipartUpload(TARGET_B, bucketB, "mp/in-progress.txt", listingUploadId);
        client.deleteBucket(TARGET_B, bucketB);

        MultipartUploadPartList parts = client.listParts(TARGET_A, bucket, "mp/merged.txt", uploadId);
        assertEquals(2, parts.getParts().size(), "已上传分段应可被列举");

        client.completeMultipartUpload(TARGET_A, bucket, "mp/merged.txt", uploadId,
                Arrays.asList(etagTwo, etagOne));

        byte[] merged = new byte[partOne.length + partTwo.length];
        System.arraycopy(partOne, 0, merged, 0, partOne.length);
        System.arraycopy(partTwo, 0, merged, partOne.length, partTwo.length);
        assertTrue(Arrays.equals(merged, readObjectBytes(client.getObject(TARGET_A, bucket, "mp/merged.txt"))),
                "分片合并后内容应与两片顺序拼接一致（乱序 ETag 自动升序提交）");
        log.info("分片四步与分段列举闭环完成: part1={}, part2={}",
                partOne.length, partTwo.length);

        String abortUploadId = client.initiateMultipartUpload(TARGET_A, bucket, "mp/aborted.txt");
        client.abortMultipartUpload(TARGET_A, bucket, "mp/aborted.txt", abortUploadId);
        client.abortMultipartUpload(TARGET_A, bucket, "mp/aborted.txt", abortUploadId);
        assertFalse(client.doesObjectExist(TARGET_A, bucket, "mp/aborted.txt"),
                "中止后不应残留对象且重复中止幂等");

        client.deleteObject(TARGET_A, bucket, "mp/merged.txt");
        client.deleteBucket(TARGET_A, bucket);
        for (File tempFile : Arrays.asList(partFileOne, partFileTwo)) {
            assertTrue(tempFile.delete());
        }
        log.info("分片中止清理路径验证完成");
    }

    @Test
    void autoMultipartUploadsOversizedFile() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucket = "e2e-s3-client-auto-mp-" + runMarker;
        client.createBucket(TARGET_A, bucket);

        byte[] data = new byte[6 * 1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        File bigFile = File.createTempFile("e2e-auto-mp", ".bin");
        Files.write(bigFile.toPath(), data);

        client.uploadObjectMultipart(TARGET_A, bucket, "auto-mp/big.bin", bigFile);

        ObjectMetadata metadata = client.getObjectMetadata(TARGET_A, bucket, "auto-mp/big.bin");
        assertEquals(data.length, metadata.getContentLength(), "自动分片合并后对象大小应一致");
        byte[] readBack = readObjectBytes(client.getObject(TARGET_A, bucket, "auto-mp/big.bin"));
        assertTrue(Arrays.equals(data, readBack), "自动分片合并后内容应逐字节一致");

        File smallFile = File.createTempFile("e2e-auto-mp-small", ".bin");
        Files.write(smallFile.toPath(), "under-threshold".getBytes(StandardCharsets.UTF_8));
        client.uploadObjectMultipart(TARGET_A, bucket, "auto-mp/small.txt", smallFile);
        assertEquals("under-threshold",
                readObject(client.getObject(TARGET_A, bucket, "auto-mp/small.txt")),
                "阈值内文件应直传不分片");

        client.deleteObject(TARGET_A, bucket, "auto-mp/big.bin");
        client.deleteObject(TARGET_A, bucket, "auto-mp/small.txt");
        client.deleteBucket(TARGET_A, bucket);
        assertTrue(bigFile.delete());
        assertTrue(smallFile.delete());
        log.info("自动分片一把梭 E2E 完成: 6MB 按 5MB 分为 2 段并发上传");
    }

    @Test
    void taggingAndFolderRoundTrip() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucket = "e2e-s3-client-tag-" + runMarker;
        client.createBucket(TARGET_A, bucket);

        client.createFolder(TARGET_A, bucket, "logs");
        assertTrue(client.doesObjectExist(TARGET_A, bucket, "logs/"), "文件夹对象应以 / 结尾存在");
        client.createFolder(TARGET_A, bucket, "logs");
        log.info("重复建文件夹幂等");

        client.putObject(TARGET_A, bucket, "logs/app.log",
                new ByteArrayInputStream("tag-content".getBytes(StandardCharsets.UTF_8)));
        Map<String, String> tags = new HashMap<>();
        tags.put("env", "e2e");
        tags.put("run", runMarker);
        client.setObjectTagging(TARGET_A, bucket, "logs/app.log", tags);

        Map<String, String> readTags = client.getObjectTagging(TARGET_A, bucket, "logs/app.log");
        assertEquals(tags, readTags, "对象标签应回读一致");

        client.deleteObjectTagging(TARGET_A, bucket, "logs/app.log");
        assertTrue(client.getObjectTagging(TARGET_A, bucket, "logs/app.log").isEmpty(),
                "删除标签后应无标签");

        client.deleteObject(TARGET_A, bucket, "logs/app.log");
        client.deleteObject(TARGET_A, bucket, "logs/");
        client.deleteBucket(TARGET_A, bucket);
        log.info("标签三步与文件夹 E2E 完成");
    }

    @Test
    void versioningBucketAndLifecyclePrefixRoundTrip() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucket = "e2e-s3-client-ver-" + runMarker;
        client.createVersioningBucket(TARGET_A, bucket);

        String key = "versioned/data.txt";
        client.putObject(TARGET_A, bucket, key,
                new ByteArrayInputStream(("v1-" + runMarker).getBytes(StandardCharsets.UTF_8)));
        client.putObject(TARGET_A, bucket, key,
                new ByteArrayInputStream(("v2-" + runMarker).getBytes(StandardCharsets.UTF_8)));

        VersionListing versions = client.listVersions(TARGET_A, bucket, "versioned/");
        assertEquals(2, versions.getVersionSummaries().size(), "同对象两次写入应产生两个版本");

        List<String> downloadedByVersions = new ArrayList<>();
        for (com.amazonaws.services.s3.model.S3VersionSummary summary : versions.getVersionSummaries()) {
            File versionFile = File.createTempFile("e2e-version-dl", ".bin");
            client.downloadObject(TARGET_A, bucket, key, versionFile.getPath(), summary.getVersionId());
            downloadedByVersions.add(
                    new String(Files.readAllBytes(versionFile.toPath()), StandardCharsets.UTF_8));
            assertTrue(versionFile.delete());
        }
        assertTrue(downloadedByVersions.contains("v1-" + runMarker)
                        && downloadedByVersions.contains("v2-" + runMarker),
                "带版本号断点续传下载应分别回读两个版本各自内容");

        client.putObjectWithExpirationPrefix(TARGET_A, bucket, "temp/data.txt",
                tempFile("expiration content " + runMarker));
        assertTrue(client.doesObjectExist(TARGET_A, bucket, "expiration-temp/data.txt"),
                "过期前缀上传应落在 expiration- 前缀路径");

        client.deleteObjectWithExpirationPrefix(TARGET_A, bucket, "temp/data.txt");
        assertFalse(client.doesObjectExist(TARGET_A, bucket, "expiration-temp/data.txt"),
                "过期前缀删除应清理同一前缀对象");

        client.deleteObject(TARGET_A, bucket, key);
        VersionListing afterDelete = client.listVersions(TARGET_A, bucket, "versioned/");
        assertTrue(afterDelete.getVersionSummaries().stream()
                        .anyMatch(com.amazonaws.services.s3.model.S3VersionSummary::isDeleteMarker),
                "版本化桶删除应产生删除标记");
        for (com.amazonaws.services.s3.model.S3VersionSummary summary
                : client.listVersions(TARGET_A, bucket).getVersionSummaries()) {
            client.execute(TARGET_A, s3 -> {
                s3.deleteVersion(summary.getBucketName(), summary.getKey(), summary.getVersionId());
                return null;
            });
        }
        client.deleteBucket(TARGET_A, bucket);
        log.info("版本化桶与生命周期前缀 E2E 完成, 版本数: {}", versions.getVersionSummaries().size());
    }

    @Test
    void presignedUploadUrlAcceptsPutBody() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucket = "e2e-s3-client-put-" + runMarker;
        client.createBucket(TARGET_A, bucket);

        String body = "presigned-put-body-" + runMarker;
        String uploadUrl = client.generateUploadPresignedUrl(TARGET_A, bucket, "upload/presigned.txt",
                300L, "text/plain");
        httpPut(uploadUrl, body, "text/plain");

        assertEquals(body, readObject(client.getObject(TARGET_A, bucket, "upload/presigned.txt")),
                "PUT 预签名上传内容应回读一致");

        client.deleteObject(TARGET_A, bucket, "upload/presigned.txt");
        client.deleteBucket(TARGET_A, bucket);
        log.info("PUT 预签名实传 E2E 完成");
    }

    private File tempFile(String content) throws IOException {
        File file = File.createTempFile("e2e-s3-client-tmp", ".txt");
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String readObject(S3Object object) throws IOException {
        return new String(readObjectBytes(object), StandardCharsets.UTF_8);
    }

    private byte[] readObjectBytes(S3Object object) throws IOException {
        try {
            InputStream input = object.getObjectContent();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } finally {
            object.close();
        }
    }

    private String httpGetBodyAndDisposition(String url, String expectedFileName) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(10000);
        try {
            assertEquals(200, connection.getResponseCode(), "预签名 URL 应返回 HTTP 200");
            String disposition = connection.getHeaderField("Content-Disposition");
            assertTrue(disposition != null && disposition.contains("attachment")
                            && disposition.contains(expectedFileName),
                    "预签名 GET 应携带附件下载 Disposition: " + disposition);
            InputStream input = connection.getInputStream();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            input.close();
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private void httpPut(String url, String body, String contentType) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try {
            assertEquals(200, connection.getResponseCode(), "PUT 预签名应返回 HTTP 200");
        } finally {
            connection.disconnect();
        }
    }
}
