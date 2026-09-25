package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response;

import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.entity.DecompressData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * B2M 发送响应内部载体（平台原始响应，不进公共 API）。
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SendTemplateSmsResponse {
    private ResponseEntity<byte[]> responseEntity;
    private List<DecompressData> decompressData;
}
