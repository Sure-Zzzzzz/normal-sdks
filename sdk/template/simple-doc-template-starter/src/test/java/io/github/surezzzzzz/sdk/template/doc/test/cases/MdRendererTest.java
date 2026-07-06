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
import java.util.Arrays;
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
    @DisplayName("嵌套循环：外层按风险展开，内层按措施展开")
    void nestedLoopExpand() {
        String md = "[suredt.for:risks]\n## [suredt.var:rname]\n[suredt.for:measures]\n- [suredt.var:mname]\n[suredt.endfor:measures]\n[suredt.endfor:risks]";
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> risks = new ArrayList<>();
        risks.add(risk("风险A", measure("措施1"), measure("措施2")));
        risks.add(risk("风险B", measure("措施3")));
        data.put("risks", risks);

        String visible = visible(render(md, data));
        log.info("嵌套循环展开:\n{}", visible);
        assertEquals("## 风险A\n- 措施1\n- 措施2\n## 风险B\n- 措施3", visible, "外层和内层循环应各自展开");
    }

    @Test
    @DisplayName("嵌套循环：内层列表缺失时整块内层删除，外层仍展开")
    void nestedLoopInnerListMissing() {
        String md = "[suredt.for:risks]\n## [suredt.var:rname]\n[suredt.for:measures]\n- [suredt.var:mname]\n[suredt.endfor:measures]\n[suredt.endfor:risks]";
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> risks = new ArrayList<>();
        risks.add(risk("风险A", measure("措施1")));
        Map<String, Object> riskWithoutMeasures = new HashMap<>();
        riskWithoutMeasures.put("rname", "风险B");
        risks.add(riskWithoutMeasures);
        data.put("risks", risks);

        String visible = visible(render(md, data));
        log.info("内层缺失展开:\n{}", visible);
        assertTrue(visible.contains("## 风险A"), "外层风险A应展开");
        assertTrue(visible.contains("- 措施1"), "内层措施1应展开");
        assertTrue(visible.contains("## 风险B"), "外层风险B应展开");
        assertFalse(visible.contains("[suredt.for:measures]"), "缺失内层列表时 for/endfor 应被删除");
        assertFalse(visible.contains("[suredt.var:mname]"), "缺失内层列表时内层占位符应被删除");
    }

    @Test
    @DisplayName("嵌套循环：内层循环项可访问外层循环变量")
    void nestedLoopInnerAccessesOuterVariable() {
        String md = "[suredt.for:risks]\n## [suredt.var:rname]\n[suredt.for:measures]\n- [suredt.var:rname]: [suredt.var:mname]\n[suredt.endfor:measures]\n[suredt.endfor:risks]";
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> risks = new ArrayList<>();
        risks.add(risk("风险A", measure("措施1")));
        risks.add(risk("风险B", measure("措施2")));
        data.put("risks", risks);

        String visible = visible(render(md, data));
        log.info("内层访问外层变量:\n{}", visible);
        assertEquals("## 风险A\n- 风险A: 措施1\n## 风险B\n- 风险B: 措施2", visible,
                "内层循环项应通过作用域访问外层 rname");
    }

    @Test
    @DisplayName("三层嵌套循环：区域/风险/措施各自展开")
    void threeLevelNestedLoop() {
        String md = "[suredt.for:areas]\n# [suredt.var:area]\n[suredt.for:risks]\n## [suredt.var:rname]\n[suredt.for:measures]\n- [suredt.var:mname]\n[suredt.endfor:measures]\n[suredt.endfor:risks]\n[suredt.endfor:areas]";
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> area = new HashMap<>();
        area.put("area", "区域A");
        area.put("risks", Arrays.asList(risk("风险1", measure("措施a"))));
        data.put("areas", Arrays.asList(area));

        String visible = visible(render(md, data));
        log.info("三层嵌套展开:\n{}", visible);
        assertEquals("# 区域A\n## 风险1\n- 措施a", visible, "三层嵌套应各自展开");
    }

    @Test
    @DisplayName("同一外层多个内层循环（兄弟 for）")
    void siblingInnerLoops() {
        String md = "[suredt.for:risks]\n## [suredt.var:rname]\n[suredt.for:measures]\n- M: [suredt.var:mname]\n[suredt.endfor:measures]\n[suredt.for:notes]\n- N: [suredt.var:ntext]\n[suredt.endfor:notes]\n[suredt.endfor:risks]";
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> risk = new HashMap<>();
        risk.put("rname", "风险A");
        risk.put("measures", Arrays.asList(item("mname", "措施1")));
        risk.put("notes", Arrays.asList(item("ntext", "备注x")));
        data.put("risks", Arrays.asList(risk));

        String visible = visible(render(md, data));
        log.info("兄弟内层循环展开:\n{}", visible);
        assertEquals("## 风险A\n- M: 措施1\n- N: 备注x", visible, "同一外层的两个内层 for 应各自展开");
    }

    @Test
    @DisplayName("嵌套循环：内层列表为空时整块内层删除")
    void nestedLoopInnerListEmpty() {
        String md = "[suredt.for:risks]\n## [suredt.var:rname]\n[suredt.for:measures]\n- [suredt.var:mname]\n[suredt.endfor:measures]\n[suredt.endfor:risks]";
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> risk = new HashMap<>();
        risk.put("rname", "风险A");
        risk.put("measures", new ArrayList<>());
        data.put("risks", Arrays.asList(risk));

        String visible = visible(render(md, data));
        log.info("内层空列表展开:\n{}", visible);
        assertEquals("## 风险A", visible, "空内层列表应删除整块内层，外层保留");
        assertFalse(visible.contains("[suredt.for:measures]"), "空列表时 for 标签应被删除");
        assertFalse(visible.contains("[suredt.var:mname]"), "空列表时内层占位符应被删除");
    }

    @Test
    @DisplayName("循环标签 key 不匹配抛出异常")
    void loopKeyMismatchThrows() {
        String md = "[suredt.for:outer]\nx\n[suredt.endfor:inner]";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> render(md, new HashMap<>()));
        log.info("key 不匹配异常: {}", ex.getMessage());
    }

    @Test
    @DisplayName("缺少 endfor 抛出异常")
    void missingEndforThrows() {
        String md = "[suredt.for:outer]\nx";
        TemplateRenderException ex = assertThrows(TemplateRenderException.class,
                () -> render(md, new HashMap<>()));
        log.info("缺少 endfor 异常: {}", ex.getMessage());
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

    private Map<String, Object> risk(String rname, Map<String, Object>... measures) {
        Map<String, Object> m = new HashMap<>();
        m.put("rname", rname);
        m.put("measures", new ArrayList<>(Arrays.asList(measures)));
        return m;
    }

    private Map<String, Object> measure(String mname) {
        Map<String, Object> m = new HashMap<>();
        m.put("mname", mname);
        return m;
    }
}
