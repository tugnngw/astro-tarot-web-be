package com.exe.astratarot.validation;

/**
 * Cổng bật live LLM probe: Gemini hoặc OpenAI-compatible (Groq/Cerebras).
 * Tránh đỏ CI khi GEMINI_LIVE_TEST=1 nhưng key là placeholder.
 */
final class LiveGeminiGate {

    private LiveGeminiGate() {}

    static boolean enabled() {
        if (!"1".equals(System.getenv("GEMINI_LIVE_TEST"))
                && !"1".equals(System.getenv("AI_QUALITY_PROBE"))) {
            return false;
        }
        return hasUsableGemini() || hasUsableOpenAiCompatible();
    }

    static boolean hasUsableGemini() {
        return isUsableApiKey(System.getenv("GEMINI_API_KEY"));
    }

    static boolean hasUsableOpenAiCompatible() {
        String key = firstNonBlank(
                System.getenv("LLM_OPENAI_API_KEY"),
                System.getenv("GROQ_API_KEY"),
                System.getenv("CEREBRAS_API_KEY"));
        return isUsableApiKey(key);
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
        return key.trim().length() >= 20;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
