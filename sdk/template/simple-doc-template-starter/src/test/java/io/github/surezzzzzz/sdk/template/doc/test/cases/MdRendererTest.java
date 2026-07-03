package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.document.MdDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.model.Image;
import io.github.surezzzzzz.sdk.template.doc.renderer.md.MdRenderer;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MdRenderer 单元行为测试（1.2.0 P0）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("MdRenderer 单元行为测试")
class MdRendererTest {

    @Autowired
    private MdRenderer mdRenderer;

    @Test
    @DisplayName("循环展开：列表项正确展开")
    void loopExpand() {
        String md = "[suredt.for:items]\n- [suredt.var:name]\n[suredt.endfor:items]";
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("name", "条目A"));
        items.add(item("name", "条目B"));
        data.put("items", items);

        String visible = visible(render(md, data));
        log.info("循环展开:\n{}", visible);
        assertTrue(visible.contains("- 条目A"), "应包含条目A");
        assertTrue(visible.contains("- 条目B"), "应包含条目B");
    }

    @Test
    @DisplayName("变量值转义 Markdown 特殊字符")
    void escapeSpecialChars() {
        String md = "值：[suredt.var:val]";
        Map<String, Object> data = new HashMap<>();
        data.put("val", "a*b_c[d]e");

        String visible = visible(render(md, data));
        log.info("转义结果: {}", visible);
        assertTrue(visible.contains("a\\*b\\_c\\[d\\]e"), "特殊字符应被反斜杠转义");
    }

    @Test
    @DisplayName("缺失变量替换为空字符串")
    void missingKeyReplacedWithEmpty() {
        String md = "前[suredt.var:missing]后";
        String visible = visible(render(md, new HashMap<>()));
        log.info("缺失变量结果: {}", visible);
        assertEquals("前后", visible);
    }

    @Test
    @DisplayName("嵌套循环抛出异常")
    void nestedLoopThrows() {
        String md = "[suredt.for:outer]\n[suredt.for:inner]\n[suredt.endfor:inner]\n[suredt.endfor:outer]";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> render(md, new HashMap<>()));
        log.info("嵌套循环异常: {}", ex.getMessage());
    }

    @Test
    @DisplayName("SDK 图片：visible 用真实 src，internal 用 token，reference 记录")
    void mixedImageVisibleAndInternal() {
        String md = "[suredt.img:logo]";
        Map<String, Object> data = new HashMap<>();
        data.put("logo", new Image("classpath:images/chart1.png", 120, 80, "logo"));

        MdDocument doc = render(md, data);
        String visible = visible(doc);
        String internal = internal(doc);
        log.info("visible: {}", visible);
        log.info("internal: {}", internal);
        log.info("references: {}", doc.getImageReferences().size());

        assertTrue(visible.contains("classpath:images/chart1.png"), "visible 应使用真实 src");
        assertFalse(visible.contains("suredt-img://"), "visible 不应包含内部 token");
        assertTrue(internal.contains("suredt-img://"), "internal 应包含内部 token");
        assertEquals(1, doc.getImageReferences().size(), "应记录 1 个图片引用");
    }

    @Test
    @DisplayName("代码块内变量仍被替换")
    void codeBlockVariablesReplaced() {
        String md = "```\n[suredt.var:val]\n```";
        Map<String, Object> data = new HashMap<>();
        data.put("val", "replaced");

        String visible = visible(render(md, data));
        log.info("代码块结果: {}", visible);
        assertTrue(visible.contains("replaced"), "代码块内变量应被替换");
    }

    private MdDocument render(String md, Map<String, Object> data) {
        return (MdDocument) mdRenderer.render(md.getBytes(StandardCharsets.UTF_8), data);
    }

    private String visible(MdDocument doc) {
        return new String(doc.getMdBytes(), StandardCharsets.UTF_8);
    }

    private String internal(MdDocument doc) {
        return new String(doc.getInternalMdBytes(), StandardCharsets.UTF_8);
    }

    private Map<String, Object> item(String key, String value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }
}
