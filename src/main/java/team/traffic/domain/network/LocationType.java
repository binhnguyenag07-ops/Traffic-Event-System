package team.traffic.domain.network;

/**
 * Legacy point-of-interest category kept temporarily for source compatibility.
 * It is no longer part of the intersection model.
 *
 * @deprecated model POIs separately from road intersections
 */
@Deprecated
public enum LocationType {
    SCHOOL,
    HOSPITAL,
    BAR,
    RESIDENTIAL,
    COMMERCIAL,
    NORMAL;
}
