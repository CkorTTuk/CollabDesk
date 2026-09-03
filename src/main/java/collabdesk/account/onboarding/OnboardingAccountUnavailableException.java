package collabdesk.account.onboarding;

public class OnboardingAccountUnavailableException extends RuntimeException {
    public OnboardingAccountUnavailableException() {
        super("Account is unavailable");
    }
}
