package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Value;

/**
 * 路由输入校验结果。预期的业务拒绝以结果返回，不使用通用参数异常作为控制流。
 *
 * @author surezzzzzz
 */
@Value
public class RouteValidationResult {

    /**
     * 是否通过。
     */
    boolean accepted;
    /**
     * 结构化原因。
     */
    RouteValidationReason reason;
    /**
     * 发生问题的应用编码；空值表示目录级结果。该字段只用于结构化诊断，事件层不得直接输出原值。
     */
    String applicationCode;

    /**
     * 创建通过结果。
     */
    public static RouteValidationResult accepted() {
        return new RouteValidationResult(true, RouteValidationReason.ACCEPTED, null);
    }

    /**
     * 创建目录级拒绝结果。
     */
    public static RouteValidationResult rejected(RouteValidationReason reason) {
        return new RouteValidationResult(false, reason, null);
    }

    /**
     * 创建应用级拒绝结果。
     */
    public static RouteValidationResult rejected(RouteValidationReason reason, String applicationCode) {
        return new RouteValidationResult(false, reason, applicationCode);
    }
}
