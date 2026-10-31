package io.github.surezzzzzz.sdk.license.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import javax.servlet.http.HttpServletRequest;

/**
 * Controller 公共支撑：请求上下文承接、精确 API 权限复核、DATA 准入围栏、受控 JSON 解析。
 *
 * <p>API 复核与公共资源链的路径规则是两道独立校验（资源链拦路径，此处拦业务语义），
 * 宿主仅提供主体解析器时端点权限不退化为"已认证即可访问"（KMS 先例同构）。</p>
 *
 * @author surezzzzzz
 */
public class LicenseHttpSupport {

    /** 请求体字节上限（管理面表单远小于协议凭据预算；超限 413）。 */
    private static final int BODY_MAX_BYTES = 262144;

    private static final ObjectMapper MAPPER = StrictJson.mapper();

    /**
     * DATA 准入围栏：当前网关角色使用 ALLOW_ALL 授权项。
     * 受限 IN 计划的逐表 SQL 过滤随各查询实施接入；在接入前对非 ALLOW_ALL 明确拒绝
     * （失败关闭，不静默放行全量数据）。
     */
    public void requireDataAll(LicenseRequestContext context, String resource, String action) {
        String document = context.getDataGrantDocumentJson();
        if (document == null) {
            throw LicenseHttpException.forbidden();
        }
        try {
            JsonNode grants = MAPPER.readTree(document).get("grants");
            if (grants == null || !grants.isArray()) {
                throw LicenseHttpException.forbidden();
            }
            for (JsonNode grant : grants) {
                if (resource.equals(grant.path("resource").asText())
                        && grant.path("actions").isArray() && containsText(grant.get("actions"), action)
                        && grant.path("all").asBoolean(false)) {
                    return;
                }
            }
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (Exception failure) {
            throw LicenseHttpException.forbidden();
        }
        // 命中资源与动作但非 all=true：受限计划未实现逐维过滤，明确 403（不降级放行）
        throw LicenseHttpException.forbidden();
    }

    /** 承接公共资源链结论；未认证/形态不符统一 401。 */
    public LicenseRequestContext context(DefaultLicensePrincipalResolver resolver,
                                         HttpServletRequest request) {
        LicenseRequestContext context = resolver.resolve(request);
        if (context == null) {
            throw LicenseHttpException.unauthenticated();
        }
        return context;
    }

    /** 精确 API 码复核（领域边界独立于资源链路径规则）。 */
    public LicenseRequestContext requireApiPermission(LicenseRequestContext context, String permission) {
        if (context == null || !context.getApiPermissions().contains(permission)) {
            throw LicenseHttpException.forbidden();
        }
        return context;
    }

    /** 受控请求体解析：字节上限+白名单字段（未知字段拒绝，防字段走私）。 */
    public ObjectNode object(String body, String... allowedFields) {
        if (body == null) {
            throw LicenseHttpException.badRequest("请求体不得为空");
        }
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > BODY_MAX_BYTES) {
            throw LicenseHttpException.payloadTooLarge();
        }
        try {
            ObjectNode node = StrictJson.parse(body, ObjectNode.class);
            java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(allowedFields));
            for (java.util.Iterator<String> fields = node.fieldNames(); fields.hasNext(); ) {
                if (!allowed.contains(fields.next())) {
                    throw LicenseHttpException.unprocessable("请求含未声明字段");
                }
            }
            return node;
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw LicenseHttpException.badRequest("请求体不是合法 JSON 对象");
        }
    }

    public String text(ObjectNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw LicenseHttpException.unprocessable("缺少必填字段：" + field);
            }
            return null;
        }
        if (!value.isTextual() || value.asText().isEmpty() || value.asText().length() > 128) {
            throw LicenseHttpException.unprocessable("字段形态非法：" + field);
        }
        return value.asText();
    }

    public Integer integer(ObjectNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw LicenseHttpException.unprocessable("缺少必填字段：" + field);
            }
            return null;
        }
        if (!value.isInt()) {
            throw LicenseHttpException.unprocessable("字段形态非法：" + field);
        }
        return value.asInt();
    }

    private static boolean containsText(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }
}
