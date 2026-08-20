package io.github.surezzzzzz.sdk.audit.http.xff.provider;

import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;

/**
 * XFF Capture 审计文档投影 Provider。
 *
 * <p>Provider 在调用线程中同步处理一份已经完成快照的不可变文档。</p>
 *
 * @author surezzzzzz
 */
public interface XffCaptureAuditPersistenceProvider {

    /**
     * 持久化或投影一条 XFF 审计文档。
     *
     * @param document 审计文档
     */
    void persist(XffCaptureAuditDocument document);
}
