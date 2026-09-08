package com.exe.astratarot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LLMConfig {

    /**
     * Synchronous RestTemplate for non-streaming LLM requests.
     *
     * <table>
     *   <tr><td>Connect timeout</td><td>10 s</td></tr>
     *   <tr><td>Read timeout</td><td>60 s</td></tr>
     * </table>
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }

    /**
     * Long-lived RestTemplate for SSE streaming from the LLM provider.
     *
     * <p>The extended read timeout allows the underlying {@code Buffered-}
     * {@code Reader} to block between chunks without prematurely closing
     * the connection.  Gemini streaming responses rarely exceed 120 s, so
     * 300 s provides a generous safety margin.
     *
     * <table>
     *   <tr><td>Connect timeout</td><td>10 s</td></tr>
     *   <tr><td>Read timeout</td><td>300 s</td></tr>
     * </table>
     */
    @Bean
    public RestTemplate streamingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(300_000);
        return new RestTemplate(factory);
    }
}