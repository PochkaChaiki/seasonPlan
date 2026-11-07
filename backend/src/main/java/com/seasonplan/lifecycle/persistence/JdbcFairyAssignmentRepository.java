package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.application.port.FairyAssignmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcFairyAssignmentRepository implements FairyAssignmentRepository {
    private final JdbcTemplate jdbc;

    public JdbcFairyAssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FairyAssignment save(FairyAssignment assignment, String actorId) {
        int updated = jdbc.update("""
                update fairy_assignments
                   set fairy_id = ?,
                       role = ?,
                       talent = ?,
                       status = ?,
                       updated_by = ?,
                       updated_at = now()
                 where id = ?
                """,
                assignment.fairyId(),
                assignment.role(),
                assignment.talent(),
                assignment.status().name(),
                actorId,
                assignment.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into fairy_assignments (
                        id, magic_task_id, fairy_id, role, talent, status, created_by, updated_by
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    assignment.id(),
                    assignment.magicTaskId(),
                    assignment.fairyId(),
                    assignment.role(),
                    assignment.talent(),
                    assignment.status().name(),
                    actorId,
                    actorId);
        }
        return assignment;
    }

    @Override
    public List<FairyAssignment> findByMagicTaskId(UUID magicTaskId) {
        return jdbc.query("select * from fairy_assignments where magic_task_id = ? order by created_at, id", this::mapAssignment, magicTaskId);
    }

    @Override
    public List<FairyAssignment> findConfirmedByFairyId(String fairyId) {
        return jdbc.query("""
                select *
                  from fairy_assignments
                 where fairy_id = ? and status = 'CONFIRMED'
                 order by created_at, id
                """, this::mapAssignment, fairyId);
    }

    private FairyAssignment mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        return new FairyAssignment(
                rs.getObject("id", UUID.class),
                rs.getObject("magic_task_id", UUID.class),
                rs.getString("fairy_id"),
                rs.getString("role"),
                rs.getString("talent"),
                AssignmentStatus.valueOf(rs.getString("status"))
        );
    }
}
