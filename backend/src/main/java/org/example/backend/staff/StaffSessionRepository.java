package org.example.backend.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffSessionRepository extends JpaRepository<StaffSession, Long> {

    Optional<StaffSession> findByTokenHashAndRevokedFalse(String tokenHash);
}
