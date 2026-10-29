package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmClock;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerTimeHelper;

import java.time.Instant;

/**
 * 系统时钟适配：生产用真实时钟，测试可替换。
 *
 * @author surezzzzzz
 */
public class SimpleCrmServerClockAdapter implements CrmClock {

    @Override
    public Instant now() {
        return SimpleCrmServerTimeHelper.truncateToMillis(Instant.now());
    }
}
