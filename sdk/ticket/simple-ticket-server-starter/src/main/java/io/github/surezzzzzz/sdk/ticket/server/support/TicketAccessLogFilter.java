package io.github.surezzzzzz.sdk.ticket.server.support;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * API 入口埋点（DEBUG 级 method/path/status/耗时——DESIGN §14；默认关，无敏感数据）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class TicketAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TicketAccessLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("工单 API：method={}, path={}, status={}, 耗时={}ms", request.getMethod(),
                        request.getRequestURI(), response.getStatus(),
                        System.currentTimeMillis() - start);
            }
        }
    }
}
