package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 数据权限拒绝异常（MVC 薄壳，1.1.0 起）。
 *
 * <p>契约本体已下沉 core（纯 RuntimeException）；本壳保留旧 FQCN 与 {@code @ResponseStatus}
 * 的 403 兜底语义，供既有编译产物与签名兼容。机器（拦截器/参数解析器/门面）继续抛本壳——
 * 存量 {@code catch(本壳)} 与 {@code @ExceptionHandler(本壳)} 照常命中；新代码面向 core 父类。
 * 三期（下个 major）撤壳，届时 403 兜底由装配件 advice 承担。</p>
 *
 * @author surezzzzzz
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class DataPermissionAccessDeniedException
        extends io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionAccessDeniedException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建数据权限拒绝异常。
     *
     * @param message 拒绝原因
     */
    public DataPermissionAccessDeniedException(String message) {
        super(message);
    }
}
