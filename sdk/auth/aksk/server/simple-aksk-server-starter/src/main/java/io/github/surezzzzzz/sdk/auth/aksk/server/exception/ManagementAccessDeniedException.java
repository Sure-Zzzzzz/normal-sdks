package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

/**
 * 管理 REST 数据范围不足异常。
 *
 * @author surezzzzzz
 */
public class ManagementAccessDeniedException extends SimpleAkskServerException {

    public ManagementAccessDeniedException() {
        super("管理数据范围不足");
    }
}
