package org.example.backend.admin;

import org.example.backend.owner.OwnerController;
import org.example.backend.security.OwnerLinkService;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-admin operations across shops. Unlike every other service this one
 * is deliberately not scoped to a current shop: the admin has none.
 */
@Service
public class AdminShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final OwnerLinkService ownerLinkService;

    public AdminShopService(ShopRepository shopRepository, UserRepository userRepository,
                            OwnerLinkService ownerLinkService) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.ownerLinkService = ownerLinkService;
    }

    public record OwnerInfo(String name, String email, String phoneNumber, String status) {
    }

    public record ShopSummary(Long id, String name, Instant createdAt, int activeStaff, OwnerInfo owner) {
    }

    @Transactional(readOnly = true)
    public List<ShopSummary> listShops() {
        Map<Long, Integer> staffCounts = new HashMap<>();
        for (Object[] row : userRepository.countActiveStaffByShop()) {
            staffCounts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        Map<Long, List<User>> ownersByShop = new HashMap<>();
        for (User owner : userRepository.findAllOwnersWithShop()) {
            ownersByShop.computeIfAbsent(owner.getShop().getId(), k -> new java.util.ArrayList<>()).add(owner);
        }
        return shopRepository.findAll().stream()
                .sorted(Comparator.comparing(Shop::getId))
                .map(shop -> new ShopSummary(shop.getId(), shop.getName(), shop.getCreatedAt(),
                        staffCounts.getOrDefault(shop.getId(), 0),
                        currentOwner(ownersByShop.getOrDefault(shop.getId(), List.of()))))
                .toList();
    }

    /** Active owner if there is one, else the waiting (pending) one, else none. */
    private OwnerInfo currentOwner(List<User> owners) {
        User active = owners.stream().filter(User::isActive).findFirst().orElse(null);
        if (active != null) {
            return info(active, "active");
        }
        User pending = owners.stream().filter(o -> o.getClerkUserId() == null)
                .reduce((a, b) -> b).orElse(null);
        return pending == null ? null : info(pending, "pending");
    }

    private OwnerInfo info(User owner, String status) {
        return new OwnerInfo(owner.getName(), owner.getEmail(), owner.getPhoneNumber(), status);
    }

    /** Shop and its pending owner are created together or not at all. */
    @Transactional
    public ShopSummary createShop(String shopName, String ownerName, String phone, String email) {
        if (shopName == null || shopName.isBlank()) {
            throw new IllegalArgumentException("Shop name is required");
        }
        String normalizedPhone = validatedPhone(phone);
        Shop shop = shopRepository.save(new Shop(shopName.trim()));
        ownerLinkService.assignOwner(shop, ownerName, normalizedPhone, email);
        return summary(shop);
    }

    @Transactional
    public ShopSummary relinkOwner(Long shopId, String ownerName, String phone, String email) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("No shop " + shopId));
        ownerLinkService.assignOwner(shop, ownerName, validatedPhone(phone), email);
        return summary(shop);
    }

    private ShopSummary summary(Shop shop) {
        userRepository.flush();
        return listShops().stream().filter(s -> s.id().equals(shop.getId())).findFirst().orElseThrow();
    }

    // Phone is optional for the pending owner; she can also set it in Settings.
    private String validatedPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (!OwnerController.E164.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Enter a phone number in international format, e.g. +233241234567");
        }
        return trimmed;
    }
}
