package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * ES256 原语往返与拒绝向量：JOSE/DER 双向、SPKI/点解析、验签真钥正反例、坏形态拒绝。
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
class Es256PrimitivesTest {

    private static KeyPair p256KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    @Test
    void joseDerRoundTripPreserves64ByteForm() {
        byte[] jose = new byte[64];
        for (int i = 0; i < 64; i++) {
            jose[i] = (byte) ((i * 7 + 3) & 0xFF);
        }
        byte[] der = Es256Primitives.joseToDer(jose);
        byte[] back = Es256Primitives.derToJose(der);
        assertThat(back).isEqualTo(jose);
    }

    @Test
    void derWithHighBitAndShortFormsBothRound() {
        // 高位 r（首位字节高位置 1）与短 s（首字节为 0）都需正确编码
        byte[] jose = new byte[64];
        Arrays.fill(jose, 0, 32, (byte) 0xFF);
        Arrays.fill(jose, 32, 64, (byte) 0x00);
        jose[0] = (byte) 0x80;
        jose[32] = 0x01;
        byte[] back = Es256Primitives.derToJose(Es256Primitives.joseToDer(jose));
        assertThat(back).isEqualTo(jose);
    }

    @Test
    void malformedJoseSignatureRejected() {
        assertThatThrownBy(() -> Es256Primitives.requireJoseSignature(new byte[63]))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Es256Primitives.requireJoseSignature(null))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Es256Primitives.derToJose(new byte[]{0x30, 0x05}))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void leadingZeroCoordinatesUseMinimalDerEncoding() {
        byte[] jose = new byte[64];
        jose[31] = 1;
        jose[63] = (byte) 0x80;
        byte[] expected = {0x30, 0x07, 0x02, 0x01, 0x01, 0x02, 0x02, 0x00, (byte) 0x80};
        log.info("固定向量验证 DER 前导零剥除与正整数符号位补齐");
        assertThat(Es256Primitives.joseToDer(jose)).isEqualTo(expected);
        assertThat(Es256Primitives.derToJose(expected)).isEqualTo(jose);
    }

    @Test
    void malformedDerLengthSignAndTrailingBytesAreRejected() {
        byte[][] malformed = {
                {0x30, 0x07, 0x02, 0x01, 1, 0x02, 0x01, 1},
                {0x30, 0x07, 0x02, 0x02, 0, 1, 0x02, 0x01, 1},
                {0x30, 0x06, 0x02, 0x01, (byte) 0x80, 0x02, 0x01, 1},
                {0x30, 0x07, 0x02, 0x01, 1, 0x02, 0x01, 1, 0}
        };
        for (byte[] der : malformed) {
            assertThatThrownBy(() -> Es256Primitives.derToJose(der)).isInstanceOf(LicenseCoreException.class);
        }
    }

    @Test
    void spkiAndPointParsingRejectForeignShapes() {
        assertThatThrownBy(() -> Es256Primitives.parseUncompressedPoint(new byte[33]))
                .isInstanceOf(LicenseCoreException.class);
        byte[] badPoint = new byte[65];
        badPoint[0] = 0x02;
        assertThatThrownBy(() -> Es256Primitives.parseUncompressedPoint(badPoint))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Es256Primitives.parseSpkiPublicKey("not-a-key".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void verifyAcceptsRealSignatureAndRejectsTampering() throws Exception {
        KeyPair pair = p256KeyPair();
        byte[] input = "header.payload".getBytes(StandardCharsets.US_ASCII);
        byte[] signature = Es256Primitives.signForTesting(input, pair.getPrivate());
        assertThat(signature).hasSize(64);
        assertThat(Es256Primitives.verify(input, signature, pair.getPublic())).isTrue();

        byte[] tampered = signature.clone();
        tampered[10] ^= 0x01;
        assertThat(Es256Primitives.verify(input, tampered, pair.getPublic())).isFalse();

        byte[] otherInput = "header.payload2".getBytes(StandardCharsets.US_ASCII);
        assertThat(Es256Primitives.verify(otherInput, signature, pair.getPublic())).isFalse();
    }

    @Test
    void spkiBytesRoundTripsThroughParser() throws Exception {
        KeyPair pair = p256KeyPair();
        byte[] spki = Es256Primitives.spkiBytes((ECPublicKey) pair.getPublic());
        assertThat(Es256Primitives.parseSpkiPublicKey(spki)).isEqualTo(pair.getPublic());
    }
}
