package org.example.backend.device;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.example.backend.shop.Shop;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByShopOrderById(Shop shop);

    Optional<Device> findByIdAndShop(Long id, Shop shop);

    // The shop is loaded with the device: StaffAccessFilter reads it outside a
    // transaction (open-in-view is off), where a lazy shop would fail.
    @EntityGraph(attributePaths = "shop")
    Optional<Device> findByTokenHashAndRevokedFalse(String tokenHash);
}
