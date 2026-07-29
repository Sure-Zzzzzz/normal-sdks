package io.github.surezzzzzz.sdk.kms.client.e2eserver;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 固定 KMS E2E Server 的 loopback 生命周期控制器。
 *
 * <p>控制请求必须携带启动任务生成的临时值；应用仅绑定 loopback，控制器不返回业务载荷。</p>
 *
 * @author surezzzzzz
 */
@RestController
public class KmsClientE2eControlController {

    private static final String CONTROL_TOKEN_PROPERTY = "kms.e2e.control.token";
    private static final String CONTROL_HEADER = "X-Kms-E2e-Control";
    private static final String READY_PATH = "/__kms-e2e/ready";
    private static final String SHUTDOWN_PATH = "/__kms-e2e/shutdown";

    private final ConfigurableApplicationContext context;

    public KmsClientE2eControlController(ConfigurableApplicationContext context) {
        this.context = context;
    }

    private static void requireControlToken(String controlToken) {
        String expected = System.getProperty(CONTROL_TOKEN_PROPERTY);
        if (expected == null || !expected.equals(controlToken)) {
            throw new IllegalStateException("KMS E2E Server 控制请求未授权");
        }
    }

    /**
     * 返回仅 loopback 夹具使用的就绪状态。
     *
     * @param controlToken 启动任务提供的临时控制值
     */
    @GetMapping(READY_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ready(@RequestHeader(CONTROL_HEADER) String controlToken) {
        requireControlToken(controlToken);
    }

    /**
     * 在 HTTP 响应返回后关闭测试 Server。
     *
     * @param controlToken 启动任务提供的临时控制值
     */
    @PostMapping(SHUTDOWN_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shutdown(@RequestHeader(CONTROL_HEADER) String controlToken) {
        requireControlToken(controlToken);
        Thread shutdownThread = new Thread(context::close, "kms-e2e-server-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }
}
