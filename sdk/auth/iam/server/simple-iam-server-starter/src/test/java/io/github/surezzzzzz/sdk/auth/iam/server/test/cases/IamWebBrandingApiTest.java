package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.annotation.Resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM Web 品牌 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamWebBrandingApiTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    @DisplayName("Web 品牌 API 默认应返回统一认证中心")
    void testDefaultBranding() throws Exception {
        mockMvc.perform(get(SimpleIamServerConstant.PATH_WEB_BRANDING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(SimpleIamServerConstant.DEFAULT_WEB_BRAND_NAME));
    }
}
