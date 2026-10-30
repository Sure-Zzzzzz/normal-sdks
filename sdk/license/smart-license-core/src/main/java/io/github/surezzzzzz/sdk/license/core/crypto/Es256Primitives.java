package io.github.surezzzzzz.sdk.license.core.crypto;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * ES256/P-256 密码学原语：JOSE R||S 与 JCA DER 转换、SPKI 公钥解析与序列化、原文验签。
 *
 * <p>签名段恰 64 字节；验签输入为 Compact JWS 前两段与点号的原始 ASCII 字节，不重新序列化。
 * 生产签发经 Server 的 LicenseSigner 适配 KMS；本类验签侧为唯一实现，测试签出仅供非生产固定向量。</p>
 *
 * @author surezzzzzz
 */
public final class Es256Primitives {

    private static final String EC = "EC";
    private static final String SHA256_WITH_ECDSA = "SHA256withECDSA";
    private static final int COORD_BYTES = 32;

    /** P-256 OID 的 SPKI 固定前缀（未压缩点前缀 0x04||X||Y 共 65 字节拼接其后即合法 SPKI）。 */
    private static final byte[] P256_SPKI_PREFIX = {
            0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01,
            0x06, 0x08, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00
    };

    private Es256Primitives() {
    }

    /** 校验签名段形态恰为 64 字节 JOSE R||S；违规确定拒绝。 */
    public static void requireJoseSignature(byte[] signature) {
        if (signature == null || signature.length != 64) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
        }
    }

    /** JOSE R||S（64 字节）转 JCA ASN.1 DER SEQ{INT r,INT s}；P-256 值域下 DER 长度可用单字节编码。 */
    public static byte[] joseToDer(byte[] joseSignature) {
        requireJoseSignature(joseSignature);
        byte[] r = derInteger(Arrays.copyOfRange(joseSignature, 0, COORD_BYTES));
        byte[] s = derInteger(Arrays.copyOfRange(joseSignature, COORD_BYTES, 64));
        byte[] output = new byte[2 + r.length + s.length];
        output[0] = 0x30;
        output[1] = (byte) (r.length + s.length);
        System.arraycopy(r, 0, output, 2, r.length);
        System.arraycopy(s, 0, output, 2 + r.length, s.length);
        return output;
    }

    /** JCA ASN.1 DER 转 JOSE R||S；结构或长度非法确定拒绝。 */
    public static byte[] derToJose(byte[] derSignature) {
        try {
            if (derSignature == null || derSignature.length < 8 || derSignature.length > 72
                    || derSignature[0] != 0x30 || (derSignature[1] & 0xFF) != derSignature.length - 2) {
                throw new IllegalArgumentException("bad sequence");
            }
            int cursor = 2;
            BigInteger r = readInteger(derSignature, cursor);
            cursor += (derSignature[cursor + 1] & 0xFF) + 2;
            BigInteger s = readInteger(derSignature, cursor);
            cursor += (derSignature[cursor + 1] & 0xFF) + 2;
            if (cursor != derSignature.length) {
                throw new IllegalArgumentException("签名含尾随字节");
            }
            byte[] jose = new byte[64];
            leftPad(r.toByteArray(), jose, 0);
            leftPad(s.toByteArray(), jose, COORD_BYTES);
            return jose;
        } catch (Exception failure) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
        }
    }

    /** 解析 X.509 SPKI 公钥（P-256）。 */
    public static PublicKey parseSpkiPublicKey(byte[] spki) {
        try {
            PublicKey key = KeyFactory.getInstance(EC).generatePublic(new X509EncodedKeySpec(spki));
            requireP256(key);
            return key;
        } catch (LicenseCoreException boundary) {
            throw boundary;
        } catch (Exception failure) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
        }
    }

    /** 未压缩点（0x04||X||Y，65 字节）转 P-256 公钥（SPKI 前缀包装）。 */
    public static PublicKey parseUncompressedPoint(byte[] point) {
        if (point == null || point.length != 65 || point[0] != 4) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
        }
        byte[] spki = new byte[P256_SPKI_PREFIX.length + 65];
        System.arraycopy(P256_SPKI_PREFIX, 0, spki, 0, P256_SPKI_PREFIX.length);
        System.arraycopy(point, 0, spki, P256_SPKI_PREFIX.length, 65);
        return parseSpkiPublicKey(spki);
    }

    /** 公钥序列化为 SPKI 字节（用于信任资料指纹与本地比对）。 */
    public static byte[] spkiBytes(ECPublicKey publicKey) {
        return publicKey.getEncoded();
    }

    /**
     * 用公钥对原文做 ES256 验签。
     *
     * @param signingInput Compact JWS 前两段与点号的原始字节
     * @param joseSignature 64 字节 JOSE 签名段
     */
    public static boolean verify(byte[] signingInput, byte[] joseSignature, PublicKey publicKey) {
        requireJoseSignature(joseSignature);
        try {
            Signature verifier = Signature.getInstance(SHA256_WITH_ECDSA);
            verifier.initVerify(publicKey);
            verifier.update(signingInput);
            return verifier.verify(joseToDer(joseSignature));
        } catch (Exception failure) {
            // 验签执行失败统一为确定拒绝；异常细节不出边界
            throw new LicenseCoreException(ErrorCode.CRYPTO_SIGNATURE, ErrorMessage.CRYPTO_SIGNATURE_INVALID);
        }
    }

    /** 非生产测试签名：JCA 输出 DER 转 JOSE，供固定向量生成使用。 */
    public static byte[] signForTesting(byte[] signingInput, java.security.PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance(SHA256_WITH_ECDSA);
            signer.initSign(privateKey);
            signer.update(signingInput);
            return derToJose(signer.sign());
        } catch (Exception failure) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_SIGNATURE, ErrorMessage.CRYPTO_SIGNATURE_INVALID);
        }
    }

    private static void requireP256(PublicKey key) {
        // 仅认 secp256r1（P-256/NIST prime256v1）：域参数比对，不认任意 256 位曲线（如 secp256k1）
        if (!(key instanceof ECPublicKey)) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
        }
        java.security.spec.ECParameterSpec spec = ((ECPublicKey) key).getParams();
        try {
            java.security.AlgorithmParameters parameters = java.security.AlgorithmParameters.getInstance("EC");
            parameters.init(spec);
            String name = parameters.toString();
            if (!name.contains("secp256r1") && !name.contains("prime256v1") && !name.contains("P-256")) {
                throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
            }
        } catch (java.security.GeneralSecurityException invalid) {
            throw new LicenseCoreException(ErrorCode.CRYPTO_ENCODING, ErrorMessage.CRYPTO_ENCODING_INVALID);
        }
    }

    private static byte[] derInteger(byte[] magnitude) {
        // DER 整数必须使用最短的有符号编码；JOSE 固定宽度的零前缀不能原样进入 DER。
        int first = 0;
        while (first < magnitude.length - 1 && magnitude[first] == 0) {
            first++;
        }
        int length = magnitude.length - first;
        int pad = (magnitude[first] & 0x80) != 0 ? 1 : 0;
        byte[] output = new byte[length + pad + 2];
        output[0] = 0x02;
        output[1] = (byte) (length + pad);
        System.arraycopy(magnitude, first, output, 2 + pad, length);
        return output;
    }

    private static BigInteger readInteger(byte[] der, int offset) {
        if (offset < 2 || offset + 2 > der.length || der[offset] != 0x02) {
            throw new IllegalArgumentException("bad integer tag");
        }
        int length = der[offset + 1] & 0xFF;
        if (length < 1 || length > 33 || offset + 2 + length > der.length
                || (der[offset + 2] & 0x80) != 0
                || length > 1 && der[offset + 2] == 0 && (der[offset + 3] & 0x80) == 0) {
            throw new IllegalArgumentException("签名整数编码无效");
        }
        int magnitudeLength = length - (der[offset + 2] == 0 ? 1 : 0);
        if (magnitudeLength > COORD_BYTES) {
            throw new IllegalArgumentException("签名整数超出坐标宽度");
        }
        return new BigInteger(1, Arrays.copyOfRange(der, offset + 2, offset + 2 + length));
    }

    private static void leftPad(byte[] magnitude, byte[] target, int offset) {
        int copy = Math.min(magnitude.length, COORD_BYTES);
        System.arraycopy(magnitude, magnitude.length - copy, target, offset + COORD_BYTES - copy, copy);
    }
}
