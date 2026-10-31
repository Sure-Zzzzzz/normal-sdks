package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference;
import java.util.Collections;
import java.util.List;

/**
 * 真实实名额度快照端口。实现方必须返回已核验的当前资格，不得把请求中的编号直接当作事实。
 *
 * @author surezzzzzz
 */
public interface NamedSeatSnapshotPort {

    List<NamedSeatReference> snapshot(String grantId);

    /** 无实名提供方时的失败关闭默认值。 */
    static List<NamedSeatReference> empty() {
        return Collections.emptyList();
    }
}
