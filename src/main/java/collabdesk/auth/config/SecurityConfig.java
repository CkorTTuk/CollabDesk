package collabdesk.auth.config;

import collabdesk.auth.security.LocalUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        http
            .authenticationProvider(authenticationProvider)

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
    @Bean
    public AuthenticationProvider authenticationProvider(
            LocalUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }
}
