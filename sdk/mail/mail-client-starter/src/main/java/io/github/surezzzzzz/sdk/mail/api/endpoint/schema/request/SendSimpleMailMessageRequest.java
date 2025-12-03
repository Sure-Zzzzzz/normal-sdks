package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.mail.SimpleMailMessage;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/22 14:55
 */
@Getter
@Setter
@NoArgsConstructor
public class SendSimpleMailMessageRequest extends SimpleMailMessage {

}