package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Page<User> findByUsernameContainingIgnoreCaseAndIdNot(String query, Long excludeUserId, Pageable pageable);
}
