package collabdesk.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public final class CollabDeskAuthorities {
    public static final String PROFILE_COMPLETE = "PROFILE_COMPLETE";

    private static final List<GrantedAuthority> PROFILE_COMPLETE_AUTHORITIES =
            List.of(new SimpleGrantedAuthority(PROFILE_COMPLETE));

    private CollabDeskAuthorities() {
    }

    public static List<GrantedAuthority> forProfile(boolean onboardingCompleted) {
        return onboardingCompleted ? PROFILE_COMPLETE_AUTHORITIES : List.of();
    }
}
