package collabdesk.auth.verification;

/**
 * Security operation a code may authorize. Including the purpose in the HMAC
 * prevents a code issued for one operation from being reused for another.
 */
public enum VerificationPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    SENSITIVE_ACTION
}
