package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.resolver.WordStyleResolver;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Word Style Resolver Test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
class WordStyleResolverTest {

    @Test
    @DisplayName("验证段落缩进解析不会退化为异常值")
    void testIndentResolution() throws Exception {
        Path docx = Paths.get("src/test/resources/templates/weekly-report-template.docx");

        boolean hasTextParagraph = false;
        try (FileInputStream fis = new FileInputStream(docx.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {
            WordStyleResolver resolver = new WordStyleResolver(doc);
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                hasTextParagraph = true;
                long firstLine = resolver.resolveIndFirstLine(paragraph);
                long left = resolver.resolveIndLeft(paragraph);
                log.info("段落缩进解析: firstLine={}, left={}, text={}", firstLine, left, text);
                assertTrue(firstLine >= -1, "首行缩进解析值不应低于未设置哨兵值");
                assertTrue(left >= -1, "左缩进解析值不应低于未设置哨兵值");
            }
        }

        assertTrue(hasTextParagraph, "测试文档应包含文本段落");
    }
}
