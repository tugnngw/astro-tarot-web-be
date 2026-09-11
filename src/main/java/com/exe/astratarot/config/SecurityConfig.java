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

    /** Domain của FE. Khai bằng CORS_ALLOWED_ORIGINS, ngăn cách bằng dấu phẩy. */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

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
                        // Ghi nhận lượt bấm sang sàn liên kết: phần lớn người bấm
                        // mua chưa đăng nhập, bắt họ đăng nhập chỉ để đi mua hộ
                        // mình là cách chắc chắn nhất để mất hoa hồng.
                        .requestMatchers(HttpMethod.POST, "/api/v1/shop/products/*/click").permitAll()
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
                        //
                        // Khung giờ trống và đánh giá cũng công khai: khách phải
                        // xem được Reader rảnh lúc nào và người khác nhận xét ra
                        // sao TRƯỚC khi quyết định đăng ký tài khoản.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/readers",
                                "/api/v1/readers/*",
                                "/api/v1/readers/*/slots",
                                // Mẫu một sao chỉ khớp ĐÚNG một đoạn, nên
                                // /slots/next-available phải khai riêng. Thiếu
                                // dòng này thì endpoint trả 403 dù đã có
                                // @PreAuthorize("permitAll()") — đúng cái bẫy
                                // mà chú thích bên trên đã cảnh báo.
                                "/api/v1/readers/*/slots/next-available",
                                "/api/v1/readers/*/reviews").permitAll()
                        // Ảnh đại diện đã tải lên — hiển thị công khai như mọi
                        // ảnh khác trên trang. Việc tải LÊN vẫn cần đăng nhập
                        // (POST /api/v1/me/avatar).
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // Ảnh đại diện lấy từ CSDL. Công khai như mọi ảnh đại
                        // diện khác — tên và ảnh Reader hiện ở trang danh sách
                        // mà khách chưa đăng nhập cũng xem được.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/avatar").permitAll()
                        // Docker va nginx goi endpoint nay de biet backend con
                        // song. Chi tra UP/DOWN, khong kem chi tiet.
                        .requestMatchers("/actuator/health").permitAll()
                        // PayOS gọi webhook không kèm JWT — chữ ký checksum trong body.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/payos/webhook").permitAll()
                        // Tất cả request khác cần auth
                        .anyRequest().authenticated()
                )
//                .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler))
                // Phân biệt "chưa đăng nhập" với "không đủ quyền".
                //
                // Không khai hai handler này thì Spring Security dùng mặc định
                // trả 403 cho CẢ HAI. Hệ quả không hề nhỏ: giao diện chỉ làm
                // mới token khi gặp 401, nên hễ access token hết hạn là mọi
                // danh sách chết cho tới khi người dùng tự đăng nhập lại — mà
                // thông báo lại ghi "không có quyền", dẫn người ta đi sai
                // hướng hoàn toàn.
                //
                // Đã bắt tận tay: token hết hạn 56 giây, GET /api/v1/me trả
                // 403 với thân rỗng.
                //
                // Thân phản hồi là JSON để tầng gọi đọc được. Trả rỗng thì
                // client.ts rơi vào nhánh "dữ liệu không đọc được", che mất
                // nguyên nhân thật một lần nữa.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write(
                                    "{\"data\":null,\"error\":{\"code\":\"UNAUTHENTICATED\","
                                    + "\"message\":\"Phiên đăng nhập đã hết hạn hoặc chưa đăng nhập.\"}}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write(
                                    "{\"data\":null,\"error\":{\"code\":\"FORBIDDEN\","
                                    + "\"message\":\"Tài khoản của bạn không có quyền dùng chức năng này.\"}}");
                        }))
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        /*
         * Chỉ những domain khai trong CORS_ALLOWED_ORIGINS mới gọi được API này
         * từ trình duyệt.
         *
         * Trước đây để "*". Với Bearer token thì "*" không cho ai đăng nhập hộ
         * được, nhưng nó cho phép mọi trang web dùng trình duyệt của người dùng
         * làm bàn đạp gọi API mình — kể cả những endpoint công khai vốn tốn
         * tiền như gọi AI. Trên VPS thì phải khoá lại đúng domain của FE.
         */
        configuration.setAllowedOriginPatterns(allowedOrigins);
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