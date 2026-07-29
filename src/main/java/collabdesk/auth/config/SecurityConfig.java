package collabdesk.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/auth/register"
                                ).permitAll()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/csrf"
                                ).permitAll()

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/auth/login"
                                ).permitAll()

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/auth/logout"
                                ).authenticated()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/auth/me"
                                ).authenticated()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs",
                                        "/v3/api-docs.yaml",
                                        "/v3/api-docs/**",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**"
                                ).authenticated()

                                .anyRequest().authenticated()
                )
                .exceptionHandling(exception ->
                        exception
                                .authenticationEntryPoint((req, resp, e) -> {
                                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                })
                )
                .formLogin(formLogin ->
                        formLogin
                                .loginProcessingUrl("/api/v1/auth/login")
                                .usernameParameter("email")
                                .passwordParameter("password")

                                .successHandler((request, response, authentication) -> {
                                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                })

                                .failureHandler((request, response, exception) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                }).permitAll()
                )


                .logout(logout ->
                        logout
                                .logoutUrl("/api/v1/auth/logout")
                                .logoutSuccessHandler((request, response, authentication) -> {
                                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                })
                );


        return http.build();
    }
}
