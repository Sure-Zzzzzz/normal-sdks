package io.github.surezzzzzz.sdk.license.server.event;

import java.time.Instant;
import lombok.Value;

/**
 * 角色协作状态提交事件，只承载计划与安全状态，不携带规则、部门名单、人员或认证材料。
 * 服务协调事件不推断请求人员身份；人员请求审计由资源入口记录。
 *
 * @author surezzzzzz
 */
@Value
public class RoleProjectionChanged {
    String eventId;
    int eventVersion;
    Instant occurredAt;
    String projectionId;
    String commandId;
    long desiredRevision;
    String executionMode;
    String state;
    String reasonCategory;
}
