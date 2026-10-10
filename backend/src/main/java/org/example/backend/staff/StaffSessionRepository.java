package org.example.backend.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StaffSessionRepository extends JpaRepository<StaffSession, Long> {

    Optional<StaffSession> findByTokenHashAndRevokedFalse(String tokenHash);

    // For StaffAccessFilter: the session with its user, device and both shops
    // loaded, so it can compare them outside a transaction.
    @Query("select s from StaffSession s join fetch s.user u join fetch u.shop "
            + "join fetch s.device d join fetch d.shop "
            + "where s.tokenHash = :tokenHash and s.revoked = false")
    Optional<StaffSession> findActiveWithShops(@Param("tokenHash") String tokenHash);
}
