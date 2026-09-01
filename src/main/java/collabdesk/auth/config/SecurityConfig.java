package collabdesk.auth.config;

import collabdesk.auth.google.CollabDeskOidcUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CollabDeskOidcUserService oidcUserService,
            @Value("${app.frontend-url}") String configuredFrontendUrl
    ) throws Exception {
        String frontendUrl = configuredFrontendUrl.endsWith("/")
                ? configuredFrontendUrl.substring(0, configuredFrontendUrl.length() - 1)
                : configuredFrontendUrl;

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
                                        HttpMethod.GET,
                                        "/oauth2/**",
                                        "/login/oauth2/**"
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

                .oauth2Login(oauth2 ->
                        oauth2
                                .userInfoEndpoint(userInfo ->
                                        userInfo.oidcUserService(oidcUserService)
                                )
                                .successHandler((request, response, authentication) ->
                                        response.sendRedirect(frontendUrl)
                                )
                                .failureHandler((request, response, exception) ->
                                        response.sendRedirect(frontendUrl + "/?oauth=failed")
                                )
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
