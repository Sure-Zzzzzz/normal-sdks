package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.iam.client.exception.IamClientProtocolException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IAM Portal 机器路由目录分页 wire 模型（1.3.8）。
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class IamPortalApplicationPageResponse {

    /**
     * 全局目录版本。
     */
    public Long catalogRevision;
    /**
     * 目录总条数。
     */
    public long totalElements;
    /**
     * 当前页码。
     */
    public int page;
    /**
     * 当前页大小。
     */
    public int size;
    /**
     * 当前页路由条目。
     */
    public List<IamPortalApplicationResponse> items;

    public IamPortalApplicationPageResponse() {
    }

    /**
     * 对 2xx 响应执行局部契约校验，避免全局 Jackson 配置或其他 DTO 解析行为受到影响。
     */
    @JsonCreator
    public IamPortalApplicationPageResponse(
            @JsonProperty("catalogRevision") Object catalogRevision,
            @JsonProperty("totalElements") Object totalElements,
            @JsonProperty("page") Object page,
            @JsonProperty("size") Object size,
            @JsonProperty("items") Object items) {
        this.catalogRevision = Long.valueOf(requireInteger(catalogRevision, 1L, Long.MAX_VALUE, "catalogRevision"));
        this.totalElements = requireInteger(totalElements, 0L, Long.MAX_VALUE, "totalElements");
        this.page = (int) requireInteger(page, 0L, Integer.MAX_VALUE, "page");
        this.size = (int) requireInteger(size, 1L, Integer.MAX_VALUE, "size");
        this.items = requireItems(items);
    }

    private static List<IamPortalApplicationResponse> requireItems(Object value) {
        if (!(value instanceof List)) {
            throw protocolError("items");
        }
        List<IamPortalApplicationResponse> result = new ArrayList<IamPortalApplicationResponse>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) {
                throw protocolError("items");
            }
            Map<?, ?> fields = (Map<?, ?>) item;
            result.add(new IamPortalApplicationResponse(
                    fields.get("applicationCode"),
                    fields.get("applicationName"),
                    fields.get("status"),
                    fields.get("portalEnabled"),
                    fields.get("routePrefix"),
                    fields.get("entry"),
                    fields.get("apiBase"),
                    fields.get("targetOrigin"),
                    fields.get("configVersion"),
                    fields.get("forwardAuthorization"),
                    fields.get("forwardCookieNames")));
        }
        return result;
    }

    private static long requireInteger(Object value, long minimum, long maximum, String field) {
        if (!(value instanceof Number)) {
            throw protocolError(field);
        }
        Number number = (Number) value;
        long result;
        if (number instanceof Integer || number instanceof Long
                || number instanceof Short || number instanceof Byte) {
            result = number.longValue();
        } else if (number instanceof BigInteger) {
            BigInteger bigInteger = (BigInteger) number;
            if (bigInteger.compareTo(BigInteger.valueOf(minimum)) < 0
                    || bigInteger.compareTo(BigInteger.valueOf(maximum)) > 0) {
                throw protocolError(field);
            }
            result = bigInteger.longValue();
        } else {
            throw protocolError(field);
        }
        if (result < minimum || result > maximum) {
            throw protocolError(field);
        }
        return result;
    }

    private static IamClientProtocolException protocolError(String field) {
        log.debug("IAM Portal 机器目录响应违反 wire 契约，字段={}", field);
        return new IamClientProtocolException();
    }
}
