package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.codec.ProtocolHeader;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 协议 2 编解码基础设施断言：严格 JSON、Base64URL、三段解析、header 字段约束。
 *
 * @author surezzzzzz
 */
class Protocol2CodecTest {

    @Test
    void strictJsonRejectsUnknownFieldsAndDuplicates() {
        String withUnknown = "{\"a\":1,\"unknown\":2}";
        assertThatThrownBy(() -> StrictJson.parse(withUnknown, SampleDto.class))
                .isInstanceOf(IllegalArgumentException.class);
        String withDuplicate = "{\"a\":1,\"a\":2}";
        assertThatThrownBy(() -> StrictJson.parse(withDuplicate, SampleDto.class))
                .isInstanceOf(IllegalArgumentException.class);
        String valid = "{\"a\":1}";
        assertThat(StrictJson.parse(valid, SampleDto.class).a).isEqualTo(1);
    }

    @Test
    void base64UrlRejectsPaddingAndStandardAlphabet() {
        assertThat(Base64Url.encode("hi".getBytes(StandardCharsets.UTF_8))).isEqualTo("aGk");
        assertThat(Base64Url.decode("aGk")).isEqualTo("hi".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> Base64Url.decode("aGk="))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Base64Url.decode("a+k"))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Base64Url.decode("a/k"))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void compactJwsRequiresExactlyThreeNonEmptyStrictSegments() {
        CompactJws jws = CompactJws.parse("aGVhZGVy.cGF5bG9hZA.c2ln");
        assertThat(jws.getSignatureSegment()).isEqualTo("c2ln");
        byte[] signingInput = jws.signingInput();
        assertThat(new String(signingInput, StandardCharsets.US_ASCII)).isEqualTo("aGVhZGVy.cGF5bG9hZA");

        assertThatThrownBy(() -> CompactJws.parse("aGVhZGVy"))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> CompactJws.parse("aGVhZGVy..c2ln"))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> CompactJws.parse("aGVh ZGVy.cGF5bG9hZA.c2ln"))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> CompactJws.parse("aGVhZGVy.cGF5bG9hZA."))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void headerRejectsWrongAlgorithmAndUnknownTyp() {
        new ProtocolHeader("ES256", "kid-1", ProtocolHeader.TYP_CREDENTIAL);
        assertThatThrownBy(() -> new ProtocolHeader("HS256", "kid-1", ProtocolHeader.TYP_CREDENTIAL))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> new ProtocolHeader("ES256", "", ProtocolHeader.TYP_CREDENTIAL))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> new ProtocolHeader("ES256", "kid-1", "JWT"))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> new ProtocolHeader("ES256", "kid-1", "application/jwt"))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void headerJsonRoundTripAndUnknownFieldRejected() {
        String json = "{\"alg\":\"ES256\",\"kid\":\"k1\",\"typ\":\"license+jwt\"}";
        ProtocolHeader header = StrictJson.parse(json, ProtocolHeader.class);
        assertThat(header.getKid()).isEqualTo("k1");
        assertThat(header.getTyp()).isEqualTo(ProtocolHeader.TYP_CREDENTIAL);

        String withExtra = "{\"alg\":\"ES256\",\"kid\":\"k1\",\"typ\":\"license+jwt\",\"jku\":\"http://evil\"}";
        assertThatThrownBy(() -> StrictJson.parse(withExtra, ProtocolHeader.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static class SampleDto {
        public int a;
    }
}
