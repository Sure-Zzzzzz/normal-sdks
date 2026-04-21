package io.github.surezzzzzz.sdk.elasticsearch.search.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.IndexRouteProcessor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;

/**
 * 执行器抽象基类
 * 定义查询/聚合的通用执行骨架（参数校验 → 获取元数据 → 降级重试 → 执行），
 * 子类实现具体的构建、处理逻辑。
 *
 * @param <Req>  请求类型
 * @param <Resp> 响应类型
 * @author surezzzzzz
 */
@Slf4j
public abstract class AbstractExecutor<Req, Resp> {

    @Autowired
    protected SimpleElasticsearchSearchProperties properties;

    @Autowired
    protected MappingManager mappingManager;

    @Autowired
    protected IndexRouteProcessor indexRouteProcessor;

    @Autowired
    protected SimpleElasticsearchRouteRegistry registry;

    @Autowired
    protected RouteResolver routeResolver;

    @Autowired
    protected ApplicationEventPublisher eventPublisher;

    // ==================== 模板方法（final，骨架不可覆盖） ====================

    /**
     * 执行入口
     *
     * @param request 请求
     * @return 响应
     */
    public final Resp execute(Req request) {
        long startTime = System.currentTimeMillis();
        try {
            validateRequest(request);
            IndexMetadata metadata = mappingManager.getMetadata(getIndex(request));
            if (needsDowngradeRetry(request, metadata)) {
                return executeWithDowngradeRetry(request, metadata, startTime);
            }
            return executeOnce(request, metadata, startTime, DowngradeLevel.LEVEL_0);
        } catch (IOException e) {
            log.error("Execution failed: index={}", getIndex(request), e);
            throw wrapIoException(e);
        }
    }

    // ==================== 抽象方法（子类必须实现） ====================

    /**
     * 校验请求参数
     */
    protected abstract void validateRequest(Req request);

    /**
     * 判断是否需要降级重试
     */
    protected abstract boolean needsDowngradeRetry(Req request, IndexMetadata metadata);

    /**
     * 执行一次查询/聚合
     */
    protected abstract Resp executeOnce(Req request, IndexMetadata metadata,
                                        long startTime, DowngradeLevel level) throws IOException;

    /**
     * 获取请求中的索引名
     */
    protected abstract String getIndex(Req request);

    /**
     * 将 IOException 包装为业务异常
     */
    protected abstract RuntimeException wrapIoException(IOException e);

    // ==================== 钩子方法（子类可选覆盖） ====================

    /**
     * 预估降级级别，默认 LEVEL_0
     * 子类可覆盖以实现预估优化
     */
    protected DowngradeLevel estimateDowngradeLevel(Req request, IndexMetadata metadata) {
        return DowngradeLevel.LEVEL_0;
    }

    // ==================== 内部通用逻辑 ====================

    private Resp executeWithDowngradeRetry(Req request, IndexMetadata metadata,
                                           long startTime) throws IOException {
        DowngradeLevel currentLevel = estimateDowngradeLevel(request, metadata);
        while (true) {
            try {
                return executeOnce(request, metadata, startTime, currentLevel);
            } catch (ElasticsearchException | IOException e) {
                if (!isTooLongFrameException(e)) {
                    throw e;
                }
                if (!currentLevel.hasNext()
                        || currentLevel.getValue() >= properties.getDowngrade().getMaxLevel()) {
                    log.error("Execution failed at max downgrade level {}: index={}",
                            currentLevel, getIndex(request), e);
                    throw new DowngradeFailedException(ErrorCode.DOWNGRADE_FAILED,
                            ErrorMessage.DOWNGRADE_FAILED, currentLevel, e);
                }
                DowngradeLevel next = currentLevel.next();
                log.warn("Downgrading to {} for index [{}]", next, getIndex(request));
                currentLevel = next;
            }
        }
    }

    private boolean isTooLongFrameException(Throwable e) {
        if (e == null) {
            return false;
        }
        String msg = e.getMessage();
        if (msg != null && msg.contains(SimpleElasticsearchSearchConstant.TOO_LONG_FRAME_EXCEPTION)) {
            return true;
        }
        return isTooLongFrameException(e.getCause());
    }
}
