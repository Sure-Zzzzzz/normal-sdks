package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentSource;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 从已验证资源上下文读取数据授权文档。
 *
 * @author surezzzzzz
 */
public final class VerifiedResourceDataGrantDocumentSource implements DataGrantDocumentSource {

    /**
     * 获取当前请求的已验证数据授权文档。
     *
     * @return 已验证数据授权文档
     */
    @Override
    public Optional<DataGrantDocument> currentDocument() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            return Optional.empty();
        }
        VerifiedResourceContext context = (VerifiedResourceContext) authentication.getPrincipal();
        return Optional.ofNullable(context.getApplicationAuthorization().getDataGrantDocument());
    }
}
