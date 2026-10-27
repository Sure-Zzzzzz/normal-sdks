package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单号日序列数据访问（原子 UPSERT 后在同一事务读取；
 * 丢号不回补——DESIGN §6.3）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketNoSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public TicketNoSequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 取下一序列（UPSERT 行锁串行化；首单并发不先读不存在的行）。
     *
     * @param dateKey 北京日期 yyMMdd
     * @return 序列值（从 1 起）
     */
    @Transactional
    public long next(String dateKey) {
        jdbcTemplate.update("INSERT INTO ticket_no_sequence (date_key, seq) VALUES (?, 1) "
                + "ON DUPLICATE KEY UPDATE seq = seq + 1", dateKey);
        return jdbcTemplate.queryForObject(
                "SELECT seq FROM ticket_no_sequence WHERE date_key = ?", Long.class, dateKey);
    }
}
