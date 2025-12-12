package io.github.surezzzzzz.sdk.elasticsearch.route.test;

import lombok.NonNull;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 文档索引辅助类（测试用）
 * 用于动态处理索引名称
 *
 * @author surezzzzzz
 */
public class DocumentIndexHelper {

    /**
     * 判断是否访问历史索引
     */
    private static boolean shouldAccessHistoryIndex() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        return requestAttributes instanceof ServletRequestAttributes
                && Boolean.TRUE.equals(
                requestAttributes.getAttribute(
                        DocumentIndexHelper.class.getCanonicalName() + ".access.history",
                        RequestAttributes.SCOPE_REQUEST
                )
        );
    }

    /**
     * 处理索引名称
     * 如果需要访问历史索引，添加 .history 后缀
     *
     * @param indexName 基础索引名
     * @return 处理后的索引名
     */
    @NonNull
    public static String processIndexName(@NonNull String indexName) {
        return indexName + (shouldAccessHistoryIndex() ? ".history" : "");
    }
}
