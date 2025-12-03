# Mail Client Starter

一个基于Spring Boot的邮件发送和读取SDK，支持多种邮件服务提供商。

## 功能特性

- ✅ 支持普通SMTP邮件发送
- ✅ 支持SendCloud SMTP邮件发送  
- ✅ 支持邮件读取和解析
- ✅ 支持附件发送
- ✅ 支持HTML和纯文本邮件
- ✅ 支持自定义邮件头
- ✅ Spring Boot自动配置

## 快速开始

### 1. 添加依赖

由于mail-client-starter使用`compileOnly`配置，您需要显式添加以下依赖：

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:mail-client-starter:1.0.0'
    // 必需的依赖
    implementation 'org.springframework:spring-context-support'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.sun.mail:jakarta.mail:1.6.7'
    // 自动传递的依赖
    implementation 'org.apache.commons:commons-lang3:3.13.0'
}
```

### 2. 配置邮件发送

#### 2.1 普通SMTP配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mail:
          send:
            normal:
              host: smtp.gmail.com
              port: 587
              username: your-email@gmail.com
              password: your-password
              protocol: smtp
              default-encoding: UTF-8
              properties:
                mail.smtp.auth: true
                mail.smtp.starttls.enable: true
                mail.smtp.connectiontimeout: 5000
                mail.smtp.timeout: 5000
                mail.smtp.writetimeout: 5000
              id-domain: your-domain.com
              custom-message-id-header: X-YourApp-Message-ID
```

#### 2.2 SendCloud SMTP配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mail:
          send:
            send-cloud:
              api-user: your-api-user
              api-key: your-api-key
              from: noreply@your-domain.com
              properties:
                mail.smtp.host: smtp.sendcloud.net
                mail.smtp.port: 587
                mail.smtp.auth: true
                mail.smtp.starttls.enable: true
```

### 3. 配置邮件读取（可选）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mail:
          read:
              host: imap.gmail.com
              port: 993
              username: your-email@gmail.com
              password: your-password
              protocol: imap
              max-page-size: 500
              properties:
                mail.imap.ssl.enable: true
                mail.imap.connectiontimeout: 5000
                mail.imap.timeout: 5000
```

## 使用示例

### 发送简单邮件

```java
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendSimpleMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.MailSender;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EmailService {
    
    @Autowired
    private MailSender mailSender;
    
    public void sendSimpleEmail() throws Exception {
        SendSimpleMailMessageRequest request = new SendSimpleMailMessageRequest();
        request.setTo("recipient@example.com");
        request.setSubject("测试邮件");
        request.setText("这是一封测试邮件");
        
        mailSender.sendSimpleMail(request);
    }
}
```

### 发送带附件的邮件

```java
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendMimeMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.MailSender;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EmailService {
    
    @Autowired
    private MailSender mailSender;
    
    public void sendEmailWithAttachment() throws Exception {
        SendMimeMailMessageRequest request = SendMimeMailMessageRequest.builder()
                .to(new String[]{"recipient@example.com"})
                .cc(new String[]{"cc@example.com"})
                .subject("带附件的邮件")
                .text("这是一封带附件的邮件")
                .textType("html")  // 或 "text"
                .filePaths(new String[]{"/path/to/attachment.pdf"})
                .build();
        
        // 添加自定义邮件头
        request.addHeader("X-Priority", "1");
        request.addHeader("X-Custom-Flag", "important");
        
        mailSender.sendMimeMail(request);
    }
}
```

### 读取邮件

```java
import io.github.surezzzzzz.sdk.mail.client.MailReader;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.response.MessageCountResponse;
import javax.mail.Message;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EmailReaderService {
    
    @Autowired
    private MailReader mailReader;
    
    public void readEmails() throws Exception {
        // 获取收件箱邮件数量
        MessageCountResponse count = mailReader.getInboxMessageCount();
        System.out.println("总邮件数: " + count.getTotal());
        System.out.println("未读邮件: " + count.getUnread());
        
        // 获取最近邮件
        List<Message> recentEmails = mailReader.fetchEmails(
            mailReader.getInboxFolder(), 
            10,  // 获取10封邮件
            MailFlag.RECENT
        );
        
        for (Message message : recentEmails) {
            System.out.println("主题: " + message.getSubject());
            System.out.println("发件人: " + message.getFrom()[0]);
        }
    }
}
```

## 注意事项

### ⚠️ 依赖声明要求

**重要提醒**：由于mail-client-starter使用`compileOnly`配置，以下依赖不会自动传递，必须在使用者的`build.gradle`中显式声明：
- `org.springframework:spring-context-support`
- `org.springframework.boot:spring-boot-starter-validation`  
- `org.springframework.boot:spring-boot-starter-mail`
- `com.fasterxml.jackson.core:jackson-databind`
- `com.sun.mail:jakarta.mail:1.6.7`

## 支持与贡献

如有问题或建议，欢迎提交Issue或Pull Request。
