package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.service.PollenReservationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPollenReservationRepository implements PollenReservationRepository {
    private final JdbcTemplate jdbc;

    public JdbcPollenReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PollenReservation save(PollenReservation reservation) {
        int updated = jdbc.update("""
                update pollen_reservations
                   set confirmed_amount = ?,
                       status = ?,
                       comment = ?,
                       confirmed_by = ?,
                       updated_at = now()
                 where id = ?
                """,
                reservation.confirmedAmount(),
                reservation.status().name(),
                reservation.comment(),
                reservation.confirmedBy(),
                reservation.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into pollen_reservations (
                        id, season_plan_id, requested_amount, confirmed_amount, status, comment, confirmed_by
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    reservation.id(),
                    reservation.seasonPlanId(),
                    reservation.requestedAmount(),
                    reservation.confirmedAmount(),
                    reservation.status().name(),
                    reservation.comment(),
                    reservation.confirmedBy());
        }
        return reservation;
    }

    @Override
    public Optional<PollenReservation> findById(UUID id) {
        return jdbc.query("select * from pollen_reservations where id = ?", this::mapReservation, id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId) {
        return jdbc.query("""
                        select * from pollen_reservations
                         where season_plan_id = ?
                         order by created_at desc, id desc
                         limit 1
                        """,
                        this::mapReservation,
                        seasonPlanId)
                .stream()
                .findFirst();
    }

    @Override
    public Collection<PollenReservation> all() {
        return jdbc.query("select * from pollen_reservations", this::mapReservation);
    }

    private PollenReservation mapReservation(ResultSet rs, int rowNum) throws SQLException {
        PollenReservation reservation = new PollenReservation(
                rs.getObject("id", UUID.class),
                rs.getObject("season_plan_id", UUID.class),
                rs.getBigDecimal("requested_amount").intValue()
        );
        BigDecimal confirmedAmount = rs.getBigDecimal("confirmed_amount");
        String status = rs.getString("status");
        if ("CONFIRMED".equals(status)) {
            reservation.confirm(confirmedAmount.intValue(), rs.getString("comment"), rs.getString("confirmed_by"));
        } else if ("REJECTED".equals(status)) {
            reservation.reject(rs.getString("comment"), rs.getString("confirmed_by"));
        }
        return reservation;
    }
}
