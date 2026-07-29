package io.github.surezzzzzz.sdk.kms.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.client.RestTemplateKmsClient;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsClientConfigurationException;
import io.github.surezzzzzz.sdk.kms.client.model.*;
import io.github.surezzzzzz.sdk.kms.client.support.KmsClientUriHelper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KMS Client 边界与不可变模型测试。
 *
 * <p>仅用本地伪造 HTTP 响应验证 URI、载荷上限和资源关闭，不依赖 KMS Server 运行时。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsClientBoundaryTest {

    @Test
    void shouldOnlyAcceptOriginAndAppendFixedApiBasePath() {
        URI apiBaseUri = KmsClientUriHelper.apiBaseUri("https://kms.example.internal:9443/");
        log.info("规范化 KMS API 路径: {}", apiBaseUri.getPath());
        assertEquals("https://kms.example.internal:9443/api/v1/kms", apiBaseUri.toString(),
                "合法 origin 必须只能追加固定 KMS API 根路径");

        List<String> invalidUrls = Arrays.asList(
                "ftp://kms.example.internal",
                "https://kms.example.internal/untrusted-path",
                "https://user@kms.example.internal",
                "https://kms.example.internal?switch=origin",
                "https://kms.example.internal#fragment",
                "https:///missing-host",
                "relative-path");
        for (String invalidUrl : invalidUrls) {
            assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri(invalidUrl),
                    "非法 origin 必须拒绝: " + invalidUrl);
        }

        KmsHttpExecutor executor = new KmsHttpExecutor(new RestTemplate(), new KmsJsonCodec(), new KmsHttpErrorMapper(),
                1024, 1024);
        assertThrows(KmsClientConfigurationException.class,
                () -> new RestTemplateKmsClient(URI.create("https://kms.example.internal/other-api"), executor),
                "手工创建客户端也不得绕过固定 API 根路径");
    }

    @Test
    void shouldRejectOversizedBinaryBeforeBase64EncodingAndCloseChunkedResponse() {
        KmsHttpExecutor requestExecutor = new KmsHttpExecutor(new RestTemplate(), new KmsJsonCodec(),
                new KmsHttpErrorMapper(), 4, 1024);
        assertThrows(io.github.surezzzzzz.sdk.kms.client.exception.KmsPayloadTooLargeException.class,
                () -> requestExecutor.validateBinaryValues(new byte[]{1, 2, 3, 4}),
                "超过请求上限的二进制字段必须在 Base64url 编码前拒绝");

        CloseTrackingResponse response = new CloseTrackingResponse("{\"payload\":\"too-large\"}");
        KmsHttpExecutor responseExecutor = new KmsHttpExecutor(new RestTemplate(new SingleResponseRequestFactory(response)),
                new KmsJsonCodec(), new KmsHttpErrorMapper(), 1024, 4);
        assertThrows(io.github.surezzzzzz.sdk.kms.client.exception.KmsResponseTooLargeException.class,
                () -> responseExecutor.execute(URI.create("https://kms.example.internal/api/v1/kms/keys/key-1"),
                        HttpMethod.GET, null, null),
                "未知长度响应实际超过上限时必须拒绝");
        assertTrue(response.closed, "chunked 响应超限后必须关闭 response");
    }

    @Test
    void shouldDefensivelyCopyBinaryModelsAndFreezePageItems() {
        byte[] publicKeyBytes = new byte[]{1, 2, 3};
        byte[] signatureBytes = new byte[]{4, 5, 6};
        KmsPublicKey publicKey = KmsPublicKey.builder().keyRef("key-1").version(Integer.valueOf(1))
                .algorithm("ES256").state("ACTIVE").publicKey(publicKeyBytes).build();
        KmsSignature signature = KmsSignature.builder().keyRef("key-1").version(Integer.valueOf(1))
                .signature(signatureBytes).build();
        KmsSigningResult signingResult = KmsSigningResult.builder().version(Integer.valueOf(1)).algorithm("ES256")
                .signature(signatureBytes).build();

        publicKeyBytes[0] = 9;
        signatureBytes[0] = 9;
        byte[] returnedPublicKey = publicKey.getPublicKey();
        byte[] returnedSignature = signature.getSignature();
        returnedPublicKey[1] = 8;
        returnedSignature[1] = 8;

        KmsKey key = KmsKey.builder().keyRef("key-1").keyAlias("alias").purpose("SIGN").algorithm("ES256")
                .state("ACTIVE").activeVersion(Integer.valueOf(1)).rowVersion(Long.valueOf(1L)).build();
        List<KmsKey> items = new ArrayList<KmsKey>();
        items.add(key);
        KmsKeyPage page = KmsKeyPage.builder().items(items).page(Integer.valueOf(1)).size(Integer.valueOf(20))
                .total(Long.valueOf(1L)).build();
        items.clear();

        log.info("防御性复制结果长度: publicKey={}, signature={}, pageItems={}", publicKey.getPublicKey().length,
                signature.getSignature().length, page.getItems().size());
        assertArrayEquals(new byte[]{1, 2, 3}, publicKey.getPublicKey(), "公钥模型必须隔离构造入参和返回数组");
        assertArrayEquals(new byte[]{4, 5, 6}, signature.getSignature(), "签名模型必须隔离构造入参和返回数组");
        assertArrayEquals(new byte[]{4, 5, 6}, signingResult.getSignature(), "业务签名结果必须隔离构造入参和返回数组");
        assertEquals(Integer.valueOf(1), Integer.valueOf(page.getItems().size()), "分页模型必须复制输入集合");
        assertThrows(UnsupportedOperationException.class, () -> page.getItems().clear(), "分页模型必须返回不可变集合");
        assertTrue(page.getItems().contains(key), "分页模型必须保留原始逻辑密钥");
    }

    /**
     * 只返回给定响应的请求工厂，用于验证执行器在响应超限时仍关闭连接。
     */
    private static class SingleResponseRequestFactory implements ClientHttpRequestFactory {
        private final ClientHttpResponse response;

        private SingleResponseRequestFactory(ClientHttpResponse response) {
            this.response = response;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new ClientHttpRequest() {
                private final HttpHeaders headers = new HttpHeaders();
                private final ByteArrayOutputStream body = new ByteArrayOutputStream();

                @Override
                public ClientHttpResponse execute() {
                    return response;
                }

                @Override
                public OutputStream getBody() {
                    return body;
                }

                @Override
                public HttpMethod getMethod() {
                    return httpMethod;
                }

                @Override
                public String getMethodValue() {
                    return httpMethod.name();
                }

                @Override
                public URI getURI() {
                    return uri;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }
            };
        }
    }

    /**
     * 记录 close 调用的内存响应，模拟未知 Content-Length 的分块响应。
     */
    private static class CloseTrackingResponse implements ClientHttpResponse {
        private final byte[] body;
        private boolean closed;

        private CloseTrackingResponse(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public int getRawStatusCode() {
            return HttpStatus.OK.value();
        }

        @Override
        public String getStatusText() {
            return HttpStatus.OK.getReasonPhrase();
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return headers;
        }
    }
}
