package io.github.surezzzzzz.sdk.license.core.spi;

import java.util.Optional;

/**
 * IAM 组织目录准入端口：CUSTOMER_MEMBERS 在线判定的部门子树成员归属回查。
 *
 * <p>聚散埋点：本接口是 license 自有 SPI——实现由宿主骑 IAM 官方 client
 * （IamOpenRoleClient 的组织目录查询）装配；未装配时绑定客户的判定按失败关闭
 * （确定拒绝，不静默放行）。子树语义由 IAM 侧目录承担（授权根内=准入）。</p>
 *
 * @author surezzzzzz
 */
public interface IamDirectoryPort {

    /**
     * 回查主体是否在授权根部门的目录子树内。
     *
     * @param rootDepartmentId 客户绑定的授权根部门
     * @param subjectId        公开主体标识
     * @return true=子树内（准入）；false=子树外或主体不存在（拒绝）；
     *         empty=目录暂不可达（失败关闭，调用方按拒绝处理并留重试语义）
     */
    Optional<Boolean> isMemberWithinRoot(long rootDepartmentId, String subjectId);
}
