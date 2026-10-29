package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;

import java.util.UUID;

/**
 * 类型化资源 ID 生成器：资源前缀 + UUID 短形态，全局唯一无协调。
 *
 * @author surezzzzzz
 */
public class SimpleCrmServerIdGeneratorAdapter implements CrmIdGenerator {

    @Override
    public String nextId(CrmIdResourceType resourceType) {
        String prefix = resourceType.name().toLowerCase().replace('_', '-');
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }
}
