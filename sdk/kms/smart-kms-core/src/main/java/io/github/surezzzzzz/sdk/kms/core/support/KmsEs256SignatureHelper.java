package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * ES256 DER 与 JOSE 格式转换工具。
 *
 * <p>JCA {@code SHA256withECDSA} 输出 ASN.1 DER，而 JWS ES256 使用固定 64 字节
 * {@code R || S}。本类只负责严格转换和 low-S 规范化，不创建密钥、不选择 Provider、
 * 不调用 JCA 密码学 API。</p>
 *
 * @author surezzzzzz
 */
public final class KmsEs256SignatureHelper {

    /**
     * secp256r1 曲线的子群阶。
     */
    private static final BigInteger P_256_ORDER = new BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
            SmartKmsCoreConstant.HEX_RADIX);

    private KmsEs256SignatureHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 将规范 ASN.1 DER ECDSA 签名转换为 low-S JOSE ES256 签名。
     *
     * @param der JCA 输出的 DER ECDSA 签名
     * @return 固定 64 字节的 low-S {@code R || S}
     * @throws KmsCryptoException DER 非规范、坐标不在 P-256 有效范围时抛出
     */
    public static byte[] derToJose(byte[] der) {
        if (der == null || der.length < SmartKmsCoreConstant.ES256_MIN_DER_SIGNATURE_LENGTH
                || der[SmartKmsCoreConstant.ZERO] != SmartKmsCoreConstant.ASN1_SEQUENCE_TAG) {
            throw new KmsCryptoException();
        }
        int[] offset = new int[]{SmartKmsCoreConstant.ONE};
        int sequenceLength = readLength(der, offset);
        if (sequenceLength != der.length - offset[SmartKmsCoreConstant.ZERO]) {
            throw new KmsCryptoException();
        }
        BigInteger r = readPositiveInteger(der, offset);
        BigInteger s = readPositiveInteger(der, offset);
        if (offset[SmartKmsCoreConstant.ZERO] != der.length) {
            throw new KmsCryptoException();
        }
        validateCoordinate(r);
        validateCoordinate(s);
        byte[] jose = new byte[SmartKmsCoreConstant.ES256_JOSE_SIGNATURE_LENGTH];
        writeCoordinate(r, jose, SmartKmsCoreConstant.ZERO);
        writeCoordinate(s.min(P_256_ORDER.subtract(s)), jose,
                SmartKmsCoreConstant.ES256_COORDINATE_LENGTH);
        return jose;
    }

    /**
     * 将 JOSE ES256 签名转换为规范 ASN.1 DER ECDSA 签名。
     *
     * <p>验签可接受高 S 和 low S，只要 {@code R}/{@code S} 均在合法坐标范围内。</p>
     *
     * @param jose 固定 64 字节 {@code R || S}
     * @return 规范 DER ECDSA 签名
     * @throws KmsCryptoException 长度或坐标非法时抛出
     */
    public static byte[] joseToDer(byte[] jose) {
        if (jose == null || jose.length != SmartKmsCoreConstant.ES256_JOSE_SIGNATURE_LENGTH) {
            throw new KmsCryptoException();
        }
        BigInteger r = new BigInteger(SmartKmsCoreConstant.ONE,
                Arrays.copyOfRange(jose, SmartKmsCoreConstant.ZERO,
                        SmartKmsCoreConstant.ES256_COORDINATE_LENGTH));
        BigInteger s = new BigInteger(SmartKmsCoreConstant.ONE,
                Arrays.copyOfRange(jose, SmartKmsCoreConstant.ES256_COORDINATE_LENGTH, jose.length));
        validateCoordinate(r);
        validateCoordinate(s);
        byte[] encodedR = encodeInteger(r);
        byte[] encodedS = encodeInteger(s);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(SmartKmsCoreConstant.ASN1_SEQUENCE_TAG);
        writeLength(output, encodedR.length + encodedS.length);
        output.write(encodedR, SmartKmsCoreConstant.ZERO, encodedR.length);
        output.write(encodedS, SmartKmsCoreConstant.ZERO, encodedS.length);
        return output.toByteArray();
    }

    /**
     * 读取并校验 DER 正整数，拒绝负数和冗余的前导零。
     */
    private static BigInteger readPositiveInteger(byte[] data, int[] offset) {
        if (offset[SmartKmsCoreConstant.ZERO] >= data.length
                || data[offset[SmartKmsCoreConstant.ZERO]++]
                != SmartKmsCoreConstant.ASN1_INTEGER_TAG) {
            throw new KmsCryptoException();
        }
        int length = readLength(data, offset);
        if (length == SmartKmsCoreConstant.ZERO
                || length > SmartKmsCoreConstant.ES256_COORDINATE_MAX_DER_LENGTH
                || offset[SmartKmsCoreConstant.ZERO] + length > data.length) {
            throw new KmsCryptoException();
        }
        byte[] value = Arrays.copyOfRange(data, offset[SmartKmsCoreConstant.ZERO],
                offset[SmartKmsCoreConstant.ZERO] + length);
        offset[SmartKmsCoreConstant.ZERO] += length;
        if ((value[SmartKmsCoreConstant.ZERO] & SmartKmsCoreConstant.ASN1_LONG_FORM_LENGTH_MASK) != 0
                || (value.length > SmartKmsCoreConstant.ONE
                && value[SmartKmsCoreConstant.ZERO] == SmartKmsCoreConstant.ZERO
                && (value[SmartKmsCoreConstant.ONE]
                & SmartKmsCoreConstant.ASN1_LONG_FORM_LENGTH_MASK) == SmartKmsCoreConstant.ZERO)) {
            throw new KmsCryptoException();
        }
        return new BigInteger(SmartKmsCoreConstant.ONE, value);
    }

    /**
     * 读取短格式 DER 长度；ES256 签名不允许长格式长度编码。
     */
    private static int readLength(byte[] data, int[] offset) {
        if (offset[SmartKmsCoreConstant.ZERO] >= data.length) {
            throw new KmsCryptoException();
        }
        int length = data[offset[SmartKmsCoreConstant.ZERO]++]
                & SmartKmsCoreConstant.UNSIGNED_BYTE_MASK;
        if ((length & SmartKmsCoreConstant.ASN1_LONG_FORM_LENGTH_MASK) != SmartKmsCoreConstant.ZERO) {
            throw new KmsCryptoException();
        }
        return length;
    }

    /**
     * 坐标必须满足 {@code 0 < value < n}。
     */
    private static void validateCoordinate(BigInteger value) {
        if (value.signum() <= SmartKmsCoreConstant.ZERO || value.compareTo(P_256_ORDER) >= 0) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 将无符号坐标左侧补零写入固定 32 字节区段。
     */
    private static void writeCoordinate(BigInteger value, byte[] output, int offset) {
        byte[] encoded = value.toByteArray();
        int sourceOffset = encoded.length > SmartKmsCoreConstant.ES256_COORDINATE_LENGTH
                ? SmartKmsCoreConstant.ONE : SmartKmsCoreConstant.ZERO;
        int length = encoded.length - sourceOffset;
        if (length > SmartKmsCoreConstant.ES256_COORDINATE_LENGTH) {
            throw new KmsCryptoException();
        }
        System.arraycopy(encoded, sourceOffset, output,
                offset + SmartKmsCoreConstant.ES256_COORDINATE_LENGTH - length, length);
    }

    /**
     * 将正整数编码为规范 DER INTEGER。
     */
    private static byte[] encodeInteger(BigInteger value) {
        byte[] raw = value.toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(SmartKmsCoreConstant.ASN1_INTEGER_TAG);
        writeLength(output, raw.length);
        output.write(raw, SmartKmsCoreConstant.ZERO, raw.length);
        return output.toByteArray();
    }

    /**
     * 写入 ES256 范围内唯一允许的短格式 DER 长度。
     */
    private static void writeLength(ByteArrayOutputStream output, int length) {
        if (length >= SmartKmsCoreConstant.ASN1_LONG_FORM_LENGTH_MASK) {
            throw new KmsCryptoException();
        }
        output.write(length);
    }
}
