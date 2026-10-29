package io.github.surezzzzzz.sdk.crm.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PATCH 动作分派查询参数推导器。
 *
 * <p>服务端 v2 契约对 PATCH 端点按查询参数路由并挂接逐动作权限码（客户 ?state=inactive/active/merged、
 * 转移 ?owner=目标、目录 ?state=published/disabled、报价 ?state=issued/confirmed/withdrawn、
 * 履约回报 ?sequence=N、人工重置 ?state=PENDING_DISPATCH）。本工具从 PATCH 载荷推导出等值的
 * 分派查询参数，保证请求体与路由判别值一致，供各传输件客户端复用。</p>
 *
 * @author surezzzzzz
 */
public final class CrmPatchRouting {

    private CrmPatchRouting() {
    }

    /**
     * 从 PATCH 载荷推导动作分派查询参数。
     *
     * @param patch PATCH 载荷（state/owner/sequence 任一存在即镜像进查询参数；state 与 owner 互斥）
     * @return 分派查询参数；载荷为空时返回空映射
     * @throws IllegalArgumentException 同一载荷同时包含 state 与 owner
     */
    public static Map<String, String> of(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> routing = new LinkedHashMap<String, String>();
        Object state = patch.get("state");
        if (state != null) {
            routing.put("state", String.valueOf(state));
        }
        Object owner = patch.get("owner");
        if (state != null && owner != null) {
            throw new IllegalArgumentException("state 与 owner 不能同时作为 PATCH 分派参数");
        }
        if (owner != null) {
            routing.put("owner", String.valueOf(owner));
        }
        Object sequence = patch.get("sequence");
        if (sequence != null) {
            routing.put("sequence", String.valueOf(sequence));
        }
        return routing;
    }
}
