package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

import java.util.List;

/**
 * 新管理台使用的完整 Portal 配置快照。
 *
 * <p>独立于旧 {@link PortalIntegrationRequest}，使 {@code defaultEntry=null} 能明确表示清空，
 * 不把旧客户端未携带的字段误认为删除动作。
 */
@Data
public class PortalConfigurationRequest {

    private Boolean enabled;
    private String entry;
    private String apiBase;
    private List<PortalMenuTreeNodeRequest> menuTree;
    private PortalDefaultEntryRequest defaultEntry;
    private Long configVersion;
}
