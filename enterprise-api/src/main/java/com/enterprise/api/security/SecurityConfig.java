package com.enterprise.api.security;

import java.util.List;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.config.properties.CorsProperties;
import com.enterprise.api.config.properties.SecurityHeadersProperties;
import com.enterprise.api.service.IRateLimitService;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.util.CorrelationId;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security for the whole API.
 * <ol>
 *   <li>CORS (whitelist), CSRF (cookie + header, Spring Security's SPA setup)</li>
 *   <li>Bearer JWT (HS256), then {@link SessionValidationFilter}, {@link PasswordChangeRequiredFilter}
 *       and {@link RateLimitFilter}</li>
 *   <li>URL rules here, permissions with {@code @PreAuthorize("hasAuthority('PERM_...')")}</li>
 * </ol>
 * In the {@code dev} profile a separate chain serves Swagger UI with a CSP that lets it load.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] HEALTH = {"/actuator/health", "/actuator/health/**"};
    static final String[] API_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
    static final String SWAGGER_CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'";

    private static final int BCRYPT_STRENGTH = 12;

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final ApiErrorResponseWriter errorWriter;
    private final SecurityHeadersProperties headersProperties;

    public SecurityConfig(JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler, ApiErrorResponseWriter errorWriter,
                          SecurityHeadersProperties headersProperties) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.errorWriter = errorWriter;
        this.headersProperties = headersProperties;
    }

    /** dev only: read-only Swagger UI and OpenAPI JSON. Calls made from Swagger go through the API chain. */
    @Bean
    @Order(1)
    @Profile("dev")
    public SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(API_DOCS);
        applyCommon(http, SWAGGER_CSP);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, API_DOCS).permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                      JwtAuthenticationConverter jwtAuthenticationConverter,
                                                      CookieCsrfTokenRepository csrfTokenRepository,
                                                      ISessionValidationService sessionValidationService,
                                                      IRateLimitService rateLimitService,
                                                      ClientIpResolver clientIpResolver) throws Exception {
        RequestMatcher health = healthMatcher();
        applyCommon(http, API_CSP);
        http
                .cors(cors -> { })
                // spa(): XSRF-TOKEN cookie readable by JS, raw value accepted in X-XSRF-TOKEN.
                // Then our repository, which only adds the cookie attributes.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository)
                        .withObjectPostProcessor(requireCsrfForBearerRequests()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(new SessionValidationFilter(sessionValidationService, errorWriter),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new PasswordChangeRequiredFilter(errorWriter), SessionValidationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimitService, clientIpResolver, errorWriter, health),
                        PasswordChangeRequiredFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(health).permitAll()
                        .requestMatchers(AuthPaths.PUBLIC).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /** {@code XSRF-TOKEN} cookie, {@code X-XSRF-TOKEN} header. Also used by {@code GET /api/v1/auth/csrf}. */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(AuthProperties authProperties) {
        AuthProperties.Csrf csrf = authProperties.csrf();
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(csrf.cookieSecure()).sameSite("Strict");
            if (csrf.cookieDomain() != null && !csrf.cookieDomain().isBlank()) {
                cookie.domain(csrf.cookieDomain().strip());
            }
        });
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, "X-XSRF-TOKEN", CorrelationId.HEADER));
        configuration.setExposedHeaders(List.of(CorrelationId.HEADER, HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    private void applyCommon(HttpSecurity http, String contentSecurityPolicy) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy));
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER));
                    headers.permissionsPolicyHeader(policy -> policy.policy(headersProperties.permissionsPolicy()));
                    if (headersProperties.hstsEnabled()) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(headersProperties.hstsMaxAge().toSeconds()));
                    } else {
                        headers.httpStrictTransportSecurity(hsts -> hsts.disable());
                    }
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
    }

    private static RequestMatcher healthMatcher() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(paths.matcher(HttpMethod.GET, HEALTH[0]), paths.matcher(HttpMethod.GET, HEALTH[1]));
    }

    /**
     * The resource server configurer exempts requests that carry a bearer token from
     * CSRF. This API requires the CSRF header on every state-changing request, so the
     * filter's matcher is reset to the default (all methods except GET, HEAD, TRACE,
     * OPTIONS). Post-processing runs after the configurer has set its matcher.
     */
    private static ObjectPostProcessor<CsrfFilter> requireCsrfForBearerRequests() {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends CsrfFilter> O postProcess(O filter) {
                filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                return filter;
            }
        };
    }

    /** Ignores the Authorization header on the anonymous auth endpoints. */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> AuthPaths.PUBLIC.matches(request) ? null : delegate.resolve(request);
    }
}
