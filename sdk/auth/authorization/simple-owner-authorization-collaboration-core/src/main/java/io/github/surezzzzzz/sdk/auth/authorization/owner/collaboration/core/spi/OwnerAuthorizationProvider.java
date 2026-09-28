package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChangePage;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;

import java.util.List;

/**
 * 所属人授权投影 SPI。
 *
 * <p>外部身份源向消费端提供所属人授权投影的唯一中性扩展点。</p>
 *
 * @author surezzzzzz
 */
public interface OwnerAuthorizationProvider {

    /**
     * provider 是否已连接到一个可用的外部授权源。
     *
     * @return true 表示已连接可用；false 表示未装配或连接不可用
     */
    default boolean isAvailable() {
        return false;
    }

    /**
     * 读取所属人在目标应用的当前最终授权投影。
     *
     * @param owner               所属人稳定键
     * @param targetApplicationId 目标应用标识
     * @return 中性读取结果；不可用或失败关闭时返回无效结果
     */
    OwnerAuthorizationReadResult resolve(OwnerAuthorizationKey owner, Long targetApplicationId);

    /**
     * 列出所属人自助创建时可选择的候选应用目录。
     *
     * @param owner 所属人稳定键
     * @return 候选应用列表；不可用或失败关闭时返回空列表
     */
    List<OwnerAuthorizationCandidate> listCandidates(OwnerAuthorizationKey owner);

    /**
     * 拉取指定位点之后的有序最终态变更页。
     *
     * @param afterSequence 上次消费位点
     * @param pageSize      页大小
     * @return 有序变更页；不可用时返回不可用页
     */
    OwnerAuthorizationChangePage pullChanges(Long afterSequence, int pageSize);

}
