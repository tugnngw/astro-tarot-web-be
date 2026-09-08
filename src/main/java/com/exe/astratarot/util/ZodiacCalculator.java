package com.exe.astratarot.util;

import java.time.LocalDate;
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
