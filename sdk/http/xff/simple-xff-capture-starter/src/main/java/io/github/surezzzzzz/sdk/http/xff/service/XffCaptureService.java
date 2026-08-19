package io.github.surezzzzzz.sdk.http.xff.service;

import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;

import javax.servlet.http.HttpServletRequest;

/**
 * XFF 采集服务。
 *
 * @author surezzzzzz
 */
public interface XffCaptureService {

    /**
     * 采集当前请求中应用可见的完整 XFF 事实，并最多发布一次采集事件。
     *
     * @param request 当前 Servlet 请求，不能为 null
     * @return 不可变 XFF 快照，Header 不存在时返回空链模型
     */
    XffChain capture(HttpServletRequest request);
}
