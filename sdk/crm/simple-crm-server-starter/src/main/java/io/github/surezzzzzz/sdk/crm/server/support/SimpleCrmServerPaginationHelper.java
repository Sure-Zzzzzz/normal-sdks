package io.github.surezzzzzz.sdk.crm.server.support;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;

import java.util.Map;

/**
 * HTTP 分页边界：页码和游标互斥，响应只返回所选模式的元数据。
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerPaginationHelper {

    private static final String FIELD_PAGE = "page";
    private static final String FIELD_PAGE_SIZE = "pageSize";
    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_NEXT_CURSOR = "nextCursor";
    private static final String FIELD_CURSOR = "cursor";
    private static final String FIELD_LIMIT = "limit";
    private static final String VALIDATION_FIELD = "pagination";

    private SimpleCrmServerPaginationHelper() {
    }

    /**
     * 选择分页模式，禁止同一次请求混用两类参数。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param cursor 游标
     * @param limit 游标条数
     * @param parameters 原始查询参数，用于识别空值及参数是否出现
     * @return 页码请求，纯游标模式为空
     */
    public static CrmPageRequest request(Integer page, Integer pageSize, String cursor, Integer limit,
                                         Map<String, String> parameters) {
        validateParameters(parameters, false);
        if (page == null && pageSize == null) {
            return null;
        }
        if (cursor != null || limit != null) {
            throw CrmException.validation(VALIDATION_FIELD);
        }
        return new CrmPageRequest(page == null ? CrmPageRequest.DEFAULT_PAGE : page,
                pageSize == null ? CrmPageRequest.DEFAULT_PAGE_SIZE : pageSize);
    }

    /**
     * 选择仅支持页码的列表模式；无分页参数时保留完整列表契约。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param cursor 不受支持的游标参数
     * @param limit 不受支持的游标条数参数
     * @param parameters 原始查询参数
     * @return 页码请求，无分页参数时为空
     */
    public static CrmPageRequest numberedRequest(Integer page, Integer pageSize,
                                                String cursor, String limit,
                                                Map<String, String> parameters) {
        validateParameters(parameters, true);
        if (cursor != null || limit != null) {
            throw CrmException.validation(VALIDATION_FIELD);
        }
        return request(page, pageSize, null, null, parameters);
    }

    /** 空值不能被 MVC 的可选整数绑定转换成 null 后冒充未传参数。 */
    private static void validateParameters(Map<String, String> parameters, boolean numberedOnly) {
        boolean numbered = parameters.containsKey(FIELD_PAGE) || parameters.containsKey(FIELD_PAGE_SIZE);
        boolean cursor = parameters.containsKey(FIELD_CURSOR) || parameters.containsKey(FIELD_LIMIT);
        if (cursor && (numberedOnly || numbered)) {
            throw CrmException.validation(VALIDATION_FIELD);
        }
        String[] fields = {FIELD_PAGE, FIELD_PAGE_SIZE, FIELD_CURSOR, FIELD_LIMIT};
        for (String field : fields) {
            if (parameters.containsKey(field)
                    && (parameters.get(field) == null || parameters.get(field).trim().isEmpty())) {
                throw CrmException.validation(VALIDATION_FIELD);
            }
        }
    }

    /**
     * 校验单页条数；页码请求的条数由页码请求对象传递。
     *
     * @param limit 游标条数
     * @return 有效条数
     */
    public static int limit(Integer limit) {
        int value = limit == null ? CrmPageRequest.DEFAULT_PAGE_SIZE : limit;
        if (value < CrmPageRequest.DEFAULT_PAGE || value > CrmPageRequest.MAX_PAGE_SIZE) {
            throw CrmException.validation(VALIDATION_FIELD);
        }
        return value;
    }

    /**
     * 获取所选模式的单页条数。
     *
     * @param limit 游标条数
     * @param request 页码请求
     * @return 实际单页条数
     */
    public static int limit(Integer limit, CrmPageRequest request) {
        return request == null ? limit(limit) : request.getPageSize();
    }

    /**
     * 写入当前模式的响应元数据。
     *
     * @param body 响应对象
     * @param page 分页结果
     */
    public static void metadata(Map<String, Object> body, CrmPage<?> page) {
        if (page.getTotal() == null) {
            body.put(FIELD_NEXT_CURSOR, page.getNextCursor());
        } else {
            body.put(FIELD_PAGE, page.getPage());
            body.put(FIELD_PAGE_SIZE, page.getPageSize());
            body.put(FIELD_TOTAL, page.getTotal());
        }
    }
}
