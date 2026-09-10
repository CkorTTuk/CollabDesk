package collabdesk.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Canonical locales persisted by the account aggregate and exposed by the API. */
public enum SupportedLocale {
    EN("en"),
    RU("ru"),
    SK("sk");

    private final String tag;

    SupportedLocale(String tag) {
        this.tag = tag;
    }

    @JsonValue
    public String tag() {
        return tag;
    }

    @JsonCreator
    public static SupportedLocale fromTag(String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("locale is required");
        }
        String candidate = tag.strip();
        return Arrays.stream(values())
                .filter(locale -> locale.tag.equals(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported locale: " + candidate
                ));
    }
}
