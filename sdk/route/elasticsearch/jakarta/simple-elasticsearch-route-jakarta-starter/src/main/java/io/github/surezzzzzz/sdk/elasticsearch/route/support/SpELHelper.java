package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring EL 表达式解析工具，用于解析 {@code @Document} 注解中的索引表达式。
 *
 * <p>解析上下文接入当前 Spring ApplicationContext，因此支持 {@code @bean} 引用；解析
 * 失败时直接拒绝调用，不能把原始表达式当成索引名后回退到默认数据源。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
public class SpELHelper implements ApplicationContextAware {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> SPEL_CACHE = new ConcurrentHashMap<>();

    private static volatile ApplicationContext applicationContext;

    /**
     * 判断字符串是否是 SpEL 表达式。
     *
     * @param value 待判断字符串
     * @return 如果是 SpEL 表达式返回 true
     */
    public static boolean isSpEL(String value) {
        // 不完整的表达式也必须进入解析并失败，不能当普通索引名下发。
        return value != null && value.startsWith("#{");
    }

    /**
     * 解析 SpEL 表达式并缓存已编译 Expression，不缓存运行结果。
     *
     * @param expression SpEL 表达式字符串
     * @return 解析后的非空索引名
     */
    public static String resolve(String expression) {
        if (!isSpEL(expression)) {
            return expression;
        }

        try {
            Expression compiledExpression = SPEL_CACHE.computeIfAbsent(expression, SpELHelper::compileExpression);
            StandardEvaluationContext context = new StandardEvaluationContext();
            ApplicationContext currentContext = applicationContext;
            if (currentContext != null) {
                context.setBeanResolver(new BeanFactoryResolver(currentContext));
            }
            Object value = compiledExpression.getValue(context);
            if (value == null || value.toString().trim().isEmpty()) {
                throw new IllegalStateException("SpEL 表达式结果为空");
            }
            String result = value.toString();
            log.debug("SpEL 索引表达式解析完成，expression=[{}]，index=[{}]", expression, result);
            return result;
        } catch (RouteException e) {
            throw e;
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_INDEX_RESOLVE_FAILED,
                    String.format(ErrorMessage.ROUTE_INDEX_RESOLVE_FAILED, expression), e);
        }
    }

    /**
     * 编译 SpEL 表达式（仅在首次使用时调用，结果会被缓存）。
     */
    private static Expression compileExpression(String expression) {
        String source = expression.substring(2, expression.length() - 1);
        return PARSER.parseExpression(source);
    }

    /**
     * 清除缓存。
     */
    public static void clearCache() {
        SPEL_CACHE.clear();
        log.info("SpEL 表达式缓存已清空");
    }

    /**
     * 获取缓存大小。
     *
     * @return 已缓存的表达式数量
     */
    public static int getCacheSize() {
        return SPEL_CACHE.size();
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }
}
