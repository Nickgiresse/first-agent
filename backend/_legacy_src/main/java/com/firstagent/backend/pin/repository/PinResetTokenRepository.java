package com.firstagent.backend.pin.repository;

import com.firstagent.backend.pin.entity.PinResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PinResetTokenRepository extends JpaRepository<PinResetToken, UUID> {

    Optional<PinResetToken> findByResetTokenAndUsedFalse(String resetToken);
}