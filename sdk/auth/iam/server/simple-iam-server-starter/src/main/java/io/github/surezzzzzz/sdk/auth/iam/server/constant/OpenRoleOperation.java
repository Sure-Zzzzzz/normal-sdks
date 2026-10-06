package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 受委托角色的审计动作，复用既有管理事件而不扩展 Core 事件族。
 *
 * @author surezzzzzz
 */
@Getter
public enum OpenRoleOperation {
    /**
     * 创建普通角色及委托记录。
     */
    CREATE_ROLE("create-role", "创建受委托角色"),
    /**
     * 修改展示信息。
     */
    UPDATE_ROLE("update-role", "修改受委托角色"),
    /**
     * 删除角色并保留墓碑。
     */
    DELETE_ROLE("delete-role", "删除受委托角色"),
    /**
     * 设置固定应用规则。
     */
    PUT_RULE("put-rule", "设置应用规则"),
    /**
     * 清除固定应用规则。
     */
    DELETE_RULE("delete-rule", "清除应用规则"),
    /**
     * 增加部门关系。
     */
    ASSIGN_DEPARTMENT("assign-department", "挂载部门"),
    /**
     * 清理部门关系。
     */
    REVOKE_DEPARTMENT("revoke-department", "撤销部门挂载");
    private final String code;
    private final String description;

    OpenRoleOperation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码解析。
     */
    public static OpenRoleOperation fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst().orElse(null);
    }

    /**
     * 判断编码有效性。
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 返回完整编码集。
     */
    public static List<String> getAllCodes() {
        return Arrays.stream(values()).map(OpenRoleOperation::getCode).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return code;
    }
}
