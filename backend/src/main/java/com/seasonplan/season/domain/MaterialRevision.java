package com.seasonplan.season.domain;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import java.util.Objects;
import java.util.UUID;
/** Legacy baseline describes only captured current data, not past decisions. */
public record MaterialRevision(UUID seasonPlanId, MaterialRevisionStamp stamp, MaterialParameters material, ExecutionRuleSnapshot executionRules) {
    public MaterialRevision { Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(stamp); Objects.requireNonNull(material); }
    public void requireConsistentWith(MaterialRevision previous) {
        if (previous == null) {
            if (!((stamp.number() == 1 && stamp.reason() == MaterialRevisionStamp.Reason.CREATE)
                    || (stamp.number() == 0 && stamp.reason() == MaterialRevisionStamp.Reason.LEGACY_BASELINE)))
                throw new IllegalStateException("initial revision must be CREATE 1 or LEGACY_BASELINE 0");
        } else if (stamp.number() == previous.stamp.number()) {
            if (!material.equals(previous.material) || !Objects.equals(executionRules, previous.executionRules))
                throw new IllegalStateException("material cannot change within a revision");
        } else if (stamp.number() != previous.stamp.number() + 1 || stamp.reason() != MaterialRevisionStamp.Reason.EDIT) {
            throw new IllegalStateException("material revision must advance monotonically by one");
        }
    }
}
