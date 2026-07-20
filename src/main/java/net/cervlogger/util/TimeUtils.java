package net.cervlogger.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time parsing/formatting helpers, matching CervLogger's "t:2w5d3h30m10s" syntax
 * and its "x.xx/h ago" output style.
 */
public final class TimeUtils {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)([wdhms])");

    private TimeUtils() {
    }

    public static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Parses a CervLogger-style time string like "1h30m", "2w5d", "30s" into seconds.
     *
     * @throws IllegalArgumentException if nothing could be parsed.
     */
    public static long parseSeconds(String input) {
        String value = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = TIME_PATTERN.matcher(value);
        long total = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            double amount = Double.parseDouble(matcher.group(1));
            switch (matcher.group(2)) {
                case "w" -> total += (long) (amount * 7L * 24L * 3600L);
                case "d" -> total += (long) (amount * 24L * 3600L);
                case "h" -> total += (long) (amount * 3600L);
                case "m" -> total += (long) (amount * 60L);
                case "s" -> total += (long) amount;
                default -> {
                }
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Invalid time value: " + input);
        }
        return total;
    }

    /**
     * Formats an epoch-seconds timestamp as CervLogger-style relative time,
     * e.g. "2.35/h ago", "12.50/m ago", "3.02/d ago".
     */
    public static String formatAgo(long epochSeconds) {
        long diff = Math.max(0, now() - epochSeconds);
        if (diff < 3600) {
            return String.format(Locale.ROOT, "%.2f/m", diff / 60.0D);
        }
        if (diff < 86400) {
            return String.format(Locale.ROOT, "%.2f/h", diff / 3600.0D);
        }
        return String.format(Locale.ROOT, "%.2f/d", diff / 86400.0D);
    }
}
