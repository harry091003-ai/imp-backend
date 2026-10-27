package com.example.AuthService.repository;

import com.example.AuthService.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

// Auth v2 part B: refresh tokens.
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Read the token AND lock its row until the transaction ends.
    // So two refreshes with the same token at the same moment run one after the other:
    // the first one swaps it, the second one sees it is already used.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    // Logout, password change, role change: cancel every token of this user that is still active.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllOfUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // Reuse found: cancel every token made from that one login.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);
}
