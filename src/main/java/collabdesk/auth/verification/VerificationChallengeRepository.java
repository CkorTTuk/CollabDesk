package collabdesk.auth.verification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VerificationChallengeRepository
        extends JpaRepository<VerificationChallenge, Long> {
    Optional<VerificationChallenge> findByUser_IdAndPurpose(
            Long userId,
            VerificationPurpose purpose
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge
            from VerificationChallenge challenge
            join fetch challenge.user
            where challenge.user.id = :userId
              and challenge.purpose = :purpose
            """)
    Optional<VerificationChallenge> findForUpdate(
            @Param("userId") Long userId,
            @Param("purpose") VerificationPurpose purpose
    );
}
