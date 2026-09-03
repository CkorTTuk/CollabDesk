package collabdesk.auth.github;

import collabdesk.auth.entity.AuthProvider;
import collabdesk.auth.external.ExternalAccountService;
import collabdesk.auth.external.ExternalIdentity;
import collabdesk.auth.security.GitHubOAuth2Principal;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class GitHubOAuth2UserService
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final GitHubEmailClient emailClient;
    private final ExternalAccountService externalAccountService;

    @Autowired
    public GitHubOAuth2UserService(
            GitHubEmailClient emailClient,
            ExternalAccountService externalAccountService
    ) {
        this(new DefaultOAuth2UserService(), emailClient, externalAccountService);
    }

    GitHubOAuth2UserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
            GitHubEmailClient emailClient,
            ExternalAccountService externalAccountService
    ) {
        this.delegate = delegate;
        this.emailClient = emailClient;
        this.externalAccountService = externalAccountService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request)
            throws OAuth2AuthenticationException {
        String registrationId = request.getClientRegistration().getRegistrationId();
        if (!"github".equals(registrationId)) {
            throw authenticationFailure("unsupported_oauth2_provider");
        }

        OAuth2User oauth2User = delegate.loadUser(request);
        String providerSubject = requireGitHubId(oauth2User.getAttribute("id"));
        String login = optionalString(oauth2User.getAttribute("login"));
        String providerName = optionalString(oauth2User.getAttribute("name"));
        String avatarUrl = optionalString(oauth2User.getAttribute("avatar_url"));
        String verifiedEmail = emailClient.findPrimaryVerifiedEmail(
                request.getAccessToken().getTokenValue()
        );
        SuggestedName suggestedName = suggestedName(providerName, login);

        var account = externalAccountService.findOrCreate(new ExternalIdentity(
                AuthProvider.GITHUB,
                providerSubject,
                verifiedEmail,
                suggestedName.firstName(),
                suggestedName.lastName(),
                avatarUrl
        ));
        return new GitHubOAuth2Principal(
                account.user(),
                oauth2User,
                suggestedName.firstName(),
                suggestedName.lastName(),
                avatarUrl
        );
    }

    private static String requireGitHubId(Object id) {
        if (id instanceof Number number && number.longValue() > 0) {
            return Long.toString(number.longValue());
        }
        if (id instanceof String value) {
            try {
                long numericId = Long.parseLong(value.strip());
                if (numericId > 0) {
                    return Long.toString(numericId);
                }
            } catch (NumberFormatException ignored) {
                // Converted to the same safe OAuth error below.
            }
        }
        throw authenticationFailure("github_subject_missing");
    }

    private static SuggestedName suggestedName(
            @Nullable String providerName,
            @Nullable String login
    ) {
        if (providerName == null) {
            return new SuggestedName(login, null);
        }
        String[] parts = providerName.split("\\s+", 2);
        return new SuggestedName(
                parts[0],
                parts.length == 2 ? parts[1] : null
        );
    }

    @Nullable
    private static String optionalString(Object value) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return null;
        }
        return stringValue.strip();
    }

    private static OAuth2AuthenticationException authenticationFailure(
            String errorCode
    ) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
    }

    private record SuggestedName(
            @Nullable String firstName,
            @Nullable String lastName
    ) {
    }
}
