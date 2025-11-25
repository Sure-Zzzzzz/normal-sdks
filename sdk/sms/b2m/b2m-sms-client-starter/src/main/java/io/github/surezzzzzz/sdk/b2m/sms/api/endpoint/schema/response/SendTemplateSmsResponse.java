package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response;

import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.entity.DecompressData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/12 8:11
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SendTemplateSmsResponse {
    private ResponseEntity<byte[]> responseEntity;
    private List<DecompressData> decompressData;
}
