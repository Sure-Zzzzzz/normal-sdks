package io.github.surezzzzzz.sdk.ticket.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理组聚合（ticket_group 表）。组员增删即时生效；有在途单不可删除只可停用（DESIGN §6.2）。
 *
 * <p>组长特权=分派/转派（成员可受理/处理）；成员被移出组后名下在途单继续由其处理（assignee 以单为准）。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroup {

    private Long id;

    /**
     * 组编码
     */
    private String code;

    /**
     * 组名称
     */
    private String name;

    /**
     * 组员标识列表（DATA 组内可见维度）
     */
    @Builder.Default
    private List<String> memberSubjectIds = new ArrayList<>();

    /**
     * 组长标识（分派/转派特权）
     */
    private String leaderSubjectId;

    /**
     * 启用状态（停用后不接新单路由，存量不碍）
     */
    private boolean enabled;

    /**
     * 是否组员。
     *
     * @param subjectId 人员标识
     * @return 在组与否
     */
    public boolean hasMember(String subjectId) {
        return subjectId != null && memberSubjectIds != null && memberSubjectIds.contains(subjectId);
    }

    /**
     * 是否组长。
     *
     * @param subjectId 人员标识
     * @return 组长与否
     */
    public boolean isLeader(String subjectId) {
        return subjectId != null && subjectId.equals(leaderSubjectId);
    }
}
