package io.github.surezzzzzz.sdk.feedback.client.feign.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StreamUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 SpringEncoder multipart 编码走查，确认附件不会在同名 part 上丢失。 */
@Slf4j
class FeedbackFeignMultipartEncodingTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<String>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/feedback/submissions", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsSubmissionAndEveryAttachmentPart() throws Exception {
        FeedbackFeignClient client = Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(new ObjectFactory<HttpMessageConverters>() {
                    @Override
                    public HttpMessageConverters getObject() {
                        return new HttpMessageConverters(new FormHttpMessageConverter(),
                                new MappingJackson2HttpMessageConverter());
                    }
                }))
                .decoder(new JacksonDecoder(new ObjectMapper()))
                .target(FeedbackFeignClient.class,
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/api/feedback");
        Map<String, Object> submission = new LinkedHashMap<String, Object>();
        submission.put("applicationCode", "demo");
        List<org.springframework.web.multipart.MultipartFile> files = Arrays.<org.springframework.web.multipart.MultipartFile>asList(
                new TestMultipartFile("first.png", "image/png", new byte[] {1}),
                new TestMultipartFile("second.png", "image/png", new byte[] {2}));

        client.createSubmission("idem-1", null,
                new TestMultipartFile("submission.json", "application/json",
                        new ObjectMapper().writeValueAsBytes(submission)), files);
        String body = requestBody.get();
        log.info("multipart 编码完成，submission part 存在={}, attachment part 数量={}",
                body.contains("name=\"submission\""), count(body, "name=\"attachments\""));
        assertTrue(body.contains("name=\"submission\""));
        assertEquals(2, count(body, "name=\"attachments\""));
        assertTrue(body.contains("filename=\"first.png\""));
        assertTrue(body.contains("filename=\"second.png\""));
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        byte[] body = StreamUtils.copyToByteArray(exchange.getRequestBody());
        requestBody.set(new String(body, StandardCharsets.ISO_8859_1));
        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static int count(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static final class TestMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;
        private TestMultipartFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            this.contentType = contentType;
            this.content = content;
        }
        @Override public String getName() { return "attachments"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File destination) throws java.io.IOException { }
    }
}
