package collabdesk.auth.security;

import collabdesk.auth.external.ExternalProfileSuggestion;
import collabdesk.user.entity.User;
import collabdesk.user.entity.UserStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;

public final class GitHubOAuth2Principal
        extends DefaultOAuth2User
        implements CollabDeskPrincipal {
    private final Long userId;
    private final String email;
    private final String displayName;
    private final UserStatus status;
    private final boolean emailVerified;
    private final boolean onboardingCompleted;
    @Nullable
    private final ExternalProfileSuggestion externalProfileSuggestion;

    public GitHubOAuth2Principal(
            User user,
            OAuth2User oauth2User,
            @Nullable String suggestedFirstName,
            @Nullable String suggestedLastName,
            @Nullable String suggestedAvatarUrl
    ) {
        super(
                authorities(user, oauth2User),
                oauth2User.getAttributes(),
                "id"
        );
        this.userId = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.status = user.getStatus();
        this.emailVerified = user.isEmailVerified();
        this.onboardingCompleted = user.isOnboardingCompleted();
        this.externalProfileSuggestion = onboardingCompleted
                ? null
                : new ExternalProfileSuggestion(
                        suggestedFirstName,
                        suggestedLastName,
                        suggestedAvatarUrl
                );
    }

    public GitHubOAuth2Principal(User user, GitHubOAuth2Principal principal) {
        this(
                user,
                principal,
                suggestionValue(principal, SuggestionField.FIRST_NAME),
                suggestionValue(principal, SuggestionField.LAST_NAME),
                suggestionValue(principal, SuggestionField.AVATAR_URL)
        );
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public UserStatus getStatus() {
        return status;
    }

    @Override
    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    @Override
    public @Nullable ExternalProfileSuggestion getExternalProfileSuggestion() {
        return externalProfileSuggestion;
    }

    private static Collection<GrantedAuthority> authorities(
            User user,
            OAuth2User oauth2User
    ) {
        ArrayList<GrantedAuthority> authorities = new ArrayList<>(
                oauth2User.getAuthorities()
        );
        for (GrantedAuthority authority : CollabDeskAuthorities.forProfile(
                user.isOnboardingCompleted()
        )) {
            if (!authorities.contains(authority)) {
                authorities.add(authority);
            }
        }
        return authorities;
    }

    @Nullable
    private static String suggestionValue(
            GitHubOAuth2Principal principal,
            SuggestionField field
    ) {
        ExternalProfileSuggestion suggestion = principal.getExternalProfileSuggestion();
        if (suggestion == null) {
            return null;
        }
        return switch (field) {
            case FIRST_NAME -> suggestion.firstName();
            case LAST_NAME -> suggestion.lastName();
            case AVATAR_URL -> suggestion.avatarUrl();
        };
    }

    private enum SuggestionField {
        FIRST_NAME,
        LAST_NAME,
        AVATAR_URL
    }
}
