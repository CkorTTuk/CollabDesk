package collabdesk.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI collabDeskOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CollabDesk API")
                                .description(
                                        "Session-based REST API for collaborative workspace"
                                )
                                .version("v1")
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "sessionCookie",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.COOKIE)
                                                .name("JSESSIONID")
                                )
                                .addParameters(
                                        "csrfToken",
                                        new HeaderParameter()
                                                .name("X-CSRF-TOKEN")
                                                .required(true)
                                                .description(
                                                        "Token returned by GET /csrf"
                                                )
                                )
                )
                .paths(new Paths()
                        .addPathItem(
                                "/api/v1/auth/login",
                                new PathItem().post(loginOperation())
                        )
                        .addPathItem(
                                "/api/v1/auth/logout",
                                new PathItem().post(logoutOperation())
                        ));
    }

    private Operation loginOperation() {
        ObjectSchema loginForm = new ObjectSchema();
        loginForm
                .addProperty(
                        "email",
                        new StringSchema()
                                .format("email")
                                .example("member@example.com")
                )
                .addProperty(
                        "password",
                        new StringSchema()
                                .format("password")
                                .example("password123")
                );
        loginForm.setRequired(List.of("email", "password"));

        return new Operation()
                .tags(List.of("Authentication"))
                .summary("Log in")
                .description(
                        "Authenticates form credentials and creates an HTTP session"
                )
                .addParametersItem(
                        new Parameter().$ref("#/components/parameters/csrfToken")
                )
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content().addMediaType(
                                org.springframework.http.MediaType
                                        .APPLICATION_FORM_URLENCODED_VALUE,
                                new io.swagger.v3.oas.models.media.MediaType()
                                        .schema(loginForm)
                        )))
                .responses(new ApiResponses()
                        .addApiResponse(
                                "204",
                                new ApiResponse().description("Login successful")
                        )
                        .addApiResponse(
                                "401",
                                new ApiResponse().description("Invalid credentials")
                        )
                        .addApiResponse(
                                "403",
                                new ApiResponse().description("CSRF token missing or invalid")
                        ));
    }

    private Operation logoutOperation() {
        return new Operation()
                .tags(List.of("Authentication"))
                .summary("Log out")
                .description("Invalidates the current HTTP session")
                .addSecurityItem(
                        new SecurityRequirement().addList("sessionCookie")
                )
                .addParametersItem(
                        new Parameter().$ref("#/components/parameters/csrfToken")
                )
                .responses(new ApiResponses()
                        .addApiResponse(
                                "204",
                                new ApiResponse().description("Logout successful")
                        )
                        .addApiResponse(
                                "401",
                                new ApiResponse().description("Authentication required")
                        )
                        .addApiResponse(
                                "403",
                                new ApiResponse().description("CSRF token missing or invalid")
                        ));
    }
}
