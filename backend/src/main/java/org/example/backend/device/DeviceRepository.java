package org.example.backend.device;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    // The shop is loaded with the device: StaffAccessFilter reads it outside a
    // transaction (open-in-view is off), where a lazy shop would fail.
    @EntityGraph(attributePaths = "shop")
    Optional<Device> findByTokenHashAndRevokedFalse(String tokenHash);
}
