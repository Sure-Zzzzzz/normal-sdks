package io.github.surezzzzzz.sdk.license.server.web;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * License API 权限码（包含管理面与机器在线判定入口，与登记清单逐字一致）。
 *
 * <p>登记侧唯一事实源是 deploy/iam/permission-catalog.json；本常量类与之同步维护，
 * 端点逐个精确消费，不做粗通配。</p>
 *
 * @author surezzzzzz
 */
public final class LicenseApi {

    public static final String ME_READ = "license.me.read";
    public static final String ISSUER_READ = "license.issuer.read";
    public static final String ISSUER_MANAGE = "license.issuer.manage";
    public static final String CUSTOMER_READ = "license.customer.read";
    public static final String CUSTOMER_MANAGE = "license.customer.manage";
    public static final String PRODUCT_READ = "license.product.read";
    public static final String PRODUCT_MANAGE = "license.product.manage";
    public static final String MODEL_READ = "license.model.read";
    public static final String MODEL_MANAGE = "license.model.manage";
    public static final String MODEL_PUBLISH = "license.model.publish";
    public static final String PLAN_READ = "license.plan.read";
    public static final String PLAN_MANAGE = "license.plan.manage";
    public static final String GRANT_READ = "license.grant.read";
    public static final String GRANT_MANAGE = "license.grant.manage";
    public static final String GRANT_ISSUE = "license.grant.issue";
    public static final String GRANT_DELIVER = "license.grant.deliver";
    /** 在线判定（机器通道；DESIGN.HTTP.1 §4 决策端点专用码，非人员管理面 31 码族）。 */
    public static final String DECISION_EVALUATE = "license.decision.evaluate";
    public static final String QUOTA_READ = "license.quota.read";
    public static final String QUOTA_USE = "license.quota.use";
    public static final String QUOTA_MANAGE = "license.quota.manage";
    public static final String SUBJECT_ASSIGNMENT_READ = "license.subject-assignment.read";
    public static final String SUBJECT_ASSIGNMENT_MANAGE = "license.subject-assignment.manage";
    public static final String SUBJECT_ASSIGNMENT_FORCE_REPLACE = "license.subject-assignment.force-replace";
    public static final String ACTIVATION_READ = "license.activation.read";
    public static final String ACTIVATION_MANAGE = "license.activation.manage";
    public static final String ACTIVATION_FORCE_REPLACE = "license.activation.force-replace";
    public static final String REVOCATION_READ = "license.revocation.read";
    public static final String REVOCATION_MANAGE = "license.revocation.manage";
    public static final String REVOCATION_DELIVER = "license.revocation.deliver";
    public static final String SIGNING_PROFILE_READ = "license.signing-profile.read";
    public static final String SIGNING_PROFILE_MANAGE = "license.signing-profile.manage";
    public static final String OPERATION_READ = "license.operation.read";
    public static final String OPERATION_RECOVER = "license.operation.recover";
    public static final String INTEGRATION_READ = "license.integration.read";
    public static final String INTEGRATION_MANAGE = "license.integration.manage";

    /** DATA 资源名（六资源）。 */
    public static final String RESOURCE_ISSUER = "license-issuer";
    public static final String RESOURCE_CUSTOMER = "license-customer";
    public static final String RESOURCE_PRODUCT = "license-product";
    public static final String RESOURCE_GRANT = "license-grant";
    public static final String RESOURCE_SIGNING_PROFILE = "license-signing-profile";
    public static final String RESOURCE_INTEGRATION = "license-integration";

    /** 全量码清单（自检与清单登记对账用）。 */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            ME_READ, ISSUER_READ, ISSUER_MANAGE, CUSTOMER_READ, CUSTOMER_MANAGE,
            PRODUCT_READ, PRODUCT_MANAGE, MODEL_READ, MODEL_MANAGE, MODEL_PUBLISH,
            PLAN_READ, PLAN_MANAGE, GRANT_READ, GRANT_MANAGE, GRANT_ISSUE, GRANT_DELIVER,
            SUBJECT_ASSIGNMENT_READ, SUBJECT_ASSIGNMENT_MANAGE, SUBJECT_ASSIGNMENT_FORCE_REPLACE,
            ACTIVATION_READ, ACTIVATION_MANAGE, ACTIVATION_FORCE_REPLACE,
            REVOCATION_READ, REVOCATION_MANAGE, REVOCATION_DELIVER,
            SIGNING_PROFILE_READ, SIGNING_PROFILE_MANAGE,
            OPERATION_READ, OPERATION_RECOVER, INTEGRATION_READ, INTEGRATION_MANAGE, DECISION_EVALUATE,
            QUOTA_READ, QUOTA_USE, QUOTA_MANAGE));

    private LicenseApi() {
    }
}
