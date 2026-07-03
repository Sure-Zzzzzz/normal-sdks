package io.github.surezzzzzz.sdk.template.doc.test.cases;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.support.TemplateLocationHelper;
import io.github.surezzzzzz.sdk.template.doc.test.SimpleDocTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Template Location Helper Test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleDocTemplateTestApplication.class)
@DisplayName("TemplateLocationHelper 测试")
class TemplateLocationHelperTest {

    @Autowired
    private TemplateLocationHelper locationHelper;

    @Test
    @DisplayName("相对路径拼接默认模板根路径")
    void resolveDefaultTemplateLocation() {
        String resolved = locationHelper.resolveLocation("markdown-report.md");
        log.info("resolved: {}", resolved);
        assertEquals("classpath:templates/markdown-report.md", resolved);
    }

    @Test
    @DisplayName("Windows 绝对路径不被误判为协议")
    void resolveWindowsAbsolutePathWithForwardSlash() {
        String location = "D:/data/report.md";
        String resolved = locationHelper.resolveLocation(location);
        log.info("resolved: {}", resolved);
        assertEquals(location, resolved);
    }

    @Test
    @DisplayName("Windows file 路径不拼接默认模板根路径")
    void resolveWindowsFilePathWithBackslash() {
        String location = "file:D:\\data\\report.md";
        String resolved = locationHelper.resolveLocation(location);
        log.info("resolved: {}", resolved);
        assertEquals(location, resolved);
    }

    @Test
    @DisplayName("后缀大小写归一")
    void extractSuffixLowerCase() {
        assertEquals(SimpleDocTemplateConstant.SUFFIX_MD,
                locationHelper.extractSuffix("classpath:templates/REPORT.MD"));
    }

    @Test
    @DisplayName("query 不影响后缀判断")
    void extractSuffixWithQuery() {
        assertEquals(SimpleDocTemplateConstant.SUFFIX_MD,
                locationHelper.extractSuffix("classpath:templates/report.md?token=x"));
    }

    @Test
    @DisplayName("fragment 不影响后缀判断")
    void extractSuffixWithFragment() {
        assertEquals(SimpleDocTemplateConstant.SUFFIX_MD,
                locationHelper.extractSuffix("classpath:templates/report.md#section"));
    }

    @Test
    @DisplayName("拒绝路径逃逸")
    void rejectPathTraversal() {
        TemplateRenderException exception = assertThrows(TemplateRenderException.class,
                () -> locationHelper.resolveLocation("../secret.md"));
        log.info("路径逃逸异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("路径逃逸"));
    }
}
