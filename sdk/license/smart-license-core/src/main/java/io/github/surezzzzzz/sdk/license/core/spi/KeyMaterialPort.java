package io.github.surezzzzzz.sdk.license.core.spi;

/**
 * 钥材料端口：登记签名档案时读取签名服务侧的完整公钥结果（宿主接 KMS/其他签名服务的适配）。
 *
 * <p>返回完整结果而非裸公钥字节——回显的 keyRef/版本、算法标识、版本状态与逻辑钥状态
 * 都是登记期严格校验的输入；只回 hex 会退化为长度检查。实现读取失败按签名服务不可用抛出
 * 运行时异常（登记侧统一转 503，不区分网络/认证细节）。</p>
 *
 * @author surezzzzzz
 */
public interface KeyMaterialPort {

    /**
     * 读取指定逻辑钥精确版本的可发布公钥完整结果。
     *
     * @param keyRef 逻辑钥引用（登记请求原样传入，不缺省）
     * @param keyVersion 精确版本（登记请求原样传入，不缺省）
     */
    KeyMaterial readKeyMaterial(String keyRef, int keyVersion);

    /** 签名服务公钥结果的完整投影（协议无关，不含任何服务内部 DTO）。 */
    final class KeyMaterial {

        private final String keyRef;
        private final int version;
        private final String algorithm;
        private final String versionState;
        private final String keyState;
        private final byte[] spki;

        public KeyMaterial(String keyRef, int version, String algorithm, String versionState,
                           String keyState, byte[] spki) {
            this.keyRef = keyRef;
            this.version = version;
            this.algorithm = algorithm;
            this.versionState = versionState;
            this.keyState = keyState;
            this.spki = spki == null ? new byte[0] : spki.clone();
        }

        public String getKeyRef() {
            return keyRef;
        }

        public int getVersion() {
            return version;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getVersionState() {
            return versionState;
        }

        public String getKeyState() {
            return keyState;
        }

        /** SPKI 公钥字节副本（防御性复制）。 */
        public byte[] getSpki() {
            return spki.clone();
        }
    }
}
