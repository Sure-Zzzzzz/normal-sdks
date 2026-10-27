package io.github.surezzzzzz.sdk.ticket.server.spi;

import java.time.Duration;

/**
 * 附件出口 SPI（DESIGN §5.2）。默认 Noop=禁用附件；S3 适配在 server-starter（预签名直传）。
 *
 * @author surezzzzzz
 */
public interface FileProvider {

    /**
     * 签发上传预签名（服务端生成随机 objectKey，前缀 ticket/{ticketNo}/）。
     *
     * @param ticketNo    工单号（可为空=提交期暂挂，提交时落正式前缀）
     * @param fileName    文件名
     * @param contentType 内容类型
     * @return objectKey（回填校验锚）
     */
    String issueUploadKey(String ticketNo, String fileName, String contentType);

    /**
     * 签发绑定上传者的临时对象键。新实现必须覆盖此方法并在对象键中绑定主体，
     * 旧实现默认委托兼容方法，升级期间不会因为新增参数而无法启动。
     *
     * @param ticketNo        工单号（可为空=提交期暂挂）
     * @param ownerSubjectId  上传者主体标识
     * @param fileName        文件名
     * @param contentType     内容类型
     * @return 绑定上传者的对象键
     */
    default String issueUploadKey(String ticketNo, String ownerSubjectId,
                                  String fileName, String contentType) {
        return issueUploadKey(ticketNo, fileName, contentType);
    }

    /**
     * 校验临时对象键是否由指定主体签发。默认实现拒绝，避免旧适配器在未完成归属校验时放行回填。
     *
     * @param objectKey       对象键
     * @param ownerSubjectId  上传者主体标识
     * @return 是否归属于主体
     */
    default boolean isUploadKeyOwned(String objectKey, String ownerSubjectId) {
        return false;
    }

    /**
     * 签发上传预签名 URL。
     *
     * @param objectKey   对象键
     * @param size        预期大小（校验上限）
     * @param contentType 内容类型
     * @param ttl         有效期
     * @return 预签名 URL
     */
    String presignUpload(String objectKey, long size, String contentType, Duration ttl);

    /**
     * 签发下载预签名 URL（短时效，可见性校验后调用）。
     *
     * @param objectKey 对象键
     * @param ttl       有效期
     * @return 预签名 URL
     */
    String presignDownload(String objectKey, Duration ttl);

    /**
     * 校验对象存在与归属（回填时）。
     *
     * @param objectKey 对象键
     * @return 存在与否
     */
    boolean exists(String objectKey);

    /**
     * 删除对象（孤儿清理/保留期清理）。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);
}
