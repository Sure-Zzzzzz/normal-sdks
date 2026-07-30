package io.github.surezzzzzz.sdk.crm.server.core.port.system;

import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType;

/**
 * CRM 服务端稳定标识生成端口。
 *
 * @author surezzzzzz
 */
public interface CrmIdGenerator {

    /**
     * 生成指定资源类型的唯一标识。
     *
     * @param resourceType 资源类型
     * @return 处理后的领域事实或校验结果。
     */
    String nextId(CrmIdResourceType resourceType);
}
