package io.github.surezzzzzz.sdk.auth.data.permission.core.spi;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

import java.util.Optional;

/**
 * 已验证授权文档来源。
 *
 * @author surezzzzzz
 */
public interface DataGrantDocumentSource {

    /**
     * 获取当前调用方的单一权威授权文档。
     *
     * @return 授权文档；没有授权文档时返回空 Optional，Optional 本身不得为 null
     */
    Optional<DataGrantDocument> currentDocument();
}
