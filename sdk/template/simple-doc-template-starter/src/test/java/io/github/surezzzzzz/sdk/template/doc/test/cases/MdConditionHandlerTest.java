package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.condition.MdConditionHandler;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.document.MdDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.md.MdOutputHandler;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MdConditionHandler 单元行为测试（1.2.2 T1）
 * + MdOutputHandler null document 分支测试（1.2.2 T2）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("MdConditionHandler + MdOutputHandler 异常分支测试")
class MdConditionHandlerTest {

    @Autowired
    private MdConditionHandler mdConditionHandler;

    @Autowired
    private MdOutputHandler mdOutputHandler;

    // ==================== T1：循环内条件块（MdConditionHandler.validateConditionNotInsideLoop）====================

    @Test
    @DisplayName("循环内条件块：start 标签在 for/endfor 之间抛出 MD_001")
    void conditionInsideLoopStartThrows() {
        String md = "[suredt.for:items]\n[suredt.start:flag]\ncontent\n[suredt.end:flag]\n[suredt.endfor:items]";
        Map<String, Object> data = new HashMap<>();
        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> mdConditionHandler.process(md.getBytes(StandardCharsets.UTF_8), data));
        log.info("循环内 start 异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.MARKDOWN_UNSUPPORTED_FEATURE, ex.getErrorCode(),
                "循环内条件块应抛出 MD_001");
        assertTrue(ex.getMessage().contains("Markdown 不支持的能力"),
                "异常消息应包含 Markdown 不支持的能力前缀");
    }

    @Test
    @DisplayName("循环内条件块：end 标签在 for/endfor 之间抛出 MD_001")
    void conditionInsideLoopEndThrows() {
        String md = "[suredt.for:items]\n[suredt.end:flag]\n[suredt.endfor:items]";
        Map<String, Object> data = new HashMap<>();
        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> mdConditionHandler.process(md.getBytes(StandardCharsets.UTF_8), data));
        log.info("循环内 end 异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.MARKDOWN_UNSUPPORTED_FEATURE, ex.getErrorCode(),
                "循环内 end 标签应抛出 MD_001");
    }

    @Test
    @DisplayName("条件块在循环外：start=true 保留内容，不抛异常")
    void conditionOutsideLoopKept() {
        String md = "[suredt.start:show]\nvisible\n[suredt.end:show]";
        Map<String, Object> data = new HashMap<>();
        data.put("show", Boolean.TRUE);
        byte[] result = mdConditionHandler.process(md.getBytes(StandardCharsets.UTF_8), data);
        String output = new String(result, StandardCharsets.UTF_8);
        log.info("条件块在循环外（保留）结果:\n{}", output);
        assertTrue(output.contains("visible"), "start=true 时条件块内容应保留");
        assertFalse(output.contains("[suredt."), "start/end 标记应被清除");
    }

    @Test
    @DisplayName("条件块在循环外：start=false 删除整块，不抛异常")
    void conditionOutsideLoopRemoved() {
        String md = "[suredt.start:show]\nvisible\n[suredt.end:show]\nafter";
        Map<String, Object> data = new HashMap<>();
        data.put("show", Boolean.FALSE);
        byte[] result = mdConditionHandler.process(md.getBytes(StandardCharsets.UTF_8), data);
        String output = new String(result, StandardCharsets.UTF_8);
        log.info("条件块在循环外（删除）结果:\n{}", output);
        assertFalse(output.contains("visible"), "start=false 时条件块内容应删除");
        assertTrue(output.contains("after"), "条件块外内容应保留");
    }

    // ==================== T2：MdOutputHandler null document 抛出 OUTPUT_002 ====================

    @Test
    @DisplayName("MdOutputHandler 收到 null document 抛出 OUTPUT_002")
    void nullDocumentThrowsOutput002() {
        log.info("测试 MdOutputHandler null document 分支");
        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> mdOutputHandler.toBytes(null));
        log.info("null document 异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.OUTPUT_FORMAT_MISMATCH, ex.getErrorCode(),
                "null document 应抛出 OUTPUT_002（格式不匹配）");
        assertTrue(ex.getMessage().contains("md"),
                "异常消息应包含格式信息 md");
    }

    @Test
    @DisplayName("MdOutputHandler 收到正常 MdDocument 正常返回")
    void normalDocumentOk() {
        MdDocument doc = new MdDocument(
                "hello world".getBytes(StandardCharsets.UTF_8),
                "hello world".getBytes(StandardCharsets.UTF_8),
                Collections.emptyList());
        byte[] result = mdOutputHandler.toBytes(doc);
        log.info("正常 document 结果: {}", new String(result, StandardCharsets.UTF_8));
        assertEquals("hello world", new String(result, StandardCharsets.UTF_8),
                "正常 document 应原样返回");
    }
}
