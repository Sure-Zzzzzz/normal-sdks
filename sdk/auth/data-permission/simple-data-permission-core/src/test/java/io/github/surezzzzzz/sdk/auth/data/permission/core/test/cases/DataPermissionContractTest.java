package io.github.surezzzzzz.sdk.auth.data.permission.core.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentCodec;
import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DataPermissionContractTest {

    @Test
    void shouldRetainOperationMetadataAtRuntime() throws Exception {
        Method method = AnnotatedOperation.class.getDeclaredMethod("read");
        DataPermissionOperation operation = method.getAnnotation(DataPermissionOperation.class);
        log.info("读取到数据权限操作注解：{}", operation);
        assertNotNull(operation, "运行时必须可读取数据权限操作注解");
        assertEquals("test_resource", operation.resource(), "注解资源必须保持声明值");
        assertEquals("read", operation.action(), "注解动作必须保持声明值");
    }

    @Test
    void shouldKeepSourceAndCodecAsPureSpiContracts() throws Exception {
        DataGrantDocumentSource source = new DataGrantDocumentSource() {
            @Override
            public Optional<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument> currentDocument() {
                return Optional.empty();
            }
        };
        DataGrantDocumentCodec codec = new DataGrantDocumentCodec() {
            @Override
            public io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument decode(String payload) {
                throw new UnsupportedOperationException("由适配器实现");
            }

            @Override
            public String encode(io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument document) {
                throw new UnsupportedOperationException("由适配器实现");
            }
        };
        int abstractMethodCount = 0;
        for (Method method : DataGrantDocumentCodec.class.getDeclaredMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                abstractMethodCount++;
            }
        }
        Method decode = DataGrantDocumentCodec.class.getDeclaredMethod("decode", String.class);
        Method encode = DataGrantDocumentCodec.class.getDeclaredMethod("encode",
                io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument.class);
        log.info("SPI 来源返回：{}，Codec 类型：{}，Codec 抽象方法数：{}", source.currentDocument(), codec.getClass().getName(),
                abstractMethodCount);
        assertTrue(!source.currentDocument().isPresent(), "空来源必须以 Optional.empty 表达");
        assertEquals(2, abstractMethodCount, "Codec 必须仅保留 decode 和 encode 两个抽象 SPI 方法");
        assertTrue(Modifier.isAbstract(decode.getModifiers()), "decode 必须保持抽象 SPI 方法");
        assertTrue(Modifier.isAbstract(encode.getModifiers()), "encode 必须保持抽象 SPI 方法");
    }

    @Test
    void shouldKeepCanonicalCodecFixtureAvailable() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("data-grant-document-1.0.json");
        log.info("读取1.0协议规范fixture");
        assertNotNull(inputStream, "未来codec适配器必须复用规范JSON fixture");
        try {
            String fixture = new String(readAllBytes(inputStream), StandardCharsets.UTF_8).trim();
            log.info("规范fixture：{}", fixture);
            assertEquals("{\"protocol\":\"simple-data-permission\",\"version\":\"1.0\",\"grants\":[{\"resource\":\"test_resource\",\"actions\":[\"export\",\"read\"],\"all\":false,\"constraints\":[{\"dimension\":\"scope_a\",\"operator\":\"IN\",\"values\":[\"value-a\"]},{\"dimension\":\"scope_b\",\"operator\":\"IN\",\"values\":[\"value-b\"]}]}]}",
                    fixture, "规范fixture必须保持紧凑、字段有序且数组已规范化");
        } finally {
            inputStream.close();
        }
    }

    @Test
    void shouldExposeStableErrorCode() {
        DataPermissionException exception = new DataPermissionException(ErrorCode.INVALID_DOCUMENT, "测试错误");
        log.info("异常错误码：{}", exception.getErrorCode());
        assertEquals("BIZ_005", exception.getErrorCode(), "基础异常必须暴露稳定错误码");
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private static final class AnnotatedOperation {

        @DataPermissionOperation(resource = "test_resource", action = "read")
        private void read() {
        }
    }
}
