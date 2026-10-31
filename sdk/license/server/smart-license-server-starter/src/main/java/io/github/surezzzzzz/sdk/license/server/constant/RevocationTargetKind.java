package io.github.surezzzzzz.sdk.license.server.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 吊销目标联合：每种编号只能在对应领域表中确认，不能跨种类猜归属。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum RevocationTargetKind {

    CREDENTIAL("CREDENTIAL", "凭据"),
    GRANT("GRANT", "授权"),
    ACTIVATION("ACTIVATION", "部署激活"),
    ASSIGNMENT("ASSIGNMENT", "固定资格"),
    SIGNING_KEY("SIGNING_KEY", "协议签名密钥");

    private final String code;
    private final String description;

    /** 严格按编码解析，未知值拒绝。 */
    public static RevocationTargetKind fromCode(String code) {
        for (RevocationTargetKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(RevocationConstant.INVALID_KIND);
    }

    /** 检查编码是否属于已声明目标集合。 */
    public static boolean isValid(String code) {
        for (RevocationTargetKind kind : values()) {
            if (kind.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 返回所有稳定编码。 */
    public static String[] getAllCodes() {
        RevocationTargetKind[] kinds = values();
        String[] codes = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            codes[i] = kinds[i].code;
        }
        return codes;
    }
}
