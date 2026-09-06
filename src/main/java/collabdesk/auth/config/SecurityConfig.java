package collabdesk.auth.config;

import collabdesk.auth.google.CollabDeskOidcUserService;
import collabdesk.auth.security.CollabDeskAuthorities;
import collabdesk.auth.github.GitHubOAuth2UserService;
import collabdesk.auth.security.CollabDeskOAuth2FailureHandler;
import collabdesk.auth.security.CollabDeskOAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Defines authentication mechanisms, public endpoints, authorization rules,
 * logout behavior and persistence of security contexts in HTTP sessions.
 */
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CollabDeskOidcUserService oidcUserService,
            GitHubOAuth2UserService gitHubOAuth2UserService,
            CollabDeskOAuth2SuccessHandler oauth2SuccessHandler,
            CollabDeskOAuth2FailureHandler oauth2FailureHandler,
            SecurityContextRepository securityContextRepository
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/auth/register",
                                        "/api/v1/auth/email-verification/confirm",
                                        "/api/v1/auth/email-verification/resend"
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
                                        "/api/v1/account/onboarding/**"
                                ).authenticated()

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs",
                                        "/v3/api-docs.yaml",
                                        "/v3/api-docs/**",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**"
                                ).authenticated()

                                .requestMatchers("/api/v1/**")
                                .hasAuthority(CollabDeskAuthorities.PROFILE_COMPLETE)

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
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )

                .oauth2Login(oauth2 ->
                        oauth2
                                .userInfoEndpoint(userInfo ->
                                        userInfo
                                                .oidcUserService(oidcUserService)
                                                .userService(gitHubOAuth2UserService)
                                )
                                .successHandler(oauth2SuccessHandler)
                                .failureHandler(oauth2FailureHandler)
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

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }
}
