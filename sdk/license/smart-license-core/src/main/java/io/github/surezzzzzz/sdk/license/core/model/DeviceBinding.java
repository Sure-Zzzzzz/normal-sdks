package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备绑定政策：NONE/WEIGHTED/DEVICE_KEY 三档（仅部署使用）。
 *
 * <p>人员/服务只能 NONE，不采集或伪造设备证据；模型允许多对象种类时非 NONE 设备政策
 * 使人员/服务组合非法。</p>
 *
 * @author surezzzzzz
 */
public abstract class DeviceBinding {

    /** 绑定模式。 */
    public enum Mode {
        NONE("NONE"), WEIGHTED("WEIGHTED"), DEVICE_KEY("DEVICE_KEY");

        private final String code;

        Mode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Mode mode;

    private DeviceBinding(Mode mode) {
        this.mode = mode;
    }

    public static DeviceBinding none() {
        return new None();
    }

    public static Weighted weighted(String profileVersion, Map<String, String> componentDigests,
                                    Map<String, Integer> componentWeights, int minimumAvailableWeight,
                                    int maximumMismatchWeight) {
        return new Weighted(profileVersion, componentDigests, componentWeights,
                minimumAvailableWeight, maximumMismatchWeight);
    }

    public static DeviceKey deviceKey(byte[] spkiFingerprint) {
        return new DeviceKey(spkiFingerprint);
    }

    public Mode getMode() {
        return mode;
    }

    /** 无绑定。 */
    public static final class None extends DeviceBinding {
        private None() {
            super(Mode.NONE);
        }
    }

    /** 加权分量绑定：已绑定分量指纹+权重+最低可用+最大不匹配。 */
    public static final class Weighted extends DeviceBinding {

        private final String profileVersion;
        private final Map<String, String> componentDigests;
        private final Map<String, Integer> componentWeights;
        private final int minimumAvailableWeight;
        private final int maximumMismatchWeight;

        private Weighted(String profileVersion, Map<String, String> componentDigests,
                         Map<String, Integer> componentWeights, int minimumAvailableWeight,
                         int maximumMismatchWeight) {
            super(Mode.WEIGHTED);
            if (profileVersion == null || profileVersion.isEmpty() || componentDigests == null
                    || componentDigests.isEmpty() || componentWeights == null || componentWeights.isEmpty()) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            int totalWeight = 0;
            for (Map.Entry<String, Integer> entry : componentWeights.entrySet()) {
                if (entry.getValue() < 1 || entry.getValue() > 100 || !componentDigests.containsKey(entry.getKey())) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                totalWeight += entry.getValue();
            }
            if (minimumAvailableWeight <= 0 || maximumMismatchWeight < 0
                    || maximumMismatchWeight >= minimumAvailableWeight || minimumAvailableWeight > totalWeight) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            this.profileVersion = profileVersion;
            this.componentDigests = Collections.unmodifiableMap(new LinkedHashMap<String, String>(componentDigests));
            this.componentWeights = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(componentWeights));
            this.minimumAvailableWeight = minimumAvailableWeight;
            this.maximumMismatchWeight = maximumMismatchWeight;
        }

        public String getProfileVersion() {
            return profileVersion;
        }

        public Map<String, String> getComponentDigests() {
            return componentDigests;
        }

        public Map<String, Integer> getComponentWeights() {
            return componentWeights;
        }

        public int getMinimumAvailableWeight() {
            return minimumAvailableWeight;
        }

        public int getMaximumMismatchWeight() {
            return maximumMismatchWeight;
        }
    }

    /** 设备公钥绑定：SPKI 指纹+挑战持有性。 */
    public static final class DeviceKey extends DeviceBinding {

        private final byte[] spkiFingerprint;

        private DeviceKey(byte[] spkiFingerprint) {
            super(Mode.DEVICE_KEY);
            if (spkiFingerprint == null || spkiFingerprint.length != 32) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            this.spkiFingerprint = spkiFingerprint.clone();
        }

        public byte[] getSpkiFingerprint() {
            return spkiFingerprint.clone();
        }
    }
}
