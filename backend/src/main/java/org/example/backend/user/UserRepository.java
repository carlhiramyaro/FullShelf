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

    // The pending owner a first Clerk login links to: an owner row with this
    // email that no Clerk user has claimed yet. Unique per email (partial index).
    @EntityGraph(attributePaths = "shop")
    Optional<User> findFirstByEmailIgnoreCaseAndRoleAndClerkUserIdIsNull(String email, UserRole role);

    List<User> findByShopAndRoleAndClerkUserIdIsNull(Shop shop, UserRole role);

    boolean existsByEmailIgnoreCaseAndRoleAndClerkUserIdIsNullAndShopNot(String email, UserRole role, Shop shop);
}
