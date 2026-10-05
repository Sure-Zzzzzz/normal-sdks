package io.github.surezzzzzz.sdk.elasticsearch.search.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.model.CursorState;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import lombok.RequiredArgsConstructor;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.BITS_PER_BYTE;
import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.cursor;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.text;

/**
 * 多实例共享密钥的 AES-GCM token；随机 IV 不等于随机实例密钥。
 */
@RequiredArgsConstructor
public class AesGcmCursorTokenCodec implements CursorTokenCodec {
    private final SimpleElasticsearchSearchProperties properties;
    private final SearchPayloadCodec codec;
    private final SecureRandom random = new SecureRandom();

    /**
     * 校验使用前置条件，失败在发送业务 HTTP 前暴露。
     */
    public void validate() {
        key();
    }

    /**
     * 编码独立快照，失败不携带源数据。
     */
    public String encode(CursorState state) {
        try {
            byte[] iv = new byte[CURSOR_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CURSOR_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(CURSOR_TAG_BITS, iv));
            cipher.updateAAD(CURSOR_AAD.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(codec.encode(state).getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(result);
            if (token.length() > MAX_CURSOR_LENGTH) throw cursor();
            return token;
        } catch (Exception error) {
            throw cursor();
        }
    }

    /**
     * 严格解码协议对象，失败不携带原始正文。
     */
    public CursorState decode(String token, String kind, boolean allowExpired) {
        try {
            if (!text(token) || token.length() > MAX_CURSOR_LENGTH) throw cursor();
            byte[] raw = Base64.getUrlDecoder().decode(token);
            if (raw.length <= CURSOR_IV_BYTES + CURSOR_TAG_BITS / BITS_PER_BYTE) throw cursor();
            Cipher cipher = Cipher.getInstance(CURSOR_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(CURSOR_TAG_BITS, Arrays.copyOf(raw, CURSOR_IV_BYTES)));
            cipher.updateAAD(CURSOR_AAD.getBytes(StandardCharsets.UTF_8));
            String decoded = new String(cipher.doFinal(raw, CURSOR_IV_BYTES, raw.length - CURSOR_IV_BYTES), StandardCharsets.UTF_8);
            CursorState state = codec.copyMap(codec.decode(decoded), CursorState.class);
            if (!kind.equals(state.getKind()) || !text(state.getRawId()) || !text(state.getDatasource()) || !text(state.getIdentifier())
                    || state.getIndices() == null || state.getIndices().length == 0 || state.getSeen() < 0 || state.getExpiresAt() <= 0
                    || (!allowExpired && state.getExpiresAt() <= System.currentTimeMillis())) throw cursor();
            return state;
        } catch (Exception error) {
            throw cursor();
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] key = Base64.getDecoder().decode(properties.getCursor().getEncryptionKey());
            if (key.length != CURSOR_KEY_BYTES) throw cursor();
            return new SecretKeySpec(key, CURSOR_KEY_ALGORITHM);
        } catch (Exception error) {
            throw cursor();
        }
    }
}
