package io.github.surezzzzzz.sdk.kms.core.repository;

import java.time.Instant;

/**
 * KMS 权威时间端口。
 *
 * <p>授权到期、销毁到期和幂等记录过期必须从同一权威时间来源判断，测试可替换该端口得到确定性时间。</p>
 *
 * @author surezzzzzz
 */
public interface KmsClock {

    /**
     * 获取当前权威时间。
     *
     * @return 当前时间点
     */
    Instant now();
}
