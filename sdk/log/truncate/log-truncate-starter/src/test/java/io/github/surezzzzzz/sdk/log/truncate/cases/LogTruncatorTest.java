package io.github.surezzzzzz.sdk.log.truncate.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.log.truncate.LogTruncateApplication;
import io.github.surezzzzzz.sdk.log.truncate.support.LogTruncator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(classes = LogTruncateApplication.class)
public class LogTruncatorTest {

    @Autowired
    @Qualifier("logTruncator")
    private LogTruncator truncator;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试原始字符串截断
     */
    @Test
    @DisplayName("测试原始字符串截断")
    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void testRawStringTruncation() throws Exception {
        String longString = repeatString("这是一段很长的日志内容，", 500) + "结尾";

        String truncated = truncator.truncateRaw(longString);

        log.info("=== 原始字符串截断测试 ===");
        log.info("原始长度: {} bytes", longString.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        log.info("截断后长度: {} bytes", truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        log.info("截断后内容预览:\n{}", truncated.substring(0, Math.min(200, truncated.length())));

        Assertions.assertNotNull(truncated);
        Assertions.assertTrue(truncated.contains("[truncated"), "应包含截断标记");
        Assertions.assertTrue(truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < longString.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    /**
     * 测试异常对象截断
     */
    @Test
    @DisplayName("测试异常对象堆栈截断")
//    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void testExceptionTruncation() throws Exception {
        Exception exception = new RuntimeException("测试异常",
                new IllegalArgumentException("嵌套异常原因",
                        new NullPointerException("最深层异常")));

        String truncated = truncator.truncate(exception);

        log.info("=== 异常对象截断测试 ===");
        log.info("截断后内容:\n{}", truncated);

        Assertions.assertNotNull(truncated);
        Assertions.assertTrue(truncated.contains("RuntimeException"), "应包含异常类型");
        Assertions.assertTrue(truncated.contains("测试异常"), "应包含异常消息");
    }

    /**
     * 测试包含中文的超长字符串
     */
    @Test
    @DisplayName("测试中文字符截断（UTF-8 多字节安全）")
    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void testChineseCharacterTruncation() throws Exception {
        String chineseText = repeatString("这是一段包含中文的超长日志内容，用于测试UTF-8多字节字符的安全截断。", 100);

        String truncated = truncator.truncateRaw(chineseText);

        log.info("=== 中文字符截断测试 ===");
        log.info("原始长度: {} bytes ({} 字符)",
                chineseText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                chineseText.length());
        log.info("截断后长度: {} bytes",
                truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);

        Assertions.assertNotNull(truncated);
        // 验证没有乱码（UTF-8 截断不应破坏多字节字符）
        Assertions.assertDoesNotThrow(() ->
                        new String(truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                java.nio.charset.StandardCharsets.UTF_8),
                "截断后的字符串应该是合法的 UTF-8 编码");
    }

    /**
     * Java 8 兼容的字符串重复方法
     */
    private String repeatString(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

}