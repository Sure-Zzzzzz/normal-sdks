package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.annotation.SmartMiddlewareOpsServerComponent;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsHttpExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.NestedServletException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 安全异常处理范围测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MiddlewareOpsHttpExceptionHandlerTest {

    @Test
    void shouldHandleOnlyOpsControllerExceptions() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OpsFailureController(), new HostFailureController())
                .setControllerAdvice(new MiddlewareOpsHttpExceptionHandler()).build();

        log.info("验证标记控制器异常由受控 Advice 映射，未标记控制器保持宿主处理链");
        mockMvc.perform(MockMvcRequestBuilders.get("/ops-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("中间件运维查询暂不可用"));

        NestedServletException exception = assertThrows(NestedServletException.class,
                () -> mockMvc.perform(MockMvcRequestBuilders.get("/host-failure")));
        log.info("宿主控制器异常保持未被 Ops Advice 截获：exception={}", exception.getClass().getSimpleName());
    }

    @SmartMiddlewareOpsServerComponent
    @RestController
    public static class OpsFailureController {

        @GetMapping("/ops-failure")
        public void failure() {
            throw new IllegalStateException("ops failure");
        }
    }

    @RestController
    public static class HostFailureController {

        @GetMapping("/host-failure")
        public void failure() {
            throw new IllegalStateException("host failure");
        }
    }
}
