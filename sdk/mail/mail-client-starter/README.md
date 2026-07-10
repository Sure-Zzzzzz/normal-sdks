# Mail Client Starter

基于 Spring Boot 的邮件客户端 SDK，提供普通 SMTP 邮件发送、邮件读取、附件解析和本地附件存储能力。

## 功能特性

- 支持普通 SMTP / SMTPS 邮件发送
- 支持纯文本和 HTML 邮件
- 支持 TO / CC / BCC 收件人
- 支持附件路径和字节数组附件
- 支持自定义邮件头和自定义 Message-ID Header
- 支持按 Message-ID、自定义 Header、主题关键词和邮件标记读取邮件
- 支持显式标记已读、未读和移动邮件
- 支持本地附件存储扩展
- 支持 Spring Boot 自动配置

## 快速开始

### 1. 添加依赖

由于 mail-client-starter 使用 `compileOnly` 配置，调用方需要显式添加运行时依赖：

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:mail-client-starter:2.0.0'
    implementation 'org.springframework:spring-context-support'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.sun.mail:jakarta.mail:1.6.7'
}
```

### 2. 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mail:
          enable: true
          send:
            default-provider: normal
            normal:
              enable: true
              host: smtp.example.test
              port: 465
              username: ${SEND_MAIL_USERNAME:sender@example.test}
              password: ${SEND_MAIL_PASSWORD:your-password}
              properties:
                mail:
                  smtp:
                    auth: true
                    starttls:
                      enable: true
              protocol: smtps
              default-encoding: UTF-8
          read:
            enable: false
            username: ${READ_MAIL_USERNAME:reader@example.test}
            password: ${READ_MAIL_PASSWORD:your-password}
            properties:
              mail:
                store:
                  protocol: imaps
                imaps:
                  host: imap.example.test
                  port: 993
                  ssl:
                    enable: true
```

`properties` 支持 JavaMail 扁平 key 和 YAML 嵌套结构，两种写法都会展开为 JavaMail `Properties`。

## 使用示例

### 发送纯文本邮件

```java
import io.github.surezzzzzz.sdk.mail.engine.MailSendEngine;
import org.springframework.stereotype.Service;

@Service
public class MailDemoService {

    private final MailSendEngine mailSendEngine;

    public MailDemoService(MailSendEngine mailSendEngine) {
        this.mailSendEngine = mailSendEngine;
    }

    public void sendText() {
        mailSendEngine.send("测试邮件", "这是一封测试邮件", "recipient@example.com");
    }
}
```

### 发送 HTML 和附件

```java
import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.engine.MailSendEngine;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.value.MailAttachment;
import io.github.surezzzzzz.sdk.mail.model.value.MailContent;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class MailDemoService {

    private final MailSendEngine mailSendEngine;

    public MailDemoService(MailSendEngine mailSendEngine) {
        this.mailSendEngine = mailSendEngine;
    }

    public void sendHtmlWithAttachment() {
        MailSendRequest request = MailSendRequest.builder()
                .to(Arrays.asList("recipient@example.com"))
                .cc(Arrays.asList("cc@example.com"))
                .subject("带附件的邮件")
                .content(MailContent.builder()
                        .type(MailContentType.HTML)
                        .text("<h1>测试邮件</h1>")
                        .build())
                .attachments(Arrays.asList(MailAttachment.builder()
                        .fileName("test.txt")
                        .path("/tmp/test.txt")
                        .build()))
                .build();

        mailSendEngine.send(request);
    }
}
```

### 读取邮件

```java
import io.github.surezzzzzz.sdk.mail.engine.MailReadEngine;
import io.github.surezzzzzz.sdk.mail.model.request.MailPageRequest;
import io.github.surezzzzzz.sdk.mail.model.request.MailSearchRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailPageResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;
import org.springframework.stereotype.Service;

@Service
public class MailReadDemoService {

    private final MailReadEngine mailReadEngine;

    public MailReadDemoService(MailReadEngine mailReadEngine) {
        this.mailReadEngine = mailReadEngine;
    }

    public MailPageResult searchInbox() {
        MailSearchRequest request = MailSearchRequest.builder()
                .subjectKeyword("测试")
                .page(MailPageRequest.builder().pageNo(1).pageSize(20).build())
                .build();
        return mailReadEngine.search(request);
    }

    public MailReadResult readByMessageId(String messageId) {
        return mailReadEngine.readByMessageId(messageId);
    }
}
```

## 扩展点

- `MailSendEngine`：发送入口，可通过自定义 Bean 替换默认实现
- `MailReadEngine`：读取入口，可通过自定义 Bean 替换默认实现
- `MailSenderProvider`：发送 Provider SPI，默认提供普通 SMTP 实现
- `MailMessageIdGenerator`：Message-ID 生成 SPI
- `MailMessageParser`：邮件解析 SPI
- `MailAttachmentStorage`：附件存储 SPI

## 注意事项

- 2.0.0 移除了 1.x 的 `MailSender`、`MailReader` 和 `api.endpoint.schema` 旧入口，统一使用 `MailSendEngine` / `MailReadEngine`。
- 读取操作默认每次创建并关闭 `Store` / `Folder`，普通搜索和读取使用只读模式，不会自动 expunge。
- 发送日志只记录 provider 和数量统计，不记录完整正文、收件人列表或附件路径。
