package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.server.test.support.InMemoryLicenseSigner;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.Test;

/**
 * 内存签名器断言：签名可用登记公钥本地验签（生产 KMS 适配的同构契约）。
 *
 * @author surezzzzzz
 */
class InMemoryLicenseSignerTest {

    @Test
    void signedBytesVerifyWithRegisteredSpki() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        InMemoryLicenseSigner signer = new InMemoryLicenseSigner();
        signer.register("profile-1", pair, "issuer-1", "product-1", "kid-1");

        SigningInput input = SigningInput.ofBytes(
                "header.payload.".getBytes(StandardCharsets.US_ASCII));
        byte[] signature = signer.sign("profile-1", input);
        assertThat(signature).hasSize(64);

        TrustedPublicKey registered = signer.registeredPublicKey("profile-1");
        assertThat(Es256Primitives.verify(input.getBytes(), signature,
                Es256Primitives.parseSpkiPublicKey(registered.getSpki()))).isTrue();
    }

    @Test
    void unregisteredProfileRejected() {
        InMemoryLicenseSigner signer = new InMemoryLicenseSigner();
        SigningInput input = SigningInput.ofBytes("a.b.".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> signer.sign("missing", input))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signer.registeredPublicKey("missing"))
                .isInstanceOf(IllegalStateException.class);
    }
}
