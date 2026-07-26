package team.traffic.domain.demand;

import java.util.Locale;
import java.util.regex.Pattern;

/** Open, data-defined trip-purpose identifier. */
public record PurposeId(String value) {
    private static final Pattern VALID = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public PurposeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Purpose id is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Purpose id must contain only uppercase letters, digits and underscores: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
