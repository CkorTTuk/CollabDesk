package collabdesk.auth.repository;

import collabdesk.auth.entity.AuthIdentity;
import collabdesk.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {
    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider,String  providerSubject);

    boolean existsByProviderAndProviderSubject(AuthProvider provider,String  providerSubject);

    @EntityGraph(attributePaths = "user")
    Optional<AuthIdentity> findWithUserByProviderAndProviderSubject(
            AuthProvider provider,
            String providerSubject
    );

    boolean existsByUser_IdAndProvider(Long userId, AuthProvider provider);

    List<AuthIdentity> findAllByUser_IdOrderByProviderAsc(Long userId);

    @Query("select identity.provider from AuthIdentity identity " +
            "where identity.user.id = :userId order by identity.provider")
    List<AuthProvider> findProvidersByUserId(@Param("userId") Long userId);

    long countByUser_Id(Long userId);
}
