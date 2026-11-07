package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.MagicTaskStatus;
import com.seasonplan.lifecycle.domain.TimeWindow;
import com.seasonplan.lifecycle.application.port.MagicTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMagicTaskRepository implements MagicTaskRepository {
    private final JdbcTemplate jdbc;

    public JdbcMagicTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MagicTask save(MagicTask task, String actorId) {
        int updated = jdbc.update("""
                update magic_tasks
                   set title = ?,
                       description = ?,
                       required_talent = ?,
                       starts_at = ?,
                       ends_at = ?,
                       status = ?,
                       updated_by = ?,
                       updated_at = now()
                 where id = ?
                """,
                task.title(),
                task.description(),
                task.requiredTalent(),
                Timestamp.valueOf(task.timeWindow().start()),
                Timestamp.valueOf(task.timeWindow().end()),
                task.status().name(),
                actorId,
                task.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into magic_tasks (
                        id, season_plan_id, title, description, required_talent, starts_at, ends_at,
                        status, created_by, updated_by
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.id(),
                    task.seasonPlanId(),
                    task.title(),
                    task.description(),
                    task.requiredTalent(),
                    Timestamp.valueOf(task.timeWindow().start()),
                    Timestamp.valueOf(task.timeWindow().end()),
                    task.status().name(),
                    actorId,
                    actorId);
        }
        return task;
    }

    @Override
    public Optional<MagicTask> findById(UUID id) {
        return jdbc.query("select * from magic_tasks where id = ?", this::mapTask, id).stream().findFirst();
    }

    @Override
    public List<MagicTask> findBySeasonPlanId(UUID seasonPlanId) {
        return jdbc.query("select * from magic_tasks where season_plan_id = ? order by starts_at, title", this::mapTask, seasonPlanId);
    }

    private MagicTask mapTask(ResultSet rs, int rowNum) throws SQLException {
        MagicTask task = new MagicTask(
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("required_talent"),
                new TimeWindow(rs.getTimestamp("starts_at").toLocalDateTime(), rs.getTimestamp("ends_at").toLocalDateTime()),
                MagicTaskStatus.valueOf(rs.getString("status"))
        );
        task.setId(rs.getObject("id", UUID.class));
        task.setSeasonPlanId(rs.getObject("season_plan_id", UUID.class));
        return task;
    }
}
