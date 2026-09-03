package collabdesk.account.onboarding;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompleteOnboardingRequestTest {
    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void normalizesNamesBeforeValidation() {
        CompleteOnboardingRequest request = new CompleteOnboardingRequest(
                "  Alex  ",
                "   ",
                null
        );

        assertEquals("Alex", request.firstName());
        assertEquals("", request.lastName());
        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    @Test
    void rejectsBlankOrTooLongFirstName() {
        assertTrue(VALIDATOR.validate(new CompleteOnboardingRequest(
                "   ",
                null,
                null
        )).stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("firstName")
        ));

        assertTrue(VALIDATOR.validate(new CompleteOnboardingRequest(
                "a".repeat(101),
                null,
                null
        )).stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("firstName")
        ));
    }

    @Test
    void rejectsTooLongLastNameAndFutureBirthDate() {
        CompleteOnboardingRequest request = new CompleteOnboardingRequest(
                "Alex",
                "a".repeat(101),
                LocalDate.now().plusDays(1)
        );

        var violations = VALIDATOR.validate(request);
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("lastName")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("birthDate")
        ));
    }
}
