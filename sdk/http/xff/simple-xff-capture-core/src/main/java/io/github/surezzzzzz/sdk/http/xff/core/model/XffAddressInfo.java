package io.github.surezzzzzz.sdk.http.xff.core.model;

import io.github.surezzzzzz.sdk.http.xff.core.constant.*;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * XFF 地址分类结果。
 *
 * <p>非法值不包含规范化 IP 和 IP 版本；原始值仍由 XFF 链保存。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = "normalizedIp")
public final class XffAddressInfo {

    /**
     * 是否为合法 IP 字面量。
     */
    private final boolean ipLiteral;

    /**
     * 规范化 IP，非法值时为空。
     */
    private final String normalizedIp;

    /**
     * IP 版本，非法值时为空。
     */
    private final XffIpVersion ipVersion;

    /**
     * 地址范围。
     */
    private final XffIpScope scope;

    /**
     * 创建地址分类结果。
     *
     * @param ipLiteral    是否为合法 IP 字面量
     * @param normalizedIp 规范化 IP
     * @param ipVersion    IP 版本
     * @param scope        地址范围
     */
    public XffAddressInfo(boolean ipLiteral, String normalizedIp,
                          XffIpVersion ipVersion, XffIpScope scope) {
        if (scope == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureCoreConstant.FIELD_IP_SCOPE));
        }
        if (ipLiteral && (normalizedIp == null || normalizedIp.isEmpty() || ipVersion == null
                || scope == XffIpScope.INVALID)) {
            throw invalidState();
        }
        if (!ipLiteral && (normalizedIp != null || ipVersion != null || scope != XffIpScope.INVALID)) {
            throw invalidState();
        }
        this.ipLiteral = ipLiteral;
        this.normalizedIp = normalizedIp;
        this.ipVersion = ipVersion;
        this.scope = scope;
    }

    private XffCaptureValidationException invalidState() {
        return new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                        SimpleXffCaptureCoreConstant.DETAIL_ADDRESS_INFO_STATE_INVALID));
    }
}
