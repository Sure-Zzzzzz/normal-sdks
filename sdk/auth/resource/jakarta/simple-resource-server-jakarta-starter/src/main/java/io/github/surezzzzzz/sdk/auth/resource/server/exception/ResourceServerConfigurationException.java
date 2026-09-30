package io.github.surezzzzzz.sdk.auth.resource.server.exception;

import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import lombok.Getter;

/**
 * 资源服务配置异常。
 *
 * @author surezzzzzz
 */
@Getter
public class ResourceServerConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建资源服务配置异常。
     *
     * @param message 配置错误信息
     */
    public ResourceServerConfigurationException(String message) {
        super(message);
        this.errorCode = SimpleResourceServerStarterConstant.ERROR_CODE_CONFIGURATION;
    }
}
