package collabdesk.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "RFC 9457 error returned by the REST API")
public record ApiProblemResponse(
        @Schema(example = "about:blank")
        String type,

        @Schema(example = "Validation failed")
        String title,

        @Schema(example = "400")
        Integer status,

        @Schema(example = "Request validation failed")
        String detail,

        @Schema(example = "/api/v1/workspaces")
        String instance,

        @Schema(
                description = "Validation messages keyed by request field",
                example = "{\"name\":\"must not be blank\"}"
        )
        Map<String, String> errors
) {
}
