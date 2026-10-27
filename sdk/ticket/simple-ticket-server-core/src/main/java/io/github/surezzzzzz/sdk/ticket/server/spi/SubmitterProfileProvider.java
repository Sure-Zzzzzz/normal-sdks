package io.github.surezzzzzz.sdk.ticket.server.spi;

/**
 * 人员档案 SPI（DESIGN §5.2）。默认 Noop=显示 subjectId；IAM 适配取真实姓名（聚形态）。
 *
 * @author surezzzzzz
 */
public interface SubmitterProfileProvider {

    /**
     * 取人员显示名快照。
     *
     * @param subjectId 人员标识
     * @return 姓名快照（查不到回落 subjectId）
     */
    String displayNameOf(String subjectId);
}
