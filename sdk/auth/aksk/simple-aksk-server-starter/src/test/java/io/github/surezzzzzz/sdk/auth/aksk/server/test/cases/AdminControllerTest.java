package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Admin Controller 测试
 * <p>
 * 使用MockMvc测试Admin管理页面的各个功能
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        clientRepository.deleteAll();

        // 清理Redis中的测试数据
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理Redis测试数据: {} 条", keys.size());
        }

        log.info("测试数据清理完成");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminIndexPage() throws Exception {
        log.info("测试Admin首页访问");

        // 创建一些测试数据
        clientManagementService.createPlatformClient("Test Platform Client 1");
        clientManagementService.createPlatformClient("Test Platform Client 2");

        // 访问Admin首页
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andExpect(model().attributeExists("clients"));

        log.info("Admin首页访问测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreatePlatformPage() throws Exception {
        log.info("测试创建平台级AKSK页面访问");

        // 访问创建页面
        mockMvc.perform(get("/admin/create-platform"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/create-platform"));

        log.info("创建平台级AKSK页面访问测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreatePlatformFormSubmit() throws Exception {
        log.info("测试创建平台级AKSK表单提交");

        // 提交创建表单
        mockMvc.perform(post("/admin/create-platform")
                        .param("name", "Test Platform Client")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/create-success"))
                .andExpect(flash().attributeExists("success"))
                .andExpect(flash().attributeExists("clientId"))
                .andExpect(flash().attributeExists("clientSecret"));

        log.info("创建平台级AKSK表单提交测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDetailPage() throws Exception {
        log.info("测试查看AKSK详情页面");

        // 创建测试数据
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Detail Client");

        // 访问详情页面
        mockMvc.perform(get("/admin/" + clientInfo.getClientId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/detail"))
                .andExpect(model().attributeExists("client"));

        log.info("查看AKSK详情页面测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteClient() throws Exception {
        log.info("测试删除AKSK操作");

        // 创建测试数据
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Delete Client");

        // 删除AKSK - 现在使用DELETE方法调用RESTful API
        mockMvc.perform(delete("/admin/" + clientInfo.getClientId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        log.info("删除AKSK操作测试通过");
    }
}
