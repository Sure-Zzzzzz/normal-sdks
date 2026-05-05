package io.github.surezzzzzz.sdk.cache.aspect;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheEvict;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCachePut;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheable;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.SpELExpressionHelper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.lang.reflect.Method;

/**
 * Smart Cache Aspect
 * <p>
 * 缓存注解 AOP 切面
 * </p>
 *
 * @author Sure
 */
@Slf4j
@Aspect
@SmartCacheComponent
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class SmartCacheAspect {

    private final SmartCacheManager cacheManager;

    public SmartCacheAspect(SmartCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 处理 @SmartCacheable 注解
     * 先从缓存获取，如果不存在则执行方法并缓存结果
     *
     * @param joinPoint 切点
     * @return 方法返回值或缓存值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(io.github.surezzzzzz.sdk.cache.annotation.SmartCacheable)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        SmartCacheable annotation = method.getAnnotation(SmartCacheable.class);

        String cacheName = annotation.cacheName();
        Object[] args = joinPoint.getArgs();

        // 解析 key
        String key = SpELExpressionHelper.parseExpression(annotation.key(), method, args, null);
        if (key == null || key.isEmpty()) {
            log.warn("Cache key is null or empty, skip caching: {}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName());
            return joinPoint.proceed();
        }

        // 检查条件
        if (!annotation.condition().isEmpty()) {
            boolean condition = SpELExpressionHelper.parseCondition(annotation.condition(), method, args, null);
            if (!condition) {
                return joinPoint.proceed();
            }
        }

        // 从缓存获取
        return cacheManager.get(cacheName, key, () -> {
            try {
                return joinPoint.proceed();
            } catch (SmartCacheException e) {
                throw e;
            } catch (Throwable e) {
                throw new SmartCacheException(
                        "Cache operation failed: " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                                + " (caused by: " + e.getClass().getSimpleName() + ")",
                        e
                );
            }
        }, annotation.l2TtlSeconds());
    }

    /**
     * 处理 @SmartCachePut 注解
     * 执行方法并将结果更新到缓存
     *
     * @param joinPoint 切点
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(io.github.surezzzzzz.sdk.cache.annotation.SmartCachePut)")
    public Object handleCachePut(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        SmartCachePut annotation = method.getAnnotation(SmartCachePut.class);

        String cacheName = annotation.cacheName();
        Object[] args = joinPoint.getArgs();

        // 执行方法
        Object result = joinPoint.proceed();

        // 解析 key
        String key = SpELExpressionHelper.parseExpression(annotation.key(), method, args, result);
        if (key == null || key.isEmpty()) {
            log.warn("Cache key is null or empty, skip caching: {}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName());
            return result;
        }

        // 检查条件
        if (!annotation.condition().isEmpty()) {
            boolean condition = SpELExpressionHelper.parseCondition(annotation.condition(), method, args, result);
            if (!condition) {
                return result;
            }
        }

        // 更新缓存
        cacheManager.put(cacheName, key, result, annotation.l2TtlSeconds());

        return result;
    }

    /**
     * 处理 @SmartCacheEvict 注解
     * 执行方法并删除缓存
     *
     * @param joinPoint 切点
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(io.github.surezzzzzz.sdk.cache.annotation.SmartCacheEvict)")
    public Object handleCacheEvict(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        SmartCacheEvict annotation = method.getAnnotation(SmartCacheEvict.class);

        String cacheName = annotation.cacheName();
        Object[] args = joinPoint.getArgs();

        // 如果 beforeInvocation=true，在方法执行前删除缓存
        if (annotation.beforeInvocation()) {
            evictCache(annotation, cacheName, method, args, null);
            return joinPoint.proceed();
        }

        // 默认：方法执行后删除缓存
        Object result = joinPoint.proceed();

        // 检查条件
        if (!annotation.condition().isEmpty()) {
            boolean condition = SpELExpressionHelper.parseCondition(annotation.condition(), method, args, result);
            if (!condition) {
                return result;
            }
        }

        evictCache(annotation, cacheName, method, args, result);

        return result;
    }

    /**
     * 删除缓存的通用方法
     *
     * @param annotation 注解
     * @param cacheName  缓存名称
     * @param method     方法
     * @param args       方法参数
     * @param result     方法返回值（beforeInvocation=true 时为 null）
     */
    private void evictCache(SmartCacheEvict annotation, String cacheName, Method method, Object[] args, Object result) {
        if (annotation.allEntries()) {
            cacheManager.clear(cacheName);
        } else {
            String key = SpELExpressionHelper.parseExpression(annotation.key(), method, args, result);
            if (key == null || key.isEmpty()) {
                log.warn("Cache key is null or empty, skip eviction: {}.{}",
                        method.getDeclaringClass().getSimpleName(), method.getName());
                return;
            }
            cacheManager.evict(cacheName, key);
        }
    }
}
