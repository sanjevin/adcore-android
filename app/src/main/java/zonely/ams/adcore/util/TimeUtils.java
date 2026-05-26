package zonely.ams.adcore.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TimeUtils {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TimeUtils() {
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static String isoUtc(long millis) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis));
    }

    public static String todayKey() {
        return LocalDate.now().format(DATE_FORMAT);
    }

    public static String yesterdayKey() {
        return LocalDate.now().minusDays(1).format(DATE_FORMAT);
    }

    public static long startOfLocalDay(String dateKey) {
        return LocalDate.parse(dateKey, DATE_FORMAT)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    public static long endOfLocalDayExclusive(String dateKey) {
        return LocalDate.parse(dateKey, DATE_FORMAT)
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    public static long todayAt0030() {
        return todayAtLocalTime(0, 30);
    }

    public static long next0030() {
        return nextLocalTime(0, 30);
    }

    public static long todayAtLocalTime(int hour, int minute) {
        LocalDateTime local = LocalDate.now().atTime(LocalTime.of(hour, minute));
        return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long nextLocalTime(int hour, int minute) {
        LocalDate today = LocalDate.now();
        LocalDateTime next = today.atTime(LocalTime.of(hour, minute));
        if (next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() <= now()) {
            next = today.plusDays(1).atTime(LocalTime.of(hour, minute));
        }
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String formatLocalTime(int hour, int minute) {
        return LocalTime.of(hour, minute).toString();
    }

    public static String isoLocalDateFromMillis(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DATE_FORMAT);
    }

    public static List<String> dateKeysBetween(long startMillis, long endMillis) {
        List<String> keys = new ArrayList<>();
        long safeEnd = Math.max(startMillis, endMillis);
        LocalDate start = Instant.ofEpochMilli(startMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate end = Instant.ofEpochMilli(safeEnd)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            keys.add(current.format(DATE_FORMAT));
            current = current.plusDays(1);
        }
        return keys;
    }

    public static String isoUtcNow() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
    }
}
