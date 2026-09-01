package collabdesk.auth.security;

import collabdesk.user.entity.UserStatus;

public interface CollabDeskPrincipal {
    Long getUserId();
    String getEmail();
    String getDisplayName();
    UserStatus getStatus();
}
