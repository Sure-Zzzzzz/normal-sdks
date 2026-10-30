package io.github.surezzzzzz.sdk.license.core.spi;

import java.util.Map;
import java.util.Optional;

/**
 * 设备证据提供端口：采集当前部署环境硬件分量（部署绑定专用）。
 *
 * <p>人员/服务的设备政策为 NONE，不使用本端口。采集规范对无效默认值、空串、重复通用 SN
 * 作不可用处理；VM 虚拟标识可复制、容器可能无法取得证据。</p>
 *
 * @author surezzzzzz
 */
public interface DeviceEvidenceProvider {

    /** 当前可采集分量名到规范化值（trim/NFC/UUID 小写/SN 原大小写）。 */
    Map<String, String> currentComponents();

    /** 设备私钥持有性挑战（DEVICE_KEY 模式）。 */
    Optional<byte[]> signChallenge(byte[] challenge);
}
