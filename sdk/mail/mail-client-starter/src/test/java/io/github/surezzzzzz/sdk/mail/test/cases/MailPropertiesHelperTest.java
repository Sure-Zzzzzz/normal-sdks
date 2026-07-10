package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.support.MailPropertiesHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mail 配置 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
class MailPropertiesHelperTest {

    @Test
    @DisplayName("测试 Map 转 Properties")
    void testToProperties() {
        Map<String, Object> map = new HashMap<>();
        map.put("mail.smtp.auth", "false");
        map.put("mail.smtp.host", "smtp.example.test");

        Properties properties = MailPropertiesHelper.toProperties(map);

        log.info("输入配置数量: {}", map.size());
        log.info("输出配置数量: {}", properties.size());
        assertEquals("false", properties.getProperty("mail.smtp.auth"), "SMTP auth 配置应正确转换");
        assertEquals("smtp.example.test", properties.getProperty("mail.smtp.host"), "SMTP host 配置应正确转换");
    }

    @Test
    @DisplayName("测试嵌套 Map 转 Properties")
    void testToPropertiesNestedMap() {
        Map<String, Object> starttls = new HashMap<>();
        starttls.put("enable", true);
        Map<String, Object> smtp = new HashMap<>();
        smtp.put("auth", true);
        smtp.put("starttls", starttls);
        Map<String, Object> mail = new HashMap<>();
        mail.put("smtp", smtp);
        Map<String, Object> map = new HashMap<>();
        map.put("mail", mail);

        Properties properties = MailPropertiesHelper.toProperties(map);

        log.info("嵌套输入配置数量: {}", map.size());
        log.info("嵌套输出配置数量: {}", properties.size());
        assertEquals("true", properties.getProperty("mail.smtp.auth"), "嵌套 SMTP auth 配置应正确展开");
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"), "嵌套 starttls 配置应正确展开");
    }

    @Test
    @DisplayName("测试空 Map 转 Properties")
    void testToPropertiesEmptyMap() {
        Properties properties = MailPropertiesHelper.toProperties(null);

        log.info("空 Map 输出配置数量: {}", properties.size());
        assertTrue(properties.isEmpty(), "空 Map 应转换为空 Properties");
    }
}
