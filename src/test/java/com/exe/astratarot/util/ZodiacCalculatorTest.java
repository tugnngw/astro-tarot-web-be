package com.exe.astratarot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ZodiacCalculator.
 *
 * Coverage:
 * - All 12 zodiac signs with representative dates
 * - Boundary dates for each sign
 * - Leap year handling
 * - Element derivation for all signs
 * - Modality derivation for all signs
 * - Null input validation
 * - Invalid sign validation
 */
@DisplayName("ZodiacCalculator Tests")
class ZodiacCalculatorTest {

    // ========================
    // SUN SIGN CALCULATION
    // ========================

    @ParameterizedTest
    @CsvSource({
            "1990-03-21, Aries",           // Start of Aries
            "1985-04-19, Aries",           // End of Aries
            "1992-04-20, Taurus",          // Start of Taurus
            "1988-05-20, Taurus",          // End of Taurus
            "1995-05-21, Gemini",          // Start of Gemini
            "1999-06-20, Gemini",          // End of Gemini
            "2000-06-21, Cancer",          // Start of Cancer
            "1975-07-22, Cancer",          // End of Cancer
            "1980-07-23, Leo",             // Start of Leo
            "1982-08-22, Leo",             // End of Leo
            "1987-08-23, Virgo",           // Start of Virgo
            "1991-09-22, Virgo",           // End of Virgo
            "1993-09-23, Libra",           // Start of Libra
            "1998-10-22, Libra",           // End of Libra
            "2001-10-23, Scorpio",         // Start of Scorpio
            "2002-11-21, Scorpio",         // End of Scorpio
            "1989-11-22, Sagittarius",     // Start of Sagittarius
            "1994-12-21, Sagittarius",     // End of Sagittarius
            "2003-12-22, Capricorn",       // Start of Capricorn
            "1996-01-19, Capricorn",       // End of Capricorn
            "1997-01-20, Aquarius",        // Start of Aquarius
            "2004-02-18, Aquarius",        // End of Aquarius
            "2005-02-19, Pisces",          // Start of Pisces
            "2006-03-20, Pisces"           // End of Pisces
    })
    @DisplayName("Should calculate sun sign for representative dates")
    void testCalculateSunSignRepresentativeDates(String date, String expectedSign) {
        LocalDate birthDate = LocalDate.parse(date);
        String sunSign = ZodiacCalculator.calculateSunSign(birthDate);
        assertEquals(expectedSign, sunSign, "Failed for date: " + date);
    }

    @Test
    @DisplayName("Should handle leap year (Feb 29)")
    void testCalculateSunSignLeapYear() {
        // Feb 29 is Pisces (Feb 19 - Mar 20)
        LocalDate leapYearDate = LocalDate.of(2020, 2, 29);
        String sunSign = ZodiacCalculator.calculateSunSign(leapYearDate);
        assertEquals("Pisces", sunSign);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null birthDate")
    void testCalculateSunSignNullInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateSunSign(null),
                "Should throw IllegalArgumentException for null birthDate");
    }

    // ========================
    // ELEMENT CALCULATION
    // ========================

    @ParameterizedTest
    @CsvSource({
            "Aries, Fire",
            "Leo, Fire",
            "Sagittarius, Fire",
            "Taurus, Earth",
            "Virgo, Earth",
            "Capricorn, Earth",
            "Gemini, Air",
            "Libra, Air",
            "Aquarius, Air",
            "Cancer, Water",
            "Scorpio, Water",
            "Pisces, Water"
    })
    @DisplayName("Should calculate element for all zodiac signs")
    void testCalculateElementAllSigns(String sunSign, String expectedElement) {
        String element = ZodiacCalculator.calculateElement(sunSign);
        assertEquals(expectedElement, element, "Failed for sign: " + sunSign);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null sun sign")
    void testCalculateElementNullInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateElement(null),
                "Should throw IllegalArgumentException for null sun sign");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for blank sun sign")
    void testCalculateElementBlankInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateElement("   "),
                "Should throw IllegalArgumentException for blank sun sign");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for unknown sun sign")
    void testCalculateElementUnknownSign() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateElement("InvalidSign"),
                "Should throw IllegalArgumentException for unknown sun sign");
    }

    // ========================
    // MODALITY CALCULATION
    // ========================

    @ParameterizedTest
    @CsvSource({
            "Aries, Cardinal",
            "Cancer, Cardinal",
            "Libra, Cardinal",
            "Capricorn, Cardinal",
            "Taurus, Fixed",
            "Leo, Fixed",
            "Scorpio, Fixed",
            "Aquarius, Fixed",
            "Gemini, Mutable",
            "Virgo, Mutable",
            "Sagittarius, Mutable",
            "Pisces, Mutable"
    })
    @DisplayName("Should calculate modality for all zodiac signs")
    void testCalculateModalityAllSigns(String sunSign, String expectedModality) {
        String modality = ZodiacCalculator.calculateModality(sunSign);
        assertEquals(expectedModality, modality, "Failed for sign: " + sunSign);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null sun sign")
    void testCalculateModalityNullInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateModality(null),
                "Should throw IllegalArgumentException for null sun sign");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for blank sun sign")
    void testCalculateModalityBlankInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateModality("   "),
                "Should throw IllegalArgumentException for blank sun sign");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for unknown sun sign")
    void testCalculateModalityUnknownSign() {
        assertThrows(IllegalArgumentException.class,
                () -> ZodiacCalculator.calculateModality("UnknownSign"),
                "Should throw IllegalArgumentException for unknown sun sign");
    }

    // ========================
    // INTEGRATION TESTS
    // ========================

    @Test
    @DisplayName("Should correctly chain calculations: date → sign → element → modality")
    void testIntegrationChain() {
        LocalDate birthDate = LocalDate.of(1990, 3, 21);

        String sunSign = ZodiacCalculator.calculateSunSign(birthDate);
        assertEquals("Aries", sunSign);

        String element = ZodiacCalculator.calculateElement(sunSign);
        assertEquals("Fire", element);

        String modality = ZodiacCalculator.calculateModality(sunSign);
        assertEquals("Cardinal", modality);
    }

    @Test
    @DisplayName("Should handle year boundary: Dec 22 (Capricorn)")
    void testYearBoundaryStartCapricorn() {
        LocalDate capricornStart = LocalDate.of(1999, 12, 22);
        String sunSign = ZodiacCalculator.calculateSunSign(capricornStart);
        assertEquals("Capricorn", sunSign);
    }

    @Test
    @DisplayName("Should handle year boundary: Jan 19 (Capricorn end)")
    void testYearBoundaryEndCapricorn() {
        LocalDate capricornEnd = LocalDate.of(2000, 1, 19);
        String sunSign = ZodiacCalculator.calculateSunSign(capricornEnd);
        assertEquals("Capricorn", sunSign);
    }

    @Test
    @DisplayName("Should transition correctly: Jan 20 (Aquarius start)")
    void testYearBoundaryAquariusStart() {
        LocalDate aquariusStart = LocalDate.of(2000, 1, 20);
        String sunSign = ZodiacCalculator.calculateSunSign(aquariusStart);
        assertEquals("Aquarius", sunSign);
    }
}
