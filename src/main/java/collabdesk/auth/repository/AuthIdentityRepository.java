package collabdesk.auth.repository;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {
    Optional<AuthIdentity> findByProviderSubjectAndProvider(String providerSubject, AuthProvider provider);

    boolean existsByProviderSubjectAndProvider(String providerSubject,  AuthProvider provider);
}
