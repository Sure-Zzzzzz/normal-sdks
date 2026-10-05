package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;

/**
 * create 冲突转 update 的解析器。
 *
 * @author surezzzzzz
 */
@FunctionalInterface
public interface ConflictUpdateResolver {

    /**
     * 为冲突项生成补偿更新，空返回或目标不一致会失败。
     */
    BulkItem resolve(BulkItem createItem, BulkItemFailure conflictFailure);
}

