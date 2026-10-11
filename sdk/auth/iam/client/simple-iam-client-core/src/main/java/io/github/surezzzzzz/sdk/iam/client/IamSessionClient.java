package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamSession;

import java.util.List;

/**
 * IAM 会话 openapi 契约（2 方法，对位 server /iam/api/sessions，1.3.7）。
 *
 * <p>风控视角：按用户列在线会话、踢指定会话。码 {@code iam:session:api}；无 DATA 面；
 * listBySubject 的 subjectId 必填（不开放全库会话分页）。</p>
 *
 * @author surezzzzzz
 */
public interface IamSessionClient {

    /**
     * 按用户列在线会话。
     *
     * @param subjectId 公开主体（必填）
     * @param page      页码（1 起）
     * @param size      页大小
     * @return 在线会话列表（sessionId/最近活跃等）
     */
    List<IamSession> listBySubject(String subjectId, Integer page, Integer size);

    /**
     * 踢指定会话（204；失效即时生效）。
     *
     * @param sessionId 会话标识
     */
    void revokeSession(String sessionId);
}
