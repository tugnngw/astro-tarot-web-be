package com.exe.astratarot.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for calculating deterministic zodiac metadata from birth date.
 *
 * Provides sun sign, element, and modality derivation using static date-range mappings.
 * No external dependencies. Thread-safe, deterministic, and fast (simple Map lookups).
 *
 * MVP Scope: Deterministic metadata only. No Swiss Ephemeris or external calculations.
 * Suitable for enriching AI prompts with basic astrological context.
 *
 * Example Usage:
 * <pre>
 *   LocalDate birthDate = LocalDate.of(1990, 3, 21);
 *   String sunSign = ZodiacCalculator.calculateSunSign(birthDate);        // "Aries"
 *   String element = ZodiacCalculator.calculateElement(sunSign);          // "Fire"
 *   String modality = ZodiacCalculator.calculateModality(sunSign);        // "Cardinal"
 * </pre>
 */
public class ZodiacCalculator {

    // Zodiac date ranges (inclusive on start, exclusive on end of next sign)
    private static final Map<String, MonthDayRange> ZODIAC_DATES = new HashMap<>();

    static {
        ZODIAC_DATES.put("Capricorn", new MonthDayRange(12, 22, 1, 19));
        ZODIAC_DATES.put("Aquarius", new MonthDayRange(1, 20, 2, 18));
        ZODIAC_DATES.put("Pisces", new MonthDayRange(2, 19, 3, 20));
        ZODIAC_DATES.put("Aries", new MonthDayRange(3, 21, 4, 19));
        ZODIAC_DATES.put("Taurus", new MonthDayRange(4, 20, 5, 20));
        ZODIAC_DATES.put("Gemini", new MonthDayRange(5, 21, 6, 20));
        ZODIAC_DATES.put("Cancer", new MonthDayRange(6, 21, 7, 22));
        ZODIAC_DATES.put("Leo", new MonthDayRange(7, 23, 8, 22));
        ZODIAC_DATES.put("Virgo", new MonthDayRange(8, 23, 9, 22));
        ZODIAC_DATES.put("Libra", new MonthDayRange(9, 23, 10, 22));
        ZODIAC_DATES.put("Scorpio", new MonthDayRange(10, 23, 11, 21));
        ZODIAC_DATES.put("Sagittarius", new MonthDayRange(11, 22, 12, 21));
    }

    // Sign to Element mapping
    private static final Map<String, String> SIGN_TO_ELEMENT = new HashMap<>();

    static {
        // Fire signs
        SIGN_TO_ELEMENT.put("Aries", "Fire");
        SIGN_TO_ELEMENT.put("Leo", "Fire");
        SIGN_TO_ELEMENT.put("Sagittarius", "Fire");

        // Earth signs
        SIGN_TO_ELEMENT.put("Taurus", "Earth");
        SIGN_TO_ELEMENT.put("Virgo", "Earth");
        SIGN_TO_ELEMENT.put("Capricorn", "Earth");

        // Air signs
        SIGN_TO_ELEMENT.put("Gemini", "Air");
        SIGN_TO_ELEMENT.put("Libra", "Air");
        SIGN_TO_ELEMENT.put("Aquarius", "Air");

        // Water signs
        SIGN_TO_ELEMENT.put("Cancer", "Water");
        SIGN_TO_ELEMENT.put("Scorpio", "Water");
        SIGN_TO_ELEMENT.put("Pisces", "Water");
    }

    // Sign to Modality mapping
    private static final Map<String, String> SIGN_TO_MODALITY = new HashMap<>();

    static {
        // Cardinal signs (initiators)
        SIGN_TO_MODALITY.put("Aries", "Cardinal");
        SIGN_TO_MODALITY.put("Cancer", "Cardinal");
        SIGN_TO_MODALITY.put("Libra", "Cardinal");
        SIGN_TO_MODALITY.put("Capricorn", "Cardinal");

        // Fixed signs (stable)
        SIGN_TO_MODALITY.put("Taurus", "Fixed");
        SIGN_TO_MODALITY.put("Leo", "Fixed");
        SIGN_TO_MODALITY.put("Scorpio", "Fixed");
        SIGN_TO_MODALITY.put("Aquarius", "Fixed");

        // Mutable signs (adaptive)
        SIGN_TO_MODALITY.put("Gemini", "Mutable");
        SIGN_TO_MODALITY.put("Virgo", "Mutable");
        SIGN_TO_MODALITY.put("Sagittarius", "Mutable");
        SIGN_TO_MODALITY.put("Pisces", "Mutable");
    }

    /**
     * Calculates sun sign from birth date.
     *
     * @param birthDate the birth date (required, non-null)
     * @return the sun sign (e.g., "Aries", "Taurus", "Gemini")
     * @throws IllegalArgumentException if birthDate is null
     */
    public static String calculateSunSign(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date cannot be null");
        }

        MonthDay monthDay = MonthDay.from(birthDate);

        for (Map.Entry<String, MonthDayRange> entry : ZODIAC_DATES.entrySet()) {
            if (entry.getValue().contains(monthDay)) {
                return entry.getKey();
            }
        }

        // Should not reach here if date ranges are complete
        throw new IllegalArgumentException("Unable to determine sun sign for date: " + birthDate);
    }

    /**
     * Calculates element from sun sign.
     *
     * @param sunSign the sun sign (required, non-null)
     * @return the element ("Fire", "Earth", "Air", or "Water")
     * @throws IllegalArgumentException if sunSign is null or unknown
     */
    public static String calculateElement(String sunSign) {
        if (sunSign == null || sunSign.isBlank()) {
            throw new IllegalArgumentException("Sun sign cannot be null or blank");
        }

        String element = SIGN_TO_ELEMENT.get(sunSign);
        if (element == null) {
            throw new IllegalArgumentException("Unknown sun sign: " + sunSign);
        }

        return element;
    }

    /**
     * Calculates modality from sun sign.
     *
     * @param sunSign the sun sign (required, non-null)
     * @return the modality ("Cardinal", "Fixed", or "Mutable")
     * @throws IllegalArgumentException if sunSign is null or unknown
     */
    public static String calculateModality(String sunSign) {
        if (sunSign == null || sunSign.isBlank()) {
            throw new IllegalArgumentException("Sun sign cannot be null or blank");
        }

        String modality = SIGN_TO_MODALITY.get(sunSign);
        if (modality == null) {
            throw new IllegalArgumentException("Unknown sun sign: " + sunSign);
        }

        return modality;
    }

    /**
     * Tên tiếng Việt của cung (tránh model lẫn "Cancer (Gemini)").
     */
    public static String vietnameseSignName(String sunSign) {
        if (sunSign == null || sunSign.isBlank()) {
            throw new IllegalArgumentException("Sun sign cannot be null or blank");
        }
        String vi = SIGN_TO_VI.get(sunSign);
        if (vi == null) {
            throw new IllegalArgumentException("Unknown sun sign: " + sunSign);
        }
        return vi;
    }

    /**
     * Ước lượng cung Mặt Trăng từ ngày (+ giờ nếu có).
     *
     * <p>Dùng kinh độ trung bình của Mặt Trăng (mean lunar longitude) —
     * đủ ổn cho tư vấn chat; không thay Swiss Ephemeris khi cần độ chính xác
     * nhà/độ. Kết quả gắn nhãn "approximate" ở tầng context nếu cần.
     */
    public static String approximateMoonSign(LocalDate birthDate, LocalTime birthTime) {
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date cannot be null");
        }
        LocalTime time = birthTime != null ? birthTime : LocalTime.NOON;
        double jd = toJulianDay(birthDate, time);
        double d = jd - 2451545.0; // days since J2000.0
        // Meeus-style mean longitude of the Moon (degrees)
        double L = 218.3164477 + 13.17639648 * d;
        L = L % 360.0;
        if (L < 0) {
            L += 360.0;
        }
        int idx = (int) (L / 30.0);
        if (idx < 0) {
            idx = 0;
        }
        if (idx > 11) {
            idx = 11;
        }
        return ZODIAC_ORDER[idx];
    }

    /** Con giáp theo năm âm lịch gần đúng (năm dương − 4) % 12. */
    public static String approximateChineseZodiac(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date cannot be null");
        }
        // Đủ cho prompt; Tết Âm lịch không tính chi tiết.
        int idx = Math.floorMod(birthDate.getYear() - 4, 12);
        return CHINESE_ZODIAC[idx];
    }

    private static final String[] ZODIAC_ORDER = {
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    };

    private static final String[] CHINESE_ZODIAC = {
            "Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake",
            "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig"
    };

    private static final Map<String, String> SIGN_TO_VI = new HashMap<>();

    static {
        SIGN_TO_VI.put("Aries", "Bạch Dương");
        SIGN_TO_VI.put("Taurus", "Kim Ngưu");
        SIGN_TO_VI.put("Gemini", "Song Tử");
        SIGN_TO_VI.put("Cancer", "Cự Giải");
        SIGN_TO_VI.put("Leo", "Sư Tử");
        SIGN_TO_VI.put("Virgo", "Xử Nữ");
        SIGN_TO_VI.put("Libra", "Thiên Bình");
        SIGN_TO_VI.put("Scorpio", "Thiên Yết");
        SIGN_TO_VI.put("Sagittarius", "Nhân Mã");
        SIGN_TO_VI.put("Capricorn", "Ma Kết");
        SIGN_TO_VI.put("Aquarius", "Bảo Bình");
        SIGN_TO_VI.put("Pisces", "Song Ngư");
    }

    private static double toJulianDay(LocalDate date, LocalTime time) {
        int y = date.getYear();
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        double dayFrac = time.toSecondOfDay() / 86400.0;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int A = y / 100;
        int B = 2 - A + A / 4;
        return Math.floor(365.25 * (y + 4716))
                + Math.floor(30.6001 * (m + 1))
                + d + dayFrac + B - 1524.5;
    }

    /**
     * Internal helper: Date range for zodiac signs.
     * Handles year wrapping (e.g., Capricorn from Dec 22 to Jan 19).
     */
    private static class MonthDayRange {
        private final MonthDay start;
        private final MonthDay end;
        private final boolean wrapsYear;

        MonthDayRange(int startMonth, int startDay, int endMonth, int endDay) {
            this.start = MonthDay.of(startMonth, startDay);
            this.end = MonthDay.of(endMonth, endDay);
            this.wrapsYear = startMonth > endMonth;
        }

        boolean contains(MonthDay monthDay) {
            if (wrapsYear) {
                // e.g., Capricorn (Dec 22 to Jan 19)
                return monthDay.compareTo(start) >= 0 || monthDay.compareTo(end) <= 0;
            } else {
                // e.g., Aries (Mar 21 to Apr 19)
                return monthDay.compareTo(start) >= 0 && monthDay.compareTo(end) <= 0;
            }
        }
    }
}
