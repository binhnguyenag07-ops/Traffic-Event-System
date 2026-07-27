package team.traffic.domain.network;

/** Network-wide fallback used when a map has no explicit U-turn rule. */
public enum UTurnNetworkPolicy {
    EXPLICIT_ONLY,
    EXPLICIT_OR_CONSERVATIVE_LOCAL_INFERENCE
}
