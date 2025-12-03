package io.github.surezzzzzz.sdk.mail.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant.MailFlag;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.FetchEmailByCustomHeaderRequest;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.FetchEmailRequest;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.response.MessageCountResponse;
import io.github.surezzzzzz.sdk.mail.configuration.MailComponent;
import io.github.surezzzzzz.sdk.mail.configuration.MailReaderProperties;
import com.sun.mail.imap.IMAPFolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.mail.*;
import javax.mail.internet.MimeUtility;
import javax.mail.search.MessageIDTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/8/16 12:24
 */
@MailComponent
@Slf4j
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.mail.read", name = "username")
public class MailReader {

    private int maxPageSize = 500;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MailReaderProperties mailReaderProperties;
    @Autowired
    private Session readMailSession;

    @Getter
    private Store store;
    @Getter
    private Folder inboxFolder;
    // 默认保存路径
    private static final String DEFAULT_PATH = "attachments";

    @PostConstruct
    public void init() throws Exception {
        if (mailReaderProperties == null) {
            return;
        }
        store = readMailSession.getStore();
        inboxFolder = openInbox();
        maxPageSize = mailReaderProperties.getMaxPageSize();
    }

    public void connect() throws MessagingException {
        if (!store.isConnected()) {
            try {
                store.connect(mailReaderProperties.getUsername(), mailReaderProperties.getPassword());
                log.info("邮件服务器连接成功");
            } catch (IllegalStateException e) {
                log.warn("连接失败: {}", e.getMessage());
            }
        }
    }

    public Folder openReadOnlyInbox() throws MessagingException {
        return openFolder("INBOX", Folder.READ_ONLY);
    }

    public Folder openInbox() throws MessagingException {
        return openFolder("INBOX", Folder.READ_WRITE);
    }

    public Folder openFolder(String folderName, int mode) throws MessagingException {
        connect();
        // 连接收件箱
        Folder folder = store.getFolder(folderName);
        if (!folder.isOpen()) {
            folder.open(mode);
        }
        folder.expunge();
        return folder;
    }

    public Folder reopenInboxFolder() throws MessagingException {
        return reopenFolder(inboxFolder);
    }

    public Folder reopenFolder(Folder folder) throws MessagingException {
        connect();
        if (folder.isOpen()) {
            folder.close(false);
            log.debug("文件夹关闭");
        }
        folder.open(Folder.READ_WRITE);
        log.debug("文件夹打开");
        return folder;
    }

    private Folder openFolder(Folder folder, int mode) throws MessagingException {
        connect();
        if (folder.isOpen()) {
            if (folder.getMode() != mode) {
                closeFolder(folder);  // 如果模式不同，关闭当前文件夹
            }
        }
        // 如果文件夹没有打开，或者模式已经是目标模式，则不需要重新打开
        if (!folder.isOpen() || folder.getMode() != mode) {
            folder.open(mode);  // 以目标模式重新打开文件夹
        }
        folder.expunge();
        return folder;
    }


    public void closeFolder(Folder folder) throws MessagingException {
        // 关闭资源
        folder.close(false);
    }

    /**
     * 获取邮件数量
     *
     * @param folder
     * @return
     * @throws MessagingException
     */
    public MessageCountResponse getFolderMessageCount(Folder folder) throws MessagingException {
        reopenFolder(folder);
        return MessageCountResponse.builder()
                .total(folder.getMessageCount())
                .unread(folder.getUnreadMessageCount())
                .recent(folder.getNewMessageCount())
                .deleted(folder.getDeletedMessageCount())
                .build();
    }

    /**
     * 获取收件箱邮件数量
     *
     * @return
     * @throws MessagingException
     */
    public MessageCountResponse getInboxMessageCount() throws MessagingException {
        return getFolderMessageCount(inboxFolder);
    }

    /**
     * 按条件获取邮件摘要列表
     *
     * @param folder
     * @param pageSize
     * @param mailFlag
     * @return
     * @throws Exception
     */
    public List<Message> fetchEmails(Folder folder, int pageSize, MailFlag mailFlag) throws Exception {
        reopenFolder(folder);
        // 获取邮件总数
        int totalMessages = folder.getMessageCount();
        log.debug("邮件总数: {}", totalMessages);

        List<Message> filteredMessages = new ArrayList<>();
        int count = 0; // 计数器

        for (int i = totalMessages; i > 0 && count < Math.min(pageSize, maxPageSize); i--) {
            Message message = folder.getMessage(i);

            // 预加载邮件标志，确保可以获取 SEEN/RECENT 等状态
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.FLAGS);
            folder.fetch(new Message[]{message}, fetchProfile);
            Flags flags = message.getFlags();
            if (mailFlag.isUnread()) {
                // UNREAD 需要手动检查：即未设置 SEEN 标志
                if (!flags.contains(Flags.Flag.SEEN)) {
                    filteredMessages.add(message);
                    count++;
                }
            } else if (flags.contains(mailFlag.getFlag())) {
                filteredMessages.add(message);
                count++;
            }
        }
        log.debug("filteredMessages count:{}", filteredMessages.size());
        return filteredMessages;
    }

    public Message fetchEmailByMessageId(Folder folder, String messageId) throws Exception {
        reopenFolder(folder);
        // 格式化Message-ID，确保它包含尖括号
        String formattedMessageId = messageId.startsWith("<") ? messageId : "<" + messageId + ">";
        SearchTerm term = new MessageIDTerm(formattedMessageId);

        if (folder instanceof IMAPFolder) {
            // 在IMAP中执行搜索
            Message[] messages = folder.search(term);
            if (messages.length > 0) {
                // 如果找到了匹配的邮件，返回第一封
                return messages[0];
            } else {
                // 如果IMAP搜索没有找到邮件，回退到遍历方式
                for (int i = folder.getMessageCount(); i > 0; i--) {
                    Message message = folder.getMessage(i);
                    String[] headers = message.getHeader("Message-ID");
                    if (headers != null && headers.length > 0 && headers[0].equals(formattedMessageId)) {
                        return message;  // 返回找到的邮件
                    }
                }
            }
        } else {
            // 如果是POP3模式，直接遍历邮件来手动匹配Message-ID
            for (int i = folder.getMessageCount(); i > 0; i--) {
                Message message = folder.getMessage(i);
                String[] headers = message.getHeader("Message-ID");
                if (headers != null && headers.length > 0 && headers[0].equals(formattedMessageId)) {
                    return message;  // 返回找到的邮件
                }
            }
        }

        return null;  // 如果未找到匹配的邮件，返回null
    }

    /**
     * 根据消息id在收件箱查找
     *
     * @param messageId
     * @return
     * @throws Exception
     */
    public Message fetchInboxEmailByMessageId(String messageId) throws Exception {
        return fetchEmailByMessageId(inboxFolder, messageId);
    }

    /**
     * 根据主题关键词遍历收件箱去查找
     *
     * @param subjectKeyword
     * @param pageSize
     * @return
     * @throws Exception
     */
    public List<Message> traverseInboxEmailsBySubjectKeyword(String subjectKeyword, int pageSize) throws Exception {
        return traverseEmailsBySubjectKeyword(inboxFolder, subjectKeyword, pageSize);
    }

    public List<Message> traverseEmailsBySubjectKeyword(Folder folder, String subjectKeyword, int pageSize) throws Exception {
        reopenFolder(folder);
        List<Message> matchedMessages = new ArrayList<>();
        for (int i = folder.getMessageCount(); i > 0 && matchedMessages.size() < Math.min(pageSize, maxPageSize); i--) {
            Message message = folder.getMessage(i);
            if (message.getSubject() != null && message.getSubject().contains(subjectKeyword)) {
                matchedMessages.add(message);
            }
        }
        return matchedMessages;
    }

    /**
     * 根据主题关键词搜索收件箱
     *
     * @param subjectKeyword
     * @param pageSize
     * @return
     * @throws Exception
     */
    public List<Message> fetchInboxEmailsBySubjectKeyword(String subjectKeyword, int pageSize) throws Exception {
        return fetchEmailsByEncodedSubjectKeyword(inboxFolder, subjectKeyword, pageSize);
    }

    public List<Message> fetchEmailsByEncodedSubjectKeyword(Folder folder, String subjectKeyword, int pageSize) throws Exception {
        reopenFolder(folder);
        List<Message> filteredMessages = new ArrayList<>();

        // 对搜索关键词进行编码
        String encodedKeyword = MimeUtility.encodeText(subjectKeyword);

        // 创建搜索条件，匹配主题包含编码后的关键词
        SearchTerm subjectSearchTerm = new SubjectTerm(encodedKeyword);

        Message[] messages = null;
        try {
            // 尝试通过服务器端搜索来提高效率
            messages = folder.search(subjectSearchTerm);
        } catch (Exception e) {
            // 若服务器端搜索失败，则回退到遍历方式
            log.info("服务器端搜索失败，回退到遍历方式：" + e.getMessage());
        }

        // 如果返回的消息为空，使用遍历方式
        if (messages == null || messages.length == 0) {
            for (int i = folder.getMessageCount(); i > 0 && filteredMessages.size() < Math.min(pageSize, maxPageSize); i--) {
                Message message = folder.getMessage(i);
                String subject = message.getSubject();
                if (subject != null) {
                    // 尝试直接匹配编码后的主题
                    if (subject.contains(subjectKeyword)) {
                        filteredMessages.add(message);
                    } else {
                        try {
                            // 解码主题并匹配
                            subject = MimeUtility.decodeText(subject);
                            if (subject.contains(subjectKeyword)) {
                                filteredMessages.add(message);
                            }
                        } catch (Exception ex) {
                            // 解码失败的异常处理
                            log.info("解码主题失败: " + ex.getMessage());
                        }
                    }
                }
            }
        } else {
            filteredMessages.addAll(Arrays.asList(messages));
        }

        // 返回符合条件的邮件，确保不超过 pageSize
        return filteredMessages.subList(0, Math.min(filteredMessages.size(), pageSize));
    }

    public List<Message> fetchEmails(Folder folder, FetchEmailRequest criteria) throws Exception {
        reopenFolder(folder);
        int totalMessages = folder.getMessageCount();
        List<Message> filteredMessages = new ArrayList<>();
        int count = 0;

        for (int i = totalMessages; i > 0 && count < Math.min(criteria.getPageSize(), maxPageSize); i--) {
            Message message = folder.getMessage(i);

            // 预加载邮件标志，确保可以获取 SEEN/RECENT 等状态
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.FLAGS);
            folder.fetch(new Message[]{message}, fetchProfile);

            Flags flags = message.getFlags();
            boolean matchesFlag = true;

            // 处理MailFlag为空的情况，如果未设置MailFlag，跳过相关条件判断
            if (criteria.getMailFlag() != null) {
                if (criteria.getMailFlag().isUnread() && !flags.contains(Flags.Flag.SEEN)) {
                    matchesFlag = true;
                } else if (!flags.contains(criteria.getMailFlag().getFlag())) {
                    matchesFlag = false;
                }
            }

            // 如果符合MailFlag条件，且满足其他条件，则添加到结果中
            if (matchesFlag) {
                boolean subjectMatches = true;

                // 判断主题关键词是否为空，只有在有关键词的情况下才做匹配
                if (criteria.getSubjectKeyword() != null && message.getSubject() != null) {
                    subjectMatches = message.getSubject().contains(criteria.getSubjectKeyword());
                }

                // 如果邮件的主题符合要求，则将其添加到结果列表
                if (subjectMatches) {
                    filteredMessages.add(message);
                    count++;
                }
            }
        }
        log.debug("filteredMessages count:{}", filteredMessages.size());
        return filteredMessages;
    }

    /**
     * 根据条件搜索邮件
     *
     * @param criteria
     * @return
     * @throws Exception
     */
    public List<Message> fetchInboxEmails(FetchEmailRequest criteria) throws Exception {
        return fetchEmails(inboxFolder, criteria);
    }

    /**
     * 按条件获取收件箱邮件摘要列表
     *
     * @param pageSize
     * @param mailFlag
     * @return
     * @throws Exception
     */
    public List<Message> fetchEmails(int pageSize, MailFlag mailFlag) throws Exception {
        return fetchEmails(inboxFolder, pageSize, mailFlag);
    }

    /**
     * 获取正文内容，这之后会将邮件标记为已读
     *
     * @param message
     * @return
     * @throws Exception
     */
    public String extractContent(Message message) throws Exception {
        reopenFolder(message.getFolder());
        StringBuilder content = new StringBuilder();
        Object messageContent = message.getContent();

        // 判断邮件内容类型
        if (messageContent instanceof String) {
            // 如果是简单文本内容，直接返回
            content.append((String) messageContent);
        } else if (messageContent instanceof Multipart) {
            // 如果邮件是 multipart 类型
            Multipart multipart = (Multipart) messageContent;

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String contentType = bodyPart.getContentType();
                // 如果该部分是正文类型，提取内容
                if (StringUtils.containsIgnoreCase(contentType, ("text/plain")) || StringUtils.containsIgnoreCase(contentType, ("text/html"))) {
                    content.append(bodyPart.getContent().toString());
                } else if (StringUtils.containsIgnoreCase(contentType, "multipart")) {
                    // 如果是 multipart 类型，递归提取正文内容
                    content.append(extractNestedContent((Multipart) bodyPart.getContent()));
                }
            }
        } else {
            // 处理其他类型的邮件内容（如果有的话）
            content.append(messageContent.toString());
        }

        return content.toString().isEmpty() ? "" : content.toString();
    }

    public String extractNestedContent(Multipart nestedMultipart) throws Exception {
        StringBuilder nestedContent = new StringBuilder();

        // 遍历 multipart/related 中的各个部分
        for (int i = 0; i < nestedMultipart.getCount(); i++) {
            BodyPart nestedPart = nestedMultipart.getBodyPart(i);
            String contentType = nestedPart.getContentType();

            // 如果内容是文本类型 (text/plain 或 text/html)
            if (StringUtils.containsIgnoreCase(contentType, ("text/plain")) || StringUtils.containsIgnoreCase(contentType, ("text/html"))) {
                String nestedPartContent = nestedPart.getContent().toString();
                nestedContent.append(nestedPartContent);
            } else if (StringUtils.containsIgnoreCase(contentType, "multipart")) {
                // 如果是嵌套的 multipart 类型，继续递归提取
                nestedContent.append(extractNestedContent((Multipart) nestedPart.getContent()));
            }
        }

        return nestedContent.toString();
    }

    public String saveAttachmentToLocal(BodyPart bodyPart) throws Exception {
        connect();
        String fileName = MimeUtility.decodeText(bodyPart.getFileName()); // 处理中文文件名
        File dir = new File("attachments");
        if (!dir.exists()) {
            dir.mkdirs(); // 创建存储目录
        }

        File file = new File(dir, fileName);
        try (InputStream is = bodyPart.getInputStream();
             OutputStream os = Files.newOutputStream(file.toPath())) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
        log.info("附件保存成功: {}", file.getAbsolutePath());
        return file.getAbsolutePath(); // 返回存储路径
    }

    // 提取附件并保存到指定路径
    public List<String> extractAttachments(Message message, String savePath) throws Exception {
        reopenFolder(message.getFolder());
        List<String> attachmentPaths = new ArrayList<>();
        Object messageContent = message.getContent();
        if (messageContent instanceof Multipart) {
            Multipart multipart = (Multipart) messageContent;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) || bodyPart.getFileName() != null) {
                    String path = saveAttachmentToLocal(bodyPart, savePath);
                    attachmentPaths.add(path);
                }
            }
        }
        return attachmentPaths;
    }


    /**
     * 下载所有附件
     *
     * @param message
     * @return 绝对路径
     * @throws Exception
     */
    public List<String> extractAttachments(Message message) throws Exception {
        return extractAttachments(message, DEFAULT_PATH);
    }

    // 保存附件到指定路径
    private String saveAttachmentToLocal(BodyPart bodyPart, String savePath) throws Exception {
        String fileName = MimeUtility.decodeText(bodyPart.getFileName());
        File dir = new File(savePath);
        if (!dir.exists()) {
            dir.mkdirs(); // 创建存储目录
        }

        File file = new File(dir, fileName);
        try (InputStream is = bodyPart.getInputStream();
             OutputStream os = Files.newOutputStream(file.toPath())) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
        log.info("附件保存成功: {}", file.getAbsolutePath());
        return file.getAbsolutePath(); // 返回存储路径
    }


    /**
     * 将邮件标记为已读
     *
     * @param message
     * @throws Exception
     */
    public void markAsRead(Message message) throws Exception {
        // 连接收件箱
        Folder folder = message.getFolder();
        reopenFolder(folder);
        if (message.isSet(Flags.Flag.SEEN)) {
            log.info("邮件已经是已读状态: {}", message.getSubject());
        } else {
            // 设置邮件为已读
            message.setFlag(Flags.Flag.SEEN, true);
            // 重新检查是否已标记为已读
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.FLAGS);
            folder.fetch(new Message[]{message}, fetchProfile);
            if (message.isSet(Flags.Flag.SEEN)) {
                log.info("邮件成功标记为已读: {}", message.getSubject());
            } else {
                log.error("邮件标记为已读失败: {}", message.getSubject());
            }
        }
    }

    /**
     * 将邮件标记为未读
     *
     * @param message
     * @throws Exception
     */
    public void markAsUnread(Message message) throws Exception {
        // 连接收件箱
        Folder folder = message.getFolder();
        reopenFolder(folder);
        if (!message.isSet(Flags.Flag.SEEN)) {
            log.info("邮件已经是未读状态: {}", message.getSubject());
        } else {
            // 设置邮件未已读
            message.setFlag(Flags.Flag.SEEN, false);
            // 重新检查是否已标记为未读
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.FLAGS);
            folder.fetch(new Message[]{message}, fetchProfile);
            if (!message.isSet(Flags.Flag.SEEN)) {
                log.info("邮件成功标记为未读: {}", message.getSubject());
            } else {
                log.error("邮件标记为未读失败: {}", message.getSubject());
            }
        }
    }

    // 获取邮件的 Message-ID
    public String getMessageId(Message message) throws Exception {
        return message.getHeader("Message-ID")[0];
    }

    // 获取邮件的 In-Reply-To
    public String getInReplyTo(Message message) throws Exception {
        String[] inReplyTo = message.getHeader("In-Reply-To");
        return inReplyTo != null ? inReplyTo[0] : null;
    }

    // 获取邮件的 References
    public List<String> getReferences(Message message) throws Exception {
        List<String> referencesList = new ArrayList<>();
        String[] references = message.getHeader("References");

        if (references != null) {
            for (String ref : references) {
                // References 字段包含了多个 Message-ID，拆分并添加到列表
                String[] messageIds = ref.split(" ");
                for (String messageId : messageIds) {
                    referencesList.add(messageId.trim());
                }
            }
        }

        return referencesList;
    }

    // 获取完整的邮件链（包括 References 和 In-Reply-To）
    public List<String> getEmailChain(Message message) throws Exception {
        List<String> emailChain = new ArrayList<>();

        String inReplyTo = getInReplyTo(message);
        if (inReplyTo != null) {
            emailChain.add("In-Reply-To: " + inReplyTo);
        }

        List<String> references = getReferences(message);
        if (!references.isEmpty()) {
            emailChain.add("References: " + String.join(" ", references));
        }

        return emailChain;
    }

    @PreDestroy
    public void close() throws Exception {
        closeFolder(inboxFolder);
        store.close();
    }

    public void moveEmailToFolder(Folder sourceFolder, Message message, Folder targetFolder) throws Exception {
        // 确保源文件夹和目标文件夹是打开的
        openFolder(sourceFolder, Folder.READ_WRITE);  // 源文件夹以读写模式打开
        openFolder(targetFolder, Folder.READ_WRITE);  // 目标文件夹以读写模式打开

        try {
            // 复制邮件到目标文件夹
            sourceFolder.copyMessages(new Message[]{message}, targetFolder);
            // 将源文件夹中的邮件标记为删除
            message.setFlag(Flags.Flag.DELETED, true);
            sourceFolder.expunge(); // 清除删除标记的邮件
        } catch (Exception e) {
            log.error("移动邮件失败", e);
            throw e;
        }
    }

    /**
     * 根据自定义header在收件箱搜索
     *
     * @param headerName
     * @param headerValue
     * @param pageSize
     * @return
     * @throws Exception
     */
    public List<Message> fetchInboxEmailsByCustomHeaderAndValue(String headerName, String headerValue, int pageSize, int pageNo) throws Exception {
        return fetchEmailsByCustomHeaderAndValue(inboxFolder, headerName, headerValue, pageSize, pageNo);
    }

    /**
     * 根据自定义header在收件箱搜索
     *
     * @param request
     * @return
     * @throws Exception
     */
    public List<Message> fetchInboxEmailsByCustomHeaderAndValue(FetchEmailByCustomHeaderRequest request) throws Exception {
        return fetchEmailsByCustomHeaderAndValue(request.getFolder() == null ? inboxFolder : request.getFolder(),
                request.getHeaderName(),
                request.getHeaderValue(),
                request.getPageSize(),
                request.getPageNo());
    }

    // 根据自定义 Header 搜索邮件
    public List<Message> fetchEmailsByCustomHeaderAndValue(Folder folder, String headerName, String headerValue, int pageSize, int pageNo) throws Exception {
        reopenFolder(folder);
        List<Message> filteredMessages = new ArrayList<>();
        int totalMessages = folder.getMessageCount();
        // 如果没有邮件或请求的页面超出总邮件数量，直接返回空列表
        if (totalMessages == 0 || pageSize * pageNo > totalMessages) {
            return filteredMessages;
        }

        // 遍历邮件并按条件过滤
        for (int i = folder.getMessageCount(); i > 0 && filteredMessages.size() < Math.min(pageSize * pageNo, maxPageSize); i--) {
            Message message = folder.getMessage(i);
            String[] values = message.getHeader(headerName);

            if (values != null) {
                // 如果邮件的头部值匹配指定的 headerName 和 headerValue，则加入结果
                for (String value : values) {
                    if (value.contains(headerValue)) {
                        filteredMessages.add(message);
                        break;
                    }
                }
            }
        }
        // 计算分页范围
        int startIndex = (pageNo - 1) * pageSize;
        if (startIndex > filteredMessages.size()) {
            return new ArrayList<>();
        }
        int endIndex = Math.min(startIndex + pageSize, filteredMessages.size());
        return filteredMessages.subList(startIndex, endIndex);
    }

    public List<Message> fetchInboxEmailsByCustomHeader(String headerName, int pageSize, int pageNo) throws Exception {
        return fetchEmailsByCustomHeader(inboxFolder, headerName, pageSize, pageNo);
    }

    public List<Message> fetchEmailsByCustomHeader(Folder folder, String headerName, int pageSize, int pageNo) throws Exception {
        reopenFolder(folder);
        List<Message> filteredMessages = new ArrayList<>();
        int totalMessages = folder.getMessageCount();
        // 如果没有邮件或请求的页面超出总邮件数量，直接返回空列表
        if (totalMessages == 0 || pageSize * pageNo > totalMessages) {
            return filteredMessages;
        }

        // 遍历邮件并按条件过滤
        for (int i = folder.getMessageCount(); i > 0 && filteredMessages.size() < Math.min(pageSize * pageNo, maxPageSize); i--) {
            Message message = folder.getMessage(i);
            String[] values = message.getHeader(headerName);
            if (values != null) {
                filteredMessages.add(message);
            }
        }
        // 计算分页范围
        int startIndex = (pageNo - 1) * pageSize;
        if (startIndex > filteredMessages.size()) {
            return new ArrayList<>();
        }
        int endIndex = Math.min(startIndex + pageSize, filteredMessages.size());
        return filteredMessages.subList(startIndex, endIndex);
    }

}
