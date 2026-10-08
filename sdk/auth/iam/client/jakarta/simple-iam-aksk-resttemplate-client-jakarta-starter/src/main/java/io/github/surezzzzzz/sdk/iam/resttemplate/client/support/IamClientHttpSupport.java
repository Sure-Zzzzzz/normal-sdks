package io.github.surezzzzzz.sdk.iam.resttemplate.client.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import io.github.surezzzzzz.sdk.iam.client.exception.IamClientConfigurationException;
import io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IAM Client HTTP 共享支撑：URL 拼装（origin-only 校验+固定基路径+独立路径段）、请求执行与
 * 非 2xx 透传、If-Match 头构造、wire 解析小工具（两种分页形态）。
 *
 * <p>传输本体=底座 akskClientRestTemplate（构造注入）；非 2xx 直接透传
 * {@link HttpStatusCodeException}，2xx 形状不符抛 {@link IamClientProtocolException}。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public final class IamClientHttpSupport {

    private final RestTemplate akskClientRestTemplate;
    private final URI apiBase;

    /**
     * 创建支撑件。
     *
     * @param akskClientRestTemplate 底座预配置 RestTemplate（认证+连接池）
     * @param baseUrl                IAM 服务 origin（仅 http/https 根地址）
     */
    public IamClientHttpSupport(RestTemplate akskClientRestTemplate, String baseUrl) {
        this.akskClientRestTemplate = akskClientRestTemplate;
        URI origin = URI.create(requireText(baseUrl));
        if (origin.getPath() != null && !origin.getPath().isEmpty() && !"/".equals(origin.getPath())) {
            throw new IamClientConfigurationException();
        }
        this.apiBase = UriComponentsBuilder.fromUri(origin)
                .pathSegment(SimpleIamClientConstant.API_BASE_PATH.substring(1)).build().toUri();
    }

    private static HttpHeaders headers(String ifMatch) {
        HttpHeaders result = new HttpHeaders();
        if (ifMatch != null && !ifMatch.isEmpty()) {
            result.set(SimpleIamClientConstant.HEADER_IF_MATCH, requireText(ifMatch));
        }
        return result;
    }

    private static String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IamClientConfigurationException();
        }
        return value.trim();
    }

    /**
     * 读必填文本字段（缺失/非文本=协议错误）。
     */
    public static String text(JsonNode node, String field) {
        if (node == null || !node.path(field).isTextual()) {
            throw new IamClientProtocolException();
        }
        return node.path(field).textValue();
    }

    /**
     * 读可空文本字段（缺失/null 返回 null）。
     */
    public static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? null
                : (value.isTextual() ? value.textValue() : null);
    }

    /**
     * 读必填长整型字段。
     */
    public static long longValue(JsonNode node, String field) {
        if (node == null || !node.path(field).canConvertToLong()) {
            throw new IamClientProtocolException();
        }
        return node.path(field).longValue();
    }

    /**
     * 读可空长整型字段。
     */
    public static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? null
                : (value.canConvertToLong() ? Long.valueOf(value.longValue()) : null);
    }

    /**
     * 读可空整型字段。
     */
    public static Integer optionalInteger(JsonNode node, String field) {
        Long value = optionalLong(node, field);
        return value == null ? null : Integer.valueOf(value.intValue());
    }

    /**
     * 读必填整型字段。
     */
    public static int intValue(JsonNode node, String field) {
        if (node == null || !node.path(field).canConvertToInt()) {
            throw new IamClientProtocolException();
        }
        return node.path(field).intValue();
    }

    /**
     * 读必填布尔字段。
     */
    public static boolean booleanValue(JsonNode node, String field) {
        if (node == null || !node.path(field).isBoolean()) {
            throw new IamClientProtocolException();
        }
        return node.path(field).booleanValue();
    }

    /**
     * 读字符串数组字段（角色编码/权限码列表）。
     */
    public static java.util.List<String> stringList(JsonNode node, String field) {
        if (node == null || !node.path(field).isArray()) {
            throw new IamClientProtocolException();
        }
        java.util.List<String> result = new java.util.ArrayList<String>();
        for (JsonNode item : node.path(field)) {
            if (!item.isTextual()) {
                throw new IamClientProtocolException();
            }
            result.add(item.textValue());
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * 读可空对象数组字段（DATA 模板等保留原始结构）。
     */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> optionalMap(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw new IamClientProtocolException();
        }
        return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(value, java.util.Map.class);
    }

    // ==================== wire 解析小工具 ====================

    /**
     * GET 并返回契约 JSON。
     *
     * @param segments 资源路径段（独立段编码，禁止标识改变路径结构）
     * @return 契约 JSON
     */
    public JsonNode get(String... segments) {
        return exchange(HttpMethod.GET, uri(segments), null, null);
    }

    /**
     * GET 带查询参数（null 值省略）。
     *
     * @param segments 资源路径段
     * @param query    查询参数名值对（null 值不产生查询项）
     * @return 契约 JSON
     */
    public JsonNode get(Query query, String... segments) {
        UriComponentsBuilder builder = path(segments);
        for (Map.Entry<String, Object> item : query.values.entrySet()) {
            if (item.getValue() != null) {
                builder.queryParam(item.getKey(), item.getValue());
            }
        }
        return exchange(HttpMethod.GET, builder.build().encode().toUri(), null, null);
    }

    /**
     * POST 请求体（幂等类端点无专用头）。
     *
     * @param segments 资源路径段
     * @param body     JSON 请求体（Map；可选字段 null 不放入）
     * @return 契约 JSON
     */
    public JsonNode post(Map<String, Object> body, String... segments) {
        return exchange(HttpMethod.POST, uri(segments), null, body);
    }

    /**
     * PUT 请求体（可选携带 If-Match 乐观并发条件）。
     *
     * @param segments 资源路径段
     * @param ifMatch  乐观并发条件（null=首写；值形如 open-role:&lt;id&gt;:&lt;revision&gt;）
     * @param body     JSON 请求体
     * @return 契约 JSON
     */
    public JsonNode put(String ifMatch, Map<String, Object> body, String... segments) {
        return exchange(HttpMethod.PUT, uri(segments), headers(ifMatch), body);
    }

    /**
     * PATCH 请求体（用户资料更新）。
     *
     * @param segments 资源路径段
     * @param body     JSON 请求体
     * @return 契约 JSON
     */
    public JsonNode patch(Map<String, Object> body, String... segments) {
        return exchange(HttpMethod.PATCH, uri(segments), null, body);
    }

    /**
     * DELETE（204 语义；可选携带 If-Match）。
     *
     * @param segments 资源路径段
     * @param ifMatch  乐观并发条件（null=不携带）
     */
    public void delete(String ifMatch, String... segments) {
        exchange(HttpMethod.DELETE, uri(segments), headers(ifMatch), null);
    }

    /**
     * 执行请求：入口 DEBUG 只记方法与路径；非 2xx WARN 后透传。
     */
    private JsonNode exchange(HttpMethod method, URI target, HttpHeaders headers, Object body) {
        log.debug("IAM client request: method={} path={}", method, target.getPath());
        try {
            HttpEntity<Object> entity = new HttpEntity<Object>(body, headers);
            ResponseEntity<JsonNode> response =
                    akskClientRestTemplate.exchange(target, method, entity, JsonNode.class);
            log.debug("IAM client request done: method={} path={} status={}",
                    method, target.getPath(), response.getStatusCodeValue());
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            log.warn("IAM client request rejected: method={} path={} status={}",
                    method, target.getPath(), exception.getStatusCode().value());
            throw exception;
        }
    }

    private URI uri(String... segments) {
        return path(segments).build().encode().toUri();
    }

    private UriComponentsBuilder path(String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(apiBase);
        for (String segment : segments) {
            builder.pathSegment(requireText(segment));
        }
        return builder;
    }

    /**
     * 查询参数构造器（null 值省略语义）。
     */
    public static final class Query {
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();

        /**
         * 追加查询参数（值 null 时不产生查询项）。
         */
        public Query add(String name, Object value) {
            if (value != null) {
                values.put(name, value);
            }
            return this;
        }
    }
}
