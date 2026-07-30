package collabdesk.project.role.dto;

import collabdesk.project.role.entity.ProjectPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "A custom workspace role")
public record CreateAccessRoleRequest(
        @NotBlank
        @Size(min = 2, max = 60)
        String name,

        @NotBlank
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String color,

        @NotNull
        @Size(max = 5)
        Set<@NotNull ProjectPermission> permissions
) {
}
