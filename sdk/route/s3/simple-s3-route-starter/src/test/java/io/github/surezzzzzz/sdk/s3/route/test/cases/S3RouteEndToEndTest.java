package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import io.github.surezzzzzz.sdk.s3.route.test.SimpleS3RouteTestApplication;
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
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3 Route 双版本 MinIO 端到端验证：建桶、双路径上传、回读、列举隔离、
 * 预签名 URL 与删除全闭环，对象操作全部经 {@code execute} 回调以标准
 * {@code AmazonS3} 客户端表达，数据全部由测试自行创建与回读。
 *
 * <p>运行前需要先启动 {@code docker-compose.s3-e2e-matrix.yml}。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleS3RouteTestApplication.class)
class S3RouteEndToEndTest {

    private static final int HEALTH_TIMEOUT_MS = 60000;

    @Autowired
    private S3RouteTemplate template;

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
    void roundTripsObjectsAcrossBothMinioVersions() throws Exception {
        String runMarker = Long.toString(System.currentTimeMillis());
        String bucketA = "e2e-s3-route-a-" + runMarker;
        String bucketB = "e2e-s3-route-b-" + runMarker;

        template.execute("minio-2023", client -> client.createBucket(bucketA));
        template.execute("minio-2025", client -> client.createBucket(bucketB));

        String fileContent = "e2e-file-content-" + runMarker;
        File file = File.createTempFile("e2e-s3-route", ".txt");
        Files.write(file.toPath(), fileContent.getBytes(StandardCharsets.UTF_8));
        template.execute("minio-2023", client -> client.putObject(bucketA, "reports/file-object.txt", file));

        String streamContent = "e2e-stream-content-" + runMarker;
        byte[] streamBytes = streamContent.getBytes(StandardCharsets.UTF_8);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(streamBytes.length);
        template.execute("minio-2025", client -> client.putObject(bucketB,
                "streams/stream-object.txt", new ByteArrayInputStream(streamBytes), metadata));

        String readFile = readObject(template.execute("minio-2023",
                client -> client.getObject(bucketA, "reports/file-object.txt")));
        assertEquals(fileContent, readFile, "File 路径上传内容应回读一致");
        String readStream = readObject(template.execute("minio-2025",
                client -> client.getObject(bucketB, "streams/stream-object.txt")));
        assertEquals(streamContent, readStream, "InputStream 路径上传内容应回读一致");
        log.info("双路径上传回读一致, file 字节数: {}, stream 字节数: {}",
                fileContent.length(), streamBytes.length);

        ObjectListing listingFile = template.execute("minio-2023",
                client -> client.listObjects(bucketA, "reports/"));
        assertEquals(1, listingFile.getObjectSummaries().size(), "前缀列举应命中一个对象");
        assertEquals("reports/file-object.txt", listingFile.getObjectSummaries().get(0).getKey());
        ObjectListing listingOtherPrefix = template.execute("minio-2023",
                client -> client.listObjects(bucketA, "streams/"));
        assertTrue(listingOtherPrefix.getObjectSummaries().isEmpty(), "未写前缀不应命中");
        ObjectListing listingB = template.execute("minio-2025", client -> client.listObjects(bucketB));
        assertEquals(1, listingB.getObjectSummaries().size(), "跨 target 数据应彼此隔离");

        URL presigned = template.execute("minio-2023", client -> client.generatePresignedUrl(
                new GeneratePresignedUrlRequest(bucketA, "reports/file-object.txt")
                        .withMethod(HttpMethod.GET)
                        .withExpiration(new Date(System.currentTimeMillis() + 60000L))));
        String presignedRead = httpGet(presigned);
        assertEquals(fileContent, presignedRead, "预签名 URL 应可读取同一内容");

        template.execute("minio-2023", client -> {
            client.deleteObject(bucketA, "reports/file-object.txt");
            return null;
        });
        Boolean exists = template.execute("minio-2023",
                client -> client.doesObjectExist(bucketA, "reports/file-object.txt"));
        assertFalse(exists, "删除后对象不应存在");

        template.execute("minio-2023", client -> {
            client.deleteBucket(bucketA);
            return null;
        });
        template.execute("minio-2025", client -> {
            client.deleteObject(bucketB, "streams/stream-object.txt");
            return null;
        });
        template.execute("minio-2025", client -> {
            client.deleteBucket(bucketB);
            return null;
        });
        assertTrue(file.delete());
    }

    private String readObject(S3Object object) throws IOException {
        try {
            InputStream input = object.getObjectContent();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            object.close();
        }
    }

    private String httpGet(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(10000);
        try {
            assertEquals(200, connection.getResponseCode(), "预签名 URL 应返回 HTTP 200");
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
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
}
