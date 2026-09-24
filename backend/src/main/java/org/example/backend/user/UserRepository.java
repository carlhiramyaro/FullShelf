package org.example.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByClerkUserId(String clerkUserId);

    List<User> findByRoleOrderByName(UserRole role);

    List<User> findByRoleAndActiveTrueOrderByName(UserRole role);
}
