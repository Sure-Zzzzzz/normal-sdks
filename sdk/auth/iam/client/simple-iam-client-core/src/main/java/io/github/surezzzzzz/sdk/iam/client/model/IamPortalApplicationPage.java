package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Portal 路由投影分页（目录版本与条目列表是同一份机器态快照）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamPortalApplicationPage {

    /**
     * 全局 Portal 目录版本。
     */
    Long catalogRevision;
    /**
     * 目录总条数。
     */
    long totalElements;
    /**
     * 当前页码（0 起）。
     */
    int page;
    /**
     * 当前页大小。
     */
    int size;
    /**
     * 当前页路由投影。
     */
    List<IamPortalApplicationRoute> items;
}
