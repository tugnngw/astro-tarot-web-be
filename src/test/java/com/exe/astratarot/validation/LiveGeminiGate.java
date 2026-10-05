package com.exe.astratarot.validation;

/**
 * Cổng bật live Gemini: tránh đỏ CI/local khi GEMINI_LIVE_TEST=1 nhưng key là placeholder.
 */
final class LiveGeminiGate {

    private LiveGeminiGate() {}

    static boolean enabled() {
        if (!"1".equals(System.getenv("GEMINI_LIVE_TEST"))) {
            return false;
        }
        return isUsableApiKey(System.getenv("GEMINI_API_KEY"));
    }

    static boolean isUsableApiKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String k = key.trim().toLowerCase();
        if (k.startsWith("dummy") || k.contains("your_") || k.contains("changeme")
                || k.equals("test") || k.equals("xxx") || k.equals("placeholder")) {
            return false;
        }
        // Gemini keys thường bắt đầu bằng AIza
        return key.trim().length() >= 20;
    }
}
