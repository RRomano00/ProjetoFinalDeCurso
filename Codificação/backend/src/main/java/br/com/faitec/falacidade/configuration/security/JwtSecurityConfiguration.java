package br.com.faitec.falacidade.configuration.security;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtRequestFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Profile("jwt")
@Configuration
public class JwtSecurityConfiguration {

    private final JwtRequestFilter jwtRequestFilter;

    public JwtSecurityConfiguration(JwtRequestFilter jwtRequestFilter) {
        this.jwtRequestFilter = jwtRequestFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Padrões, e não origens exatas: os túneis HTTPS de teste sorteiam uma
        // URL nova a cada execução. setAllowedOrigins com curinga é incompatível
        // com setAllowCredentials(true); setAllowedOriginPatterns não é.
        cfg.setAllowedOriginPatterns(List.of(
            "http://localhost:4200",
            "http://localhost:4000",
            "http://localhost:4173",
            "https://*.trycloudflare.com",
            "https://*.ngrok-free.app",
            "https://*.ngrok-free.dev",
            "https://*.ngrok.io"
        ));

        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));

        cfg.setAllowedHeaders(List.of("*"));

        cfg.setExposedHeaders(List.of("Authorization", "Location"));

        cfg.setAllowCredentials(true);

        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/authenticate").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/authenticate/mfa",
                    "/api/authenticate/mfa/send-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/user/register").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/user/password-reset/request",
                    "/api/user/password-reset/confirm").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/occurrence").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/occurrence/mine").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/occurrence", "/api/occurrence/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/occurrence/upload-media").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/occurrence/upload-status/**").permitAll()
                .requestMatchers("/api/mfa/**").authenticated()
                .requestMatchers(
                    "/api/occurrence/*/status",
                    "/api/occurrence/progress/**",
                    "/api/occurrence/conclude/**")
                    .hasAnyRole(
                        UserModel.UserRole.EMPLOYEE.name(),
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .requestMatchers(HttpMethod.PUT, "/api/department/*")
                    .hasAnyRole(
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .requestMatchers("/api/department/**", "/api/occurrence/*/forward")
                    .hasAnyRole(
                        UserModel.UserRole.EMPLOYEE.name(),
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .requestMatchers(HttpMethod.POST, "/api/user/employee")
                    .hasAnyRole(
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .requestMatchers(HttpMethod.GET, "/api/user")
                    .hasAnyRole(
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .requestMatchers(HttpMethod.PUT, "/api/user/*/active", "/api/user/*/role")
                    .hasAnyRole(
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .requestMatchers(HttpMethod.DELETE, "/api/user/account").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/user/**")
                    .hasAnyRole(
                        UserModel.UserRole.CITIZEN.name(),
                        UserModel.UserRole.ADMINISTRATOR.name(),
                        UserModel.UserRole.SUPER_ADMIN.name())
                .anyRequest().authenticated()
            )
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
            .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
