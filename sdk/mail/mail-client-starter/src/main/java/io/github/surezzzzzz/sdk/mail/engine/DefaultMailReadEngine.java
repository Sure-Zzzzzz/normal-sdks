package io.github.surezzzzzz.sdk.mail.engine;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import io.github.surezzzzzz.sdk.mail.constant.MailFlag;
import io.github.surezzzzzz.sdk.mail.exception.MailReadException;
import io.github.surezzzzzz.sdk.mail.exception.MailValidationException;
import io.github.surezzzzzz.sdk.mail.factory.MailSessionFactory;
import io.github.surezzzzzz.sdk.mail.model.request.MailMoveRequest;
import io.github.surezzzzzz.sdk.mail.model.request.MailPageRequest;
import io.github.surezzzzzz.sdk.mail.model.request.MailSearchRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailOperationResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailPageResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;
import io.github.surezzzzzz.sdk.mail.parser.MailMessageParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认 Mail 读取引擎
 *
 * @author surezzzzzz
 */
@MailComponent
@ConditionalOnMissingBean(MailReadEngine.class)
@ConditionalOnProperty(prefix = MailConstant.READ_CONFIG_PREFIX, name = MailConstant.PROPERTY_ENABLE, havingValue = MailConstant.PROPERTY_TRUE)
public class DefaultMailReadEngine implements MailReadEngine {

    private final MailProperties properties;
    private final MailSessionFactory sessionFactory;
    private final MailMessageParser parser;

    public DefaultMailReadEngine(MailProperties properties, MailSessionFactory sessionFactory, MailMessageParser parser) {
        this.properties = properties;
        this.sessionFactory = sessionFactory;
        this.parser = parser;
    }

    @Override
    public MailPageResult search(MailSearchRequest request) {
        Store store = null;
        Folder folder = null;
        try {
            SearchContext context = buildSearchContext(request);
            store = sessionFactory.createStore();
            folder = openFolder(store, context.folderName, Folder.READ_ONLY);
            List<MailReadResult> matched = new ArrayList<>();
            int scanned = 0;
            for (int i = folder.getMessageCount(); i > 0 && scanned < properties.getRead().getMaxScanSize(); i--) {
                Message message = folder.getMessage(i);
                scanned++;
                if (matches(message, context.request)) {
                    matched.add(parser.parse(message));
                }
            }
            return pageResult(matched, context.page, scanned);
        } catch (Exception e) {
            throw new MailReadException(ErrorCode.READ_FAILED, String.format(ErrorMessage.READ_FAILED, e.getMessage()), e);
        } finally {
            close(folder, store);
        }
    }

    @Override
    public MailReadResult readByMessageId(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.MESSAGE_ID_REQUIRED);
        }
        MailPageResult pageResult = search(MailSearchRequest.builder()
                .headerName(MailConstant.HEADER_MESSAGE_ID)
                .headerValue(messageId)
                .page(MailPageRequest.builder().pageNo(MailConstant.DEFAULT_PAGE_NO).pageSize(MailConstant.DEFAULT_PAGE_SIZE).build())
                .build());
        return pageResult.getRecords().isEmpty() ? null : pageResult.getRecords().get(0);
    }

    @Override
    public MailReadResult readByCustomHeader(String headerName, String headerValue) {
        if (StringUtils.isBlank(headerName)) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.HEADER_REQUIRED);
        }
        MailPageResult pageResult = search(MailSearchRequest.builder()
                .headerName(headerName)
                .headerValue(headerValue)
                .page(MailPageRequest.builder().pageNo(MailConstant.DEFAULT_PAGE_NO).pageSize(MailConstant.DEFAULT_PAGE_SIZE).build())
                .build());
        return pageResult.getRecords().isEmpty() ? null : pageResult.getRecords().get(0);
    }

    @Override
    public MailOperationResult markAsRead(String folder, String messageId) {
        return setSeen(folder, messageId, true);
    }

    @Override
    public MailOperationResult markAsUnread(String folder, String messageId) {
        return setSeen(folder, messageId, false);
    }

    @Override
    public MailOperationResult move(MailMoveRequest request) {
        validateMoveRequest(request);
        Store store = null;
        Folder sourceFolder = null;
        Folder targetFolder = null;
        try {
            store = sessionFactory.createStore();
            sourceFolder = openFolder(store, resolveFolder(request.getSourceFolder()), Folder.READ_WRITE);
            targetFolder = openFolder(store, request.getTargetFolder(), Folder.READ_WRITE);
            Message message = findByMessageId(sourceFolder, request.getMessageId());
            if (message == null) {
                return MailOperationResult.builder().success(false).messageId(request.getMessageId()).folder(request.getSourceFolder()).build();
            }
            sourceFolder.copyMessages(new Message[]{message}, targetFolder);
            message.setFlag(Flags.Flag.DELETED, true);
            if (request.isExpunge()) {
                sourceFolder.expunge();
            }
            return MailOperationResult.builder().success(true).messageId(request.getMessageId()).folder(request.getTargetFolder()).build();
        } catch (Exception e) {
            throw new MailReadException(ErrorCode.STATUS_CHANGE_FAILED, String.format(ErrorMessage.STATUS_CHANGE_FAILED, e.getMessage()), e);
        } finally {
            close(targetFolder, null);
            close(sourceFolder, store);
        }
    }

    private MailOperationResult setSeen(String folderName, String messageId, boolean seen) {
        if (StringUtils.isBlank(messageId)) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.MESSAGE_ID_REQUIRED);
        }
        Store store = null;
        Folder folder = null;
        try {
            store = sessionFactory.createStore();
            String resolvedFolder = resolveFolder(folderName);
            folder = openFolder(store, resolvedFolder, Folder.READ_WRITE);
            Message message = findByMessageId(folder, messageId);
            if (message == null) {
                return MailOperationResult.builder().success(false).messageId(messageId).folder(resolvedFolder).build();
            }
            message.setFlag(Flags.Flag.SEEN, seen);
            return MailOperationResult.builder().success(true).messageId(messageId).folder(resolvedFolder).build();
        } catch (Exception e) {
            throw new MailReadException(ErrorCode.STATUS_CHANGE_FAILED, String.format(ErrorMessage.STATUS_CHANGE_FAILED, e.getMessage()), e);
        } finally {
            close(folder, store);
        }
    }

    private boolean matches(Message message, MailSearchRequest request) throws Exception {
        if (StringUtils.isNotBlank(request.getSubjectKeyword())
                && (message.getSubject() == null || !message.getSubject().contains(request.getSubjectKeyword()))) {
            return false;
        }
        if (request.getMailFlag() != null && !matchesFlag(message, request.getMailFlag())) {
            return false;
        }
        if (StringUtils.isNotBlank(request.getHeaderName())) {
            String[] values = message.getHeader(request.getHeaderName());
            if (values == null || values.length == 0) {
                return false;
            }
            if (StringUtils.isBlank(request.getHeaderValue())) {
                return true;
            }
            for (String value : values) {
                if (value != null && value.contains(request.getHeaderValue())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean matchesFlag(Message message, MailFlag mailFlag) throws Exception {
        if (mailFlag.isUnread()) {
            return !message.isSet(Flags.Flag.SEEN);
        }
        return mailFlag.getFlag() != null && message.isSet(mailFlag.getFlag());
    }

    private Message findByMessageId(Folder folder, String messageId) throws Exception {
        for (int i = folder.getMessageCount(); i > 0; i--) {
            Message message = folder.getMessage(i);
            if (message instanceof MimeMessage && messageId.equals(((MimeMessage) message).getMessageID())) {
                return message;
            }
            String[] values = message.getHeader(MailConstant.HEADER_MESSAGE_ID);
            if (values != null) {
                for (String value : values) {
                    if (messageId.equals(value)) {
                        return message;
                    }
                }
            }
        }
        return null;
    }

    private MailPageResult pageResult(List<MailReadResult> matched, MailPageRequest page, int scanned) {
        int start = (page.getPageNo() - 1) * page.getPageSize();
        if (start >= matched.size()) {
            return MailPageResult.builder()
                    .pageNo(page.getPageNo())
                    .pageSize(page.getPageSize())
                    .scanned(scanned)
                    .matched(matched.size())
                    .records(Collections.emptyList())
                    .build();
        }
        int end = Math.min(start + page.getPageSize(), matched.size());
        return MailPageResult.builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .scanned(scanned)
                .matched(matched.size())
                .records(new ArrayList<>(matched.subList(start, end)))
                .build();
    }

    private SearchContext buildSearchContext(MailSearchRequest request) {
        MailSearchRequest actualRequest = request == null ? new MailSearchRequest() : request;
        MailPageRequest page = normalizePage(actualRequest.getPage());
        SearchContext context = new SearchContext();
        context.request = actualRequest;
        context.page = page;
        context.folderName = resolveFolder(actualRequest.getFolder());
        return context;
    }

    private MailPageRequest normalizePage(MailPageRequest page) {
        MailPageRequest actualPage = page == null ? new MailPageRequest() : page;
        int pageNo = actualPage.getPageNo() <= 0 ? MailConstant.DEFAULT_PAGE_NO : actualPage.getPageNo();
        int pageSize = actualPage.getPageSize() <= 0 ? MailConstant.DEFAULT_PAGE_SIZE : actualPage.getPageSize();
        pageSize = Math.min(pageSize, properties.getRead().getMaxPageSize());
        return MailPageRequest.builder().pageNo(pageNo).pageSize(pageSize).build();
    }

    private String resolveFolder(String folder) {
        return StringUtils.isBlank(folder) ? MailConstant.DEFAULT_INBOX_FOLDER : folder;
    }

    private Folder openFolder(Store store, String folderName, int mode) throws Exception {
        Folder folder = store.getFolder(folderName);
        folder.open(mode);
        return folder;
    }

    private void validateMoveRequest(MailMoveRequest request) {
        if (request == null || StringUtils.isBlank(request.getMessageId())) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.MESSAGE_ID_REQUIRED);
        }
        if (StringUtils.isBlank(request.getTargetFolder())) {
            throw new MailValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.TARGET_FOLDER_REQUIRED);
        }
    }

    private void close(Folder folder, Store store) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
        }
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static class SearchContext {
        private MailSearchRequest request;
        private MailPageRequest page;
        private String folderName;
    }
}
