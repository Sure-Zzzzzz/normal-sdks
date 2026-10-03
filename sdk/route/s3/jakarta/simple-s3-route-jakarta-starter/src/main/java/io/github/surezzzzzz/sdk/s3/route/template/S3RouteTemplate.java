package io.github.surezzzzzz.sdk.s3.route.template;

import com.amazonaws.services.s3.AmazonS3;
import io.github.surezzzzzz.sdk.s3.route.annotation.SimpleS3RouteComponent;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;
import io.github.surezzzzzz.sdk.s3.route.resolver.S3RouteResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * S3 Route 唯一同步门面：target 客户端获取器与 execute 回调。
 *
 * <p>Route 只治理连接：多 target 路由、连接池、凭据与生命周期；对象操作语义由
 * 调用方以标准 {@code AmazonS3} 客户端表达，Route 不做二次封装。</p>
 *
 * <p>S3 操作异常（如 {@code AmazonS3Exception}）原样透传，不重新包装；
 * target、参数与 Route 状态问题以 {@link S3RouteException} 抛出。</p>
 *
 * <p>DEBUG 日志只输出操作名、targetKey、耗时与异常类型等受控元数据，
 * 不输出 bucket、对象 key 或对象内容。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleS3RouteComponent
public class S3RouteTemplate {

    private final SimpleS3RouteRegistry registry;
    private final S3RouteResolver resolver;

    /**
     * 创建 Route 同步门面。
     *
     * @param registry target 资源注册表
     * @param resolver target 精确解析器
     */
    public S3RouteTemplate(SimpleS3RouteRegistry registry, S3RouteResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    /**
     * 获取 target 客户端引用，用于执行任意 S3 操作。
     * 客户端生命周期归 Route 管理，调用方不得调用 {@code shutdown()}；
     * 返回引用不参与 in-flight 记账，长耗时操作建议改用 {@link #execute}。
     *
     * @param targetKey 已登记 target key
     * @return target 客户端
     * @throws S3RouteException target 或 Route 状态不符合约束时抛出
     */
    public AmazonS3 amazonS3(String targetKey) {
        return registry.getAmazonS3(resolver.resolveTargetKey(targetKey));
    }

    /**
     * 在 Route 控制的 in-flight 生命周期内以 target 客户端执行回调。
     *
     * @param targetKey 已登记 target key
     * @param callback  以客户端执行的回调
     * @param <T>       回调返回类型
     * @return 回调返回值
     * @throws S3RouteException target、请求或 Route 状态不符合约束时抛出
     */
    public <T> T execute(String targetKey, Function<AmazonS3, T> callback) {
        if (callback == null) {
            throw new S3RouteException(ErrorCode.REQUEST_ILLEGAL, ErrorMessage.REQUEST_ILLEGAL);
        }
        return executeTraced("execute", targetKey, () -> doExecute(targetKey, callback));
    }

    private <T> T doExecute(String targetKey, Function<AmazonS3, T> callback) {
        return registry.execute(resolver.resolveTargetKey(targetKey), callback);
    }

    private <T> T executeTraced(String operation, String targetKey, Supplier<T> action) {
        long startMillis = System.currentTimeMillis();
        try {
            T result = action.get();
            log.debug("S3 操作完成, 操作: {}, targetKey: {}, 耗时ms: {}",
                    operation, targetKey, System.currentTimeMillis() - startMillis);
            return result;
        } catch (RuntimeException exception) {
            log.debug("S3 操作失败, 操作: {}, targetKey: {}, 耗时ms: {}, 异常类型: {}",
                    operation, targetKey, System.currentTimeMillis() - startMillis,
                    exception.getClass().getName());
            throw exception;
        }
    }
}
