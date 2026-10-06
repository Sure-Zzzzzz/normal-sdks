package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamOpenRoleLongDeserializer;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 以明确清单版本替换角色规则；角色修改版本通过 If-Match 传递。
 *
 * @author surezzzzzz
 */
@Data
public class PutOpenRoleRuleRequest {
    /**
     * 刚核对的权限清单版本。
     */
    @JsonDeserialize(using = IamOpenRoleLongDeserializer.class)
    private Long manifestVersion;
    /**
     * 权限清单的十六进制 SHA-256 摘要。
     */
    private String manifestDigest;
    /**
     * 页面权限集合，必填，可为空。
     */
    private List<String> pagePermissions;
    /**
     * 接口权限集合，必填，可为空。
     */
    private List<String> apiPermissions;
    /**
     * 数据授权模板，null 表示无数据授权。
     */
    private Map<String, Object> dataGrantTemplate;
}
