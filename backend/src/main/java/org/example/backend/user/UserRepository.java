package org.example.backend.user;

import org.example.backend.shop.Shop;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Shop loaded eagerly: OwnerShopFilter reads it outside a transaction.
    @EntityGraph(attributePaths = "shop")
    Optional<User> findByClerkUserId(String clerkUserId);

    // Shop-scoped lookups for staff management, roster and sale creation.
    Optional<User> findByIdAndShop(Long id, Shop shop);

    List<User> findByShopAndRoleOrderByName(Shop shop, UserRole role);

    List<User> findByShopAndRoleAndActiveTrueOrderByName(Shop shop, UserRole role);

    // Still unscoped on purpose: OwnerBootstrapRunner (deleted in G7) and its
    // test call these.
    List<User> findByRoleOrderByName(UserRole role);

    List<User> findByRoleAndActiveTrueOrderByIdAsc(UserRole role);
}
