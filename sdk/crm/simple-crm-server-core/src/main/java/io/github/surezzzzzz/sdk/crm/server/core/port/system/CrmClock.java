package io.github.surezzzzzz.sdk.crm.server.core.port.system;

import java.time.Instant;

/**
 * CRM 权威时钟端口。
 *
 * @author surezzzzzz
 */
public interface CrmClock {

    /**
     * 获取权威当前时间。
     *
     * @return 处理后的领域事实或校验结果。
     */
    Instant now();
}
