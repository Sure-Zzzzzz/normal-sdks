package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 签名域类型断言：原文不可变、信任资料防御复制与非法拒绝。
 *
 * @author surezzzzzz
 */
class SigningTypesTest {

    @Test
    void signingInputTakesExactlyFirstTwoSegmentsWithDot() {
        SigningInput input = SigningInput.ofCompactJws("aGViYQ.cGF5bG9hZA.c2ln");
        assertThat(new String(input.getBytes(), StandardCharsets.US_ASCII)).isEqualTo("aGViYQ.cGF5bG9hZA");
        assertThat(input.length()).isEqualTo("aGViYQ.cGF5bG9hZA".length());
    }

    @Test
    void signingInputRejectsNonThreeSegmentForms() {
        assertThatThrownBy(() -> SigningInput.ofCompactJws("only"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SigningInput.ofCompactJws("a."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SigningInput.ofCompactJws("a.b."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SigningInput.ofBytes(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signingInputDefensivelyCopiesBytes() {
        byte[] raw = "header.payload".getBytes(StandardCharsets.US_ASCII);
        SigningInput input = SigningInput.ofBytes(raw);
        raw[0] = 'X';
        assertThat(input.getBytes()[0]).isEqualTo((byte) 'h');
        byte[] taken = input.getBytes();
        taken[0] = 'Y';
        assertThat(input.getBytes()[0]).isEqualTo((byte) 'h');
    }

    @Test
    void trustedPublicKeyRejectsBlanksAndClonesSpki() {
        assertThatThrownBy(() -> TrustedPublicKey.of("", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 1, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 0, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedPublicKey.of("i", "p", "k",
                TrustedPublicKey.Purpose.CREDENTIAL, 1, null))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] spki = new byte[]{1, 2, 3};
        TrustedPublicKey key = TrustedPublicKey.of("issuer", "product", "kid",
                TrustedPublicKey.Purpose.REVOCATION, 5, spki);
        spki[0] = 9;
        assertThat(key.getSpki()[0]).isEqualTo((byte) 1);
        key.getSpki()[0] = 7;
        assertThat(key.getSpki()[0]).isEqualTo((byte) 1);
        assertThat(key.getPurpose()).isEqualTo(TrustedPublicKey.Purpose.REVOCATION);
    }

    @Test
    void trustedPublicKeyRejectsControlCharactersAndOversizedIdentifiers() {
        String oversized = String.join("", java.util.Collections.nCopies(43, "界"));
        for (String invalid : new String[]{"key\u0000alias", "key\n", oversized}) {
            assertThatThrownBy(() -> TrustedPublicKey.of(invalid, "product", "kid",
                    TrustedPublicKey.Purpose.REVOCATION, 1, new byte[]{1}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrustedPublicKey.of("issuer", invalid, "kid",
                    TrustedPublicKey.Purpose.REVOCATION, 1, new byte[]{1}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrustedPublicKey.of("issuer", "product", invalid,
                    TrustedPublicKey.Purpose.REVOCATION, 1, new byte[]{1}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
