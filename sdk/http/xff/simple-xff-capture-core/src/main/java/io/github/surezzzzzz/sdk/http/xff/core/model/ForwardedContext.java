package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * HTTP 入口转发上下文。
 *
 * <p>所有字段都是采集执行时 Servlet 容器暴露的独立原始 Header 快照，不做
 * fallback、相互替代、合并、校验或可信解释；不承诺还原更早组件已经消费的入口 Header。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"host", "xRealIp", "xForwardedHost", "xForwardedPort", "xForwardedProto"})
public final class ForwardedContext {

    /**
     * Host Header 快照。
     */
    private final HeaderValueSnapshot host;

    /**
     * X-Real-IP Header 快照。
     */
    private final HeaderValueSnapshot xRealIp;

    /**
     * X-Forwarded-Host Header 快照。
     */
    private final HeaderValueSnapshot xForwardedHost;

    /**
     * X-Forwarded-Port Header 快照。
     */
    private final HeaderValueSnapshot xForwardedPort;

    /**
     * X-Forwarded-Proto Header 快照。
     */
    private final HeaderValueSnapshot xForwardedProto;

    /**
     * 创建入口转发上下文。
     *
     * @param host            Host 快照
     * @param xRealIp         X-Real-IP 快照
     * @param xForwardedHost  X-Forwarded-Host 快照
     * @param xForwardedPort  X-Forwarded-Port 快照
     * @param xForwardedProto X-Forwarded-Proto 快照
     */
    public ForwardedContext(HeaderValueSnapshot host, HeaderValueSnapshot xRealIp,
                            HeaderValueSnapshot xForwardedHost, HeaderValueSnapshot xForwardedPort,
                            HeaderValueSnapshot xForwardedProto) {
        this.host = requireSnapshot(host, SimpleXffCaptureCoreConstant.FIELD_HOST);
        this.xRealIp = requireSnapshot(xRealIp, SimpleXffCaptureCoreConstant.FIELD_X_REAL_IP);
        this.xForwardedHost = requireSnapshot(xForwardedHost, SimpleXffCaptureCoreConstant.FIELD_X_FORWARDED_HOST);
        this.xForwardedPort = requireSnapshot(xForwardedPort, SimpleXffCaptureCoreConstant.FIELD_X_FORWARDED_PORT);
        this.xForwardedProto = requireSnapshot(xForwardedProto, SimpleXffCaptureCoreConstant.FIELD_X_FORWARDED_PROTO);
    }

    private static HeaderValueSnapshot requireSnapshot(HeaderValueSnapshot value, String name) {
        if (value == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, name));
        }
        return value;
    }
}
