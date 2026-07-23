package io.github.surezzzzzz.sdk.kms.core.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsEnvelope;
import io.github.surezzzzzz.sdk.kms.core.support.KmsEnvelopeHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsEs256SignatureHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * KMS 封装与签名格式测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsEnvelopeAndSignatureTest {

    private static final String KEY_REF = "key-ref-a";
    private static final int VERSION = SmartKmsCoreConstant.ONE;
    private static final String P_256_ORDER =
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551";

    @Test
    void shouldReturnDefensiveSkmsMagicCopy() {
        log.info("校验 SKMS 魔数副本不可篡改");
        byte[] first = SmartKmsCoreConstant.getSkmsMagic();
        first[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;

        Assertions.assertArrayEquals(new byte[]{'S', 'K', 'M', 'S'},
                SmartKmsCoreConstant.getSkmsMagic());
    }

    @Test
    void shouldSerializeSkmsInExactByteOrderAndBuildExactAad() {
        log.info("校验 SKMS 字节顺序和 AAD 绑定内容");
        byte[] iv = sequence(SmartKmsCoreConstant.GCM_IV_LENGTH,
                SmartKmsCoreConstant.ONE);
        byte[] ciphertextAndTag = sequence(SmartKmsCoreConstant.GCM_TAG_LENGTH
                + SmartKmsCoreConstant.ONE, SmartKmsCoreConstant.GCM_IV_LENGTH);
        byte[] externalAad = new byte[]{SmartKmsCoreConstant.ONE,
                SmartKmsCoreConstant.ASN1_INTEGER_TAG};
        KmsEnvelope envelope = new KmsEnvelope(KEY_REF, VERSION, iv, ciphertextAndTag);

        byte[] serialized = KmsEnvelopeHelper.serialize(envelope);
        byte[] expectedHeader = concatenate(SmartKmsCoreConstant.getSkmsMagic(),
                new byte[]{(byte) SmartKmsCoreConstant.SKMS_FORMAT_VERSION,
                        (byte) SmartKmsCoreConstant.SKMS_AES_256_GCM_ALGORITHM_CODE,
                        SmartKmsCoreConstant.ZERO, (byte) KEY_REF.length()},
                KEY_REF.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new byte[]{SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                        SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ONE}, iv);
        byte[] expectedSerialized = concatenate(expectedHeader, ciphertextAndTag);
        byte[] expectedAad = concatenate(expectedHeader,
                new byte[]{SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                        SmartKmsCoreConstant.ZERO, (byte) externalAad.length}, externalAad);

        Assertions.assertArrayEquals(expectedSerialized, serialized);
        Assertions.assertArrayEquals(expectedAad,
                KmsEnvelopeHelper.buildAad(serialized, externalAad));
        Assertions.assertArrayEquals(concatenate(expectedHeader,
                        new byte[]{SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO}),
                KmsEnvelopeHelper.buildAad(serialized, null));
    }

    @Test
    void shouldRejectAllSkmsStructuralFailuresAsCryptoFailure() {
        log.info("校验 SKMS 格式失败的安全归并");
        KmsEnvelope validEnvelope = new KmsEnvelope(KEY_REF, VERSION,
                new byte[SmartKmsCoreConstant.GCM_IV_LENGTH],
                new byte[SmartKmsCoreConstant.GCM_TAG_LENGTH]);
        byte[] valid = KmsEnvelopeHelper.serialize(validEnvelope);

        byte[] wrongMagic = Arrays.copyOf(valid, valid.length);
        wrongMagic[SmartKmsCoreConstant.ZERO] = SmartKmsCoreConstant.ZERO;
        byte[] wrongVersion = Arrays.copyOf(valid, valid.length);
        wrongVersion[SmartKmsCoreConstant.getSkmsMagic().length] = SmartKmsCoreConstant.ZERO;
        byte[] emptyKeyRef = Arrays.copyOf(valid, valid.length);
        emptyKeyRef[SmartKmsCoreConstant.getSkmsMagic().length + SmartKmsCoreConstant.ONE
                + SmartKmsCoreConstant.ONE] = SmartKmsCoreConstant.ZERO;
        emptyKeyRef[SmartKmsCoreConstant.getSkmsMagic().length + SmartKmsCoreConstant.ONE
                + SmartKmsCoreConstant.ONE + SmartKmsCoreConstant.ONE] = SmartKmsCoreConstant.ZERO;
        byte[] malformedUtf8 = new byte[]{'S', 'K', 'M', 'S', SmartKmsCoreConstant.ONE,
                SmartKmsCoreConstant.ONE, SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ONE,
                (byte) SmartKmsCoreConstant.ASN1_LONG_FORM_LENGTH_MASK,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ONE,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO,
                SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.ZERO};

        assertCryptoFailure(() -> KmsEnvelopeHelper.parse(null));
        assertCryptoFailure(() -> KmsEnvelopeHelper.parse(wrongMagic));
        assertCryptoFailure(() -> KmsEnvelopeHelper.parse(wrongVersion));
        assertCryptoFailure(() -> KmsEnvelopeHelper.parse(emptyKeyRef));
        assertCryptoFailure(() -> KmsEnvelopeHelper.parse(malformedUtf8));
        assertCryptoFailure(() -> KmsEnvelopeHelper.serialize(new KmsEnvelope(KEY_REF, VERSION,
                null, new byte[SmartKmsCoreConstant.GCM_TAG_LENGTH])));
    }

    @Test
    void shouldNormalizeHighSAndRejectNonCanonicalDerAndInvalidJose() {
        log.info("校验 ES256 DER 和 JOSE 的严格转换");
        BigInteger r = BigInteger.valueOf(SmartKmsCoreConstant.ONE);
        BigInteger highS = new BigInteger(P_256_ORDER, SmartKmsCoreConstant.HEX_RADIX)
                .subtract(BigInteger.valueOf(SmartKmsCoreConstant.ONE));
        byte[] jose = KmsEs256SignatureHelper.derToJose(derSignature(r, highS));
        byte[] expectedLowS = coordinateBytes(BigInteger.valueOf(SmartKmsCoreConstant.ONE));

        Assertions.assertArrayEquals(expectedLowS, Arrays.copyOfRange(jose,
                SmartKmsCoreConstant.ES256_COORDINATE_LENGTH,
                SmartKmsCoreConstant.ES256_JOSE_SIGNATURE_LENGTH));
        Assertions.assertArrayEquals(jose, KmsEs256SignatureHelper.derToJose(
                KmsEs256SignatureHelper.joseToDer(jose)));

        byte[] invalidJose = new byte[SmartKmsCoreConstant.ES256_JOSE_SIGNATURE_LENGTH];
        assertCryptoFailure(() -> KmsEs256SignatureHelper.joseToDer(invalidJose));
        assertCryptoFailure(() -> KmsEs256SignatureHelper.joseToDer(Arrays.copyOf(invalidJose,
                SmartKmsCoreConstant.ES256_JOSE_SIGNATURE_LENGTH - SmartKmsCoreConstant.ONE)));
        assertCryptoFailure(() -> KmsEs256SignatureHelper.derToJose(new byte[]{
                (byte) SmartKmsCoreConstant.ASN1_SEQUENCE_TAG,
                (byte) SmartKmsCoreConstant.ASN1_LONG_FORM_LENGTH_MASK}));
        assertCryptoFailure(() -> KmsEs256SignatureHelper.derToJose(new byte[]{
                (byte) SmartKmsCoreConstant.ASN1_SEQUENCE_TAG,
                SmartKmsCoreConstant.ZERO}));
    }

    private void assertCryptoFailure(ThrowingOperation operation) {
        Assertions.assertThrows(KmsCryptoException.class, operation::execute);
    }

    private byte[] sequence(int length, int start) {
        byte[] value = new byte[length];
        for (int index = SmartKmsCoreConstant.ZERO; index < length; index++) {
            value[index] = (byte) (start + index);
        }
        return value;
    }

    private byte[] coordinateBytes(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] result = new byte[SmartKmsCoreConstant.ES256_COORDINATE_LENGTH];
        System.arraycopy(raw, SmartKmsCoreConstant.ZERO, result,
                result.length - raw.length, raw.length);
        return result;
    }

    private byte[] derSignature(BigInteger r, BigInteger s) {
        byte[] encodedR = r.toByteArray();
        byte[] encodedS = s.toByteArray();
        int contentLength = SmartKmsCoreConstant.ASN1_INTEGER_TAG + encodedR.length
                + SmartKmsCoreConstant.ASN1_INTEGER_TAG + encodedS.length;
        return concatenate(new byte[]{(byte) SmartKmsCoreConstant.ASN1_SEQUENCE_TAG,
                        (byte) contentLength, (byte) SmartKmsCoreConstant.ASN1_INTEGER_TAG,
                        (byte) encodedR.length},
                encodedR,
                new byte[]{(byte) SmartKmsCoreConstant.ASN1_INTEGER_TAG, (byte) encodedS.length},
                encodedS);
    }

    private byte[] concatenate(byte[]... values) {
        int length = SmartKmsCoreConstant.ZERO;
        for (byte[] value : values) {
            length += value.length;
        }
        byte[] result = new byte[length];
        int offset = SmartKmsCoreConstant.ZERO;
        for (byte[] value : values) {
            System.arraycopy(value, SmartKmsCoreConstant.ZERO, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private interface ThrowingOperation {
        void execute();
    }
}
