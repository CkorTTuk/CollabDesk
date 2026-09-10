package collabdesk.account;

import collabdesk.user.entity.SupportedLocale;
import jakarta.validation.constraints.NotNull;

public record UpdateLocaleRequest(@NotNull SupportedLocale locale) {
}
