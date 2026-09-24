package org.example.backend.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DevicePairingCodeRepository extends JpaRepository<DevicePairingCode, Long> {

    Optional<DevicePairingCode> findByCodeHash(String codeHash);
}
