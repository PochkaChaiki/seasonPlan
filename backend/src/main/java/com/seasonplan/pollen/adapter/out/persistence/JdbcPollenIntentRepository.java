package com.seasonplan.pollen.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.pollen.application.port.PollenIntentRepository;
import com.seasonplan.pollen.domain.*;
import com.seasonplan.shared.adapter.out.persistence.JdbcJson;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/** Pollen-owned integration evidence. No authoritative inventory is maintained here. */
public final class JdbcPollenIntentRepository implements PollenIntentRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JdbcPollenIntentRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    @Override public ConsumptionClaim insertConsumption(ConsumptionIntent value) {
        int inserted = jdbc.update("insert into pollen_consumption_operations(id, season_plan_id, material_revision, intent, state, receipt) values (?, ?, ?, cast(? as jsonb), ?, cast(? as jsonb)) on conflict(id) do nothing",
                value.id(), value.planId(), value.materialRevision(), JdbcJson.write(mapper, value), value.state().name(), value.receipt() == null ? null : JdbcJson.write(mapper, value.receipt()));
        return new ConsumptionClaim(inserted == 1, findConsumption(value.id()).orElseThrow());
    }
    @Override public Optional<ConsumptionIntent> findConsumption(UUID id) {
        return jdbc.query("select * from pollen_consumption_operations where id = ?", (rs, row) -> {
            try {
                var captured = mapper.readValue(rs.getString("intent"), ConsumptionIntent.class);
                return new ConsumptionIntent(captured.id(), captured.planId(), captured.materialRevision(), captured.clientKey(), captured.amount(), captured.actorId(), captured.expectedReservationId(),
                        ConsumptionIntent.State.valueOf(rs.getString("state")), rs.getString("receipt") == null ? null : mapper.readValue(rs.getString("receipt"), ConsumptionIntent.Receipt.class), captured.createdAt());
            } catch (JsonProcessingException error) { throw new IllegalStateException("Invalid consumption evidence", error); }
        }, id).stream().findFirst();
    }
    @Override public void updateConsumption(ConsumptionIntent value) {
        if (jdbc.update("update pollen_consumption_operations set state = ?, receipt = cast(? as jsonb) where id = ?", value.state().name(),
                value.receipt() == null ? null : JdbcJson.write(mapper, value.receipt()), value.id()) != 1) throw new IllegalStateException("consumption intent missing");
    }
    @Override public Optional<UUID> unresolvedConsumption(UUID planId) {
        return jdbc.query("select id from pollen_consumption_operations where season_plan_id = ? and state not in ('APPLIED','DENIED') order by id limit 1",
                (rs, row) -> rs.getObject("id", UUID.class), planId).stream().findFirst();
    }
    @Override public Claim insert(PollenIntent intent) {
        int inserted = jdbc.update("insert into pollen_operations(id, season_plan_id, material_revision, payload, state, created_at) values (?, ?, ?, cast(? as jsonb), ?, ?) on conflict(id) do nothing",
                intent.id(), intent.payload().planId(), intent.payload().materialRevision(), JdbcJson.write(mapper, intent.payload()), intent.state().name(), Timestamp.from(intent.createdAt()));
        return new Claim(inserted == 1, find(intent.id()).orElseThrow());
    }
    @Override public Optional<PollenIntent> find(UUID id) {
        return jdbc.query("select * from pollen_operations where id = ?", this::read, id).stream().findFirst();
    }
    @Override public void update(PollenIntent value) {
        int changed = jdbc.update("update pollen_operations set state = ?, receipt = cast(? as jsonb), denial_field = ? where id = ?",
                value.state().name(), value.receipt() == null ? null : JdbcJson.write(mapper, value.receipt()), value.denialField(), value.id());
        if (changed != 1) throw new IllegalStateException("pollen operation missing");
    }
    @Override public Optional<ReservationBinding> binding(UUID id) {
        return jdbc.query("select * from pollen_reservation_bindings where reservation_id = ?", (rs, row) -> new ReservationBinding(
                rs.getObject("reservation_id", UUID.class), rs.getObject("season_plan_id", UUID.class), rs.getLong("material_revision"), rs.getObject("request_operation_id", UUID.class)), id).stream().findFirst();
    }
    @Override public void bind(ReservationBinding binding) {
        binding.requireRequestEvidence(find(binding.requestOperationId()).orElseThrow());
        jdbc.update("insert into pollen_reservation_bindings(reservation_id, season_plan_id, material_revision, request_operation_id) values (?, ?, ?, ?) on conflict(reservation_id) do nothing",
                binding.reservationId(), binding.planId(), binding.materialRevision(), binding.requestOperationId());
        if (!binding.equals(binding(binding.reservationId()).orElseThrow())) throw new IllegalStateException("reservation binding cannot change");
    }
    @Override public Optional<UUID> unresolvedOperation(UUID planId) {
        return jdbc.query("select id from pollen_operations where season_plan_id = ? and state in ('DISPATCHED','UNKNOWN','RECEIVED') order by created_at, id limit 1",
                (rs, row) -> rs.getObject("id", UUID.class), planId).stream().findFirst();
    }
    private PollenIntent read(ResultSet rs, int row) throws SQLException {
        try {
            return new PollenIntent(rs.getObject("id", UUID.class), mapper.readValue(rs.getString("payload"), PollenIntent.Payload.class),
                    PollenIntent.State.valueOf(rs.getString("state")), rs.getString("receipt") == null ? null : mapper.readValue(rs.getString("receipt"), ReservationReceipt.class),
                    rs.getString("denial_field"), rs.getTimestamp("created_at").toInstant());
        } catch (JsonProcessingException error) { throw new IllegalStateException("Invalid persisted pollen operation", error); }
    }
}
