package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsEnvelope;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Arrays;

/**
 * SKMS v1 封装工具。
 *
 * <p>本类只处理固定二进制格式和 AAD 拼接，不执行 AES-GCM、不生成 IV，也不处理 HTTP 的
 * Base64url 编码。所有格式问题统一归并为密码学安全失败，避免泄露版本、标签或编码细节。</p>
 *
 * @author surezzzzzz
 */
public final class KmsEnvelopeHelper {

    /**
     * SKMS 规定的 keyRef 字符编码。
     */
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    private KmsEnvelopeHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 按 SKMS v1 的固定字段顺序序列化密文封装。
     *
     * @param envelope 待序列化的封装
     * @return SKMS v1 二进制封装
     * @throws KmsCryptoException 封装字段不满足格式约束时抛出
     */
    public static byte[] serialize(KmsEnvelope envelope) {
        try {
            if (envelope == null) {
                throw new KmsCryptoException();
            }
            byte[] keyRef = KmsValidationHelper.requireKeyRef(envelope.getKeyRef()).getBytes(UTF_8);
            byte[] iv = envelope.getIv();
            byte[] ciphertextAndTag = envelope.getCiphertextAndTag();
            if (iv == null || ciphertextAndTag == null
                    || keyRef.length > SmartKmsCoreConstant.UNSIGNED_SHORT_MAX_VALUE
                    || envelope.getKeyVersion() <= SmartKmsCoreConstant.ZERO
                    || envelope.getKeyVersion() > SmartKmsCoreConstant.UNSIGNED_INT_MAX_VALUE
                    || iv.length != SmartKmsCoreConstant.GCM_IV_LENGTH
                    || ciphertextAndTag.length < SmartKmsCoreConstant.GCM_TAG_LENGTH) {
                throw new KmsCryptoException();
            }
            long totalLength = (long) SmartKmsCoreConstant.SKMS_FIXED_HEADER_LENGTH
                    + keyRef.length + ciphertextAndTag.length;
            if (totalLength > Integer.MAX_VALUE) {
                throw new KmsCryptoException();
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) totalLength);
            buffer.put(SmartKmsCoreConstant.getSkmsMagic())
                    .put((byte) SmartKmsCoreConstant.SKMS_FORMAT_VERSION)
                    .put((byte) SmartKmsCoreConstant.SKMS_AES_256_GCM_ALGORITHM_CODE)
                    .putShort((short) keyRef.length)
                    .put(keyRef)
                    .putInt((int) envelope.getKeyVersion())
                    .put(iv)
                    .put(ciphertextAndTag);
            return buffer.array();
        } catch (RuntimeException exception) {
            if (exception instanceof KmsCryptoException) {
                throw exception;
            }
            throw new KmsCryptoException();
        }
    }

    /**
     * 解析并严格校验 SKMS v1 二进制封装。
     *
     * <p>keyRef 使用报告模式 UTF-8 解码，禁止替换非法字节；版本号和字段长度按无符号大端解释。</p>
     *
     * @param bytes SKMS v1 二进制封装
     * @return 已解析的封装模型
     * @throws KmsCryptoException 格式、编码或长度非法时抛出
     */
    public static KmsEnvelope parse(byte[] bytes) {
        try {
            if (bytes == null || bytes.length < SmartKmsCoreConstant.SKMS_FIXED_HEADER_LENGTH
                    + SmartKmsCoreConstant.GCM_TAG_LENGTH) {
                throw new KmsCryptoException();
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] magic = new byte[SmartKmsCoreConstant.getSkmsMagic().length];
            buffer.get(magic);
            if (!Arrays.equals(magic, SmartKmsCoreConstant.getSkmsMagic())
                    || (buffer.get() & SmartKmsCoreConstant.UNSIGNED_BYTE_MASK)
                    != SmartKmsCoreConstant.SKMS_FORMAT_VERSION
                    || (buffer.get() & SmartKmsCoreConstant.UNSIGNED_BYTE_MASK)
                    != SmartKmsCoreConstant.SKMS_AES_256_GCM_ALGORITHM_CODE) {
                throw new KmsCryptoException();
            }
            int keyRefLength = buffer.getShort() & SmartKmsCoreConstant.UNSIGNED_SHORT_MAX_VALUE;
            if (keyRefLength == SmartKmsCoreConstant.ZERO || buffer.remaining() < keyRefLength
                    + SmartKmsCoreConstant.SKMS_KEY_VERSION_FIELD_LENGTH
                    + SmartKmsCoreConstant.GCM_IV_LENGTH
                    + SmartKmsCoreConstant.GCM_TAG_LENGTH) {
                throw new KmsCryptoException();
            }
            byte[] keyRefBytes = new byte[keyRefLength];
            buffer.get(keyRefBytes);
            String keyRef = KmsValidationHelper.requireKeyRef(decodeKeyRef(keyRefBytes));
            long keyVersion = ((long) buffer.getInt()) & SmartKmsCoreConstant.UNSIGNED_INT_MAX_VALUE;
            if (keyVersion == SmartKmsCoreConstant.ZERO) {
                throw new KmsCryptoException();
            }
            byte[] iv = new byte[SmartKmsCoreConstant.GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertextAndTag = new byte[buffer.remaining()];
            buffer.get(ciphertextAndTag);
            if (ciphertextAndTag.length < SmartKmsCoreConstant.GCM_TAG_LENGTH) {
                throw new KmsCryptoException();
            }
            return new KmsEnvelope(keyRef, keyVersion, iv, ciphertextAndTag);
        } catch (RuntimeException exception) {
            if (exception instanceof KmsCryptoException) {
                throw exception;
            }
            throw new KmsCryptoException();
        }
    }

    /**
     * 构造 AES-GCM 固定 AAD。
     *
     * <p>AAD 必须绑定从 magic 到 IV 的全部封装头，调用方提供的外部 AAD 通过 4 字节长度字段
     * 明确分界；未提供外部 AAD 等价于零长度字节。</p>
     *
     * @param serializedEnvelope 已序列化的 SKMS v1 封装
     * @param externalAad        调用方附加 AAD，可为 {@code null}
     * @return 固定格式的 AES-GCM AAD
     */
    public static byte[] buildAad(byte[] serializedEnvelope, byte[] externalAad) {
        KmsEnvelope envelope = parse(serializedEnvelope);
        int headerLength = serializedEnvelope.length - envelope.getCiphertextAndTag().length;
        byte[] safeExternalAad = externalAad == null
                ? new byte[SmartKmsCoreConstant.ZERO]
                : Arrays.copyOf(externalAad, externalAad.length);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(serializedEnvelope, SmartKmsCoreConstant.ZERO, headerLength);
        output.write(ByteBuffer.allocate(SmartKmsCoreConstant.EXTERNAL_AAD_LENGTH_FIELD_LENGTH)
                        .putInt(safeExternalAad.length).array(), SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.EXTERNAL_AAD_LENGTH_FIELD_LENGTH);
        output.write(safeExternalAad, SmartKmsCoreConstant.ZERO, safeExternalAad.length);
        return output.toByteArray();
    }

    /**
     * 使用严格 UTF-8 解码 keyRef，拒绝替换非法字节。
     */
    private static String decodeKeyRef(byte[] keyRefBytes) {
        CharsetDecoder decoder = UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(keyRefBytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new KmsCryptoException();
        }
    }
}
