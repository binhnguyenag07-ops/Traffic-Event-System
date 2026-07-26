package team.traffic.domain.network;

/**
 * What a patch of ground is, where it is not a building or a road.
 *
 * <p>Roads and blocks are not the whole of a city. District 1 has a river down
 * one side, a canal through it, and several large parks; drawn as undivided
 * background they read as empty land, which is the one thing they are not.</p>
 *
 * <p>{@link #NORMAL} is the default rather than a mapped value: it is the
 * ordinary urban fabric that fills whatever no other polygon claims. That
 * matters for honesty. OpenStreetMap has only about a fifth of District 1's
 * buildings, mostly the notable ones, so a great deal of genuinely built-up
 * ground has nothing mapped on it. Calling that {@code NORMAL} says "ordinary
 * city, nothing recorded here" without inventing buildings that do not exist.</p>
 */
public enum LandCoverType {
    /** Ordinary urban fabric. The default where nothing else is mapped. */
    NORMAL,
    PARK,
    WATER,
    GRASS,
    FOREST,
    COMMERCIAL,
    RESIDENTIAL,
    INDUSTRIAL,
    CONSTRUCTION,
    RELIGIOUS,
    RECREATION,
    /** Mapped as something, but nothing this model distinguishes. */
    OTHER
}
