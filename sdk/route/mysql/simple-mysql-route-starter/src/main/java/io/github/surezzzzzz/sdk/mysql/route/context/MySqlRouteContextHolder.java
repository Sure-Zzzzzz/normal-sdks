package io.github.surezzzzzz.sdk.mysql.route.context;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * MySQL Route 线程作用域上下文。
 *
 * @author surezzzzzz
 */
public final class MySqlRouteContextHolder {

    private static final ThreadLocal<Deque<String>> CONTEXT = new ThreadLocal<>();

    private MySqlRouteContextHolder() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 将数据源名称压入当前线程的路由栈。
     *
     * @param datasource 已注册的数据源名称
     * @return 可自动恢复前序路由上下文的作用域
     */
    public static Scope push(String datasource) {
        if (datasource == null || datasource.trim().isEmpty()) {
            throw new SimpleMysqlRouteException(ErrorCode.CONTEXT_INVALID, ErrorMessage.CONTEXT_INVALID);
        }
        Deque<String> stack = CONTEXT.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            CONTEXT.set(stack);
        }
        stack.push(datasource);
        return new Scope();
    }

    /**
     * 获取当前线程栈顶的数据源名称。
     *
     * @return 当前数据源名称；不存在时返回 {@code null}
     */
    public static String current() {
        Deque<String> stack = CONTEXT.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 清除当前线程全部路由上下文。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Deque<String> stack = CONTEXT.get();
            if (stack == null || stack.isEmpty()) {
                CONTEXT.remove();
                return;
            }
            stack.pop();
            if (stack.isEmpty()) {
                CONTEXT.remove();
            }
        }
    }
}
