package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory;

import io.github.surezzzzzz.sdk.iam.client.IamApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamApplication;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 可信应用注册表快照：60 秒内存缓存 + 单飞刷新 + 本地完整兜底的故障语义
 *
 * <p>聚散容错分叉（DESIGN.1.1.0）：连通类故障（超时/连接拒绝/5xx）返回 null，
 * 调用方回退本地声明（输出与散形态完全等价）；授权类失败（401/403）与版本错配
 * （404，服务端低于 1.3.7 无此端点）响亮上抛——静默兜底会把"授权缺口/版本错配"
 * 伪装成"注册表里没有"。降级与恢复各记一条日志（状态翻转才记，不逐请求刷屏）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class IamApplicationRegistrySnapshot {

    /**
     * 注册表属低频变动数据，60 秒收敛窗口内改名/停用延迟可见
     */
    static final long DEFAULT_TTL_MILLIS = 60_000L;

    /**
     * 分页拉全量的单页大小与页数上限（防御注册表异常膨胀时的失控循环）
     */
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;

    private final IamApplicationClient applicationClient;
    private final long ttlMillis;
    private final Object refreshLock = new Object();
    private volatile List<IamApplication> snapshot;
    private volatile long loadedAt;
    private volatile boolean degraded;

    /**
     * 构造注册表快照（默认 60 秒 TTL）
     *
     * @param applicationClient IAM 应用目录契约客户端
     */
    public IamApplicationRegistrySnapshot(IamApplicationClient applicationClient) {
        this(applicationClient, DEFAULT_TTL_MILLIS);
    }

    /**
     * 构造注册表快照（测试可注 TTL；ttlMillis 小于 0 视为 0，即每次都过期）
     *
     * @param applicationClient IAM 应用目录契约客户端
     * @param ttlMillis         快照有效期毫秒数
     */
    public IamApplicationRegistrySnapshot(IamApplicationClient applicationClient, long ttlMillis) {
        this.applicationClient = applicationClient;
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    /**
     * 连通类故障判定：I/O 类异常或 5xx；401/403（授权缺口）与 404（服务端低于 1.3.7）
     * 不属于连通故障，上抛响亮暴露
     */
    private static boolean isConnectivityFailure(RuntimeException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof HttpStatusCodeException) {
            return ((HttpStatusCodeException) exception).getRawStatusCode() >= 500;
        }
        return false;
    }

    /**
     * 当前注册表启用应用快照；连通类故障返回 null（调用方走本地完整兜底），
     * 授权类失败与未知异常上抛
     */
    List<IamApplication> current() {
        List<IamApplication> local = snapshot;
        if (local != null && System.currentTimeMillis() - loadedAt < ttlMillis) {
            return local;
        }
        synchronized (refreshLock) {
            // 单飞：抢不到锁的线程在锁外已用旧快照返回；此处二次检查避免重复刷新
            local = snapshot;
            if (local != null && System.currentTimeMillis() - loadedAt < ttlMillis) {
                return local;
            }
            try {
                List<IamApplication> loaded = loadAll();
                snapshot = loaded;
                loadedAt = System.currentTimeMillis();
                if (degraded) {
                    degraded = false;
                    log.warn("IAM 可信应用注册表恢复，服务清单回到注册表权威：{} 个应用", loaded.size());
                }
                return loaded;
            } catch (RuntimeException exception) {
                if (!isConnectivityFailure(exception)) {
                    throw exception;
                }
                if (!degraded) {
                    degraded = true;
                    log.error("IAM 可信应用注册表不可达，服务清单回退本地声明（本地完整兜底）", exception);
                }
                return null;
            }
        }
    }

    /**
     * 分页拉全量（页码 0 起 Spring Page 形态），空页或达页数上限提前终止
     */
    private List<IamApplication> loadAll() {
        List<IamApplication> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            IamSpringPage<IamApplication> result = applicationClient.listApplications(page, PAGE_SIZE);
            if (result == null || result.getContent() == null || result.getContent().isEmpty()) {
                break;
            }
            all.addAll(result.getContent());
            if (all.size() >= result.getTotalElements()) {
                break;
            }
        }
        return all;
    }
}
