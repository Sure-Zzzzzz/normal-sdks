package io.github.surezzzzzz.sdk.license.core.service;

import io.github.surezzzzzz.sdk.license.core.model.IssuedLicense;
import io.github.surezzzzzz.sdk.license.core.model.LicenseIssueCommand;

/**
 * License 签发服务。
 *
 * @author surezzzzzz
 */
public interface LicenseIssuanceService {

    /**
     * 按当前 tenant 与业务 kid 签发 Compact JWS。
     *
     * @param command 签发命令
     * @return 已签发 License
     */
    IssuedLicense issue(LicenseIssueCommand command);
}
