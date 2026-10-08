package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托角色分页（server 自持形态：items/total/page/size，页码 1 起；与 users 族的 Spring Page 形态不同）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenRolePage {

    /**
     * 当前页条目。
     */
    java.util.List<IamOpenRole> items;

    /**
     * 总数。
     */
    long total;

    /**
     * 当前页码（1 起）。
     */
    int page;

    /**
     * 页大小。
     */
    int size;
}
