package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 统一 API 响应类（不可变对象）
 *
 * <p>支持 Jackson 序列化和反序列化（客户端可直接使用）
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

    /**
     * Jackson 反序列化构造函数
     */
    @JsonCreator
    private ApiResponse(
            @JsonProperty("data") T data,
            @JsonProperty("error") String error) {
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
