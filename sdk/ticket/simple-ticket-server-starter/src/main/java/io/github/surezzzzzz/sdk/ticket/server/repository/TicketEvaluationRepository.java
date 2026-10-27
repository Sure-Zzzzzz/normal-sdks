package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketEvaluation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

/**
 * 工单评价数据访问（ticket_id 唯一——一次不可改，重复插入映射 409）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketEvaluationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketEvaluationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入评价（唯一键天然一次）。
     *
     * @param evaluation 评价
     * @return 自增主键
     */
    public long insert(TicketEvaluation evaluation) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("ticketId", evaluation.getTicketId())
                .addValue("evaluator", evaluation.getEvaluatorSubjectId())
                .addValue("star", evaluation.getStar())
                .addValue("comment", evaluation.getComment(), Types.VARCHAR)
                .addValue("evaluatedAt", Timestamp.from(evaluation.getEvaluatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket_evaluation (ticket_id, evaluator_subject_id, star, "
                + "comment, evaluated_at) VALUES (:ticketId, :evaluator, :star, :comment, :evaluatedAt)",
                p, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 按工单查评价。
     *
     * @param ticketId 工单 ID
     * @return 评价
     */
    public Optional<TicketEvaluation> findByTicket(long ticketId) {
        java.util.List<TicketEvaluation> result = jdbcTemplate.query(
                "SELECT * FROM ticket_evaluation WHERE ticket_id = :ticketId",
                new MapSqlParameterSource("ticketId", ticketId), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    private static final RowMapper<TicketEvaluation> ROW_MAPPER = new RowMapper<TicketEvaluation>() {
        @Override
        public TicketEvaluation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return TicketEvaluation.builder()
                    .id(rs.getLong("id"))
                    .ticketId(rs.getLong("ticket_id"))
                    .evaluatorSubjectId(rs.getString("evaluator_subject_id"))
                    .star(rs.getInt("star"))
                    .comment(rs.getString("comment"))
                    .evaluatedAt(rs.getTimestamp("evaluated_at").toInstant())
                    .build();
        }
    };
}
