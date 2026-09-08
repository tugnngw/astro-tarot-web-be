package com.exe.astratarot.config;

import com.exe.astratarot.security.JwtAuthenticationFilter;
import com.exe.astratarot.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
/*
 * BẮT BUỘC phải có. Thiếu annotation này thì Spring không dựng proxy cho
 * @PreAuthorize, và MỌI annotation phân quyền trong dự án trở thành lời chú
 * thích: chỉ còn .anyRequest().authenticated() bên dưới chặn, nghĩa là bất kỳ
 * tài khoản nào đã đăng nhập cũng gọi được /api/v1/admin/**.
 *
 * Đã kiểm chứng bằng token của một tài khoản STAFF: trước khi bật, nó đọc được
 * cả danh sách tài khoản lẫn hồ sơ Reader chờ duyệt.
 */
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.exe.astratarot.security.AuthRateLimitFilter authRateLimitFilter;
    private final UserDetailsService userDetailsService;
//    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // <-- Đổi thành STATELESS
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        // Cho phép tất cả OPTIONS requests (preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Cho phép auth endpoints
                        .requestMatchers("/auth/**",
                                "/oauth2/**",
                                "/ping",
                                "/test",
                                "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        // Catalog shop: khách chưa đăng nhập vẫn phải duyệt được
                        // sản phẩm. Giỏ hàng và đơn hàng (/shop/cart, /shop/orders)
                        // KHÔNG nằm trong đây nên vẫn cần đăng nhập.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/shop/categories",
                                "/api/v1/shop/products",
                                "/api/v1/shop/products/**").permitAll()
                        // Danh sách Reader là một trong ba trụ cột của trang, khách
                        // chưa đăng nhập phải xem được. Trước đây hai endpoint này
                        // có @PreAuthorize("permitAll()") nhưng vẫn trả 403, vì
                        // .anyRequest().authenticated() bên dưới chặn từ trước khi
                        // tới annotation.
                        //
                        // Viết rõ hai mẫu thay vì /api/v1/readers/** — dấu ** sẽ nuốt
                        // luôn /api/v1/readers/profile/me và phơi hồ sơ riêng của
                        // Reader ra ngoài. Mẫu một sao chỉ khớp đúng một đoạn đường
                        // dẫn nên /profile/me nằm ngoài.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/readers",
                                "/api/v1/readers/*").permitAll()
                        // Ảnh đại diện đã tải lên — hiển thị công khai như mọi
                        // ảnh khác trên trang. Việc tải LÊN vẫn cần đăng nhập
                        // (POST /api/v1/me/avatar).
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // Tất cả request khác cần auth
                        .anyRequest().authenticated()
                )
//                .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler))
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Cho phép cả localhost và IP
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));  // Cho phép tất cả headers
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));  // Expose Authorization header
        configuration.setAllowCredentials(false);  // Cho phép credentials
        configuration.setMaxAge(3600L);  // Cache preflight 1 giờ

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}