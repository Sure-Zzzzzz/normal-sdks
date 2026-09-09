package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.message.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamMessageEntity;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Web 站内信分页响应
 *
 * @author surezzzzzz
 */
@Data
public class WebMessagePageResponse {

    private List<WebMessageResponse> content;

    private long totalElements;

    private int totalPages;

    private int page;

    private int size;

    private int numberOfElements;

    private boolean first;

    private boolean last;

    private boolean empty;

    /**
     * 站内信分页结果转响应视图
     */
    public static WebMessagePageResponse from(Page<IamMessageEntity> source) {
        WebMessagePageResponse response = new WebMessagePageResponse();
        response.setContent(source.getContent().stream()
                .map(WebMessageResponse::from)
                .collect(Collectors.toList()));
        response.setTotalElements(source.getTotalElements());
        response.setTotalPages(source.getTotalPages());
        response.setPage(source.getNumber() + 1);
        response.setSize(source.getSize());
        response.setNumberOfElements(source.getNumberOfElements());
        response.setFirst(source.isFirst());
        response.setLast(source.isLast());
        response.setEmpty(source.isEmpty());
        return response;
    }
}
