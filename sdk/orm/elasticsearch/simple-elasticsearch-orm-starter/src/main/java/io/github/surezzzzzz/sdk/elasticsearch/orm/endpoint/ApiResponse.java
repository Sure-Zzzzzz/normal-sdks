package io.github.surezzzzzz.sdk.elasticsearch.orm.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 统一 API 响应类（不可变对象）
 *
 * @author surezzzzzz
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应数据（成功时返回）
     */
    private final T data;

    /**
     * 错误信息（失败时返回）
     */
    private final String error;

    private ApiResponse(T data, String error) {
        this.data = data;
        this.error = error;
    }

    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    /**
     * 错误响应
     */
    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(null, error);
    }
}
