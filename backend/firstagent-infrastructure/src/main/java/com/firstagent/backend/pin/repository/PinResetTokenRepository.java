package com.firstagent.backend.pin.repository;

import com.firstagent.backend.pin.entity.PinResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinResetTokenRepository extends JpaRepository<PinResetToken, UUID> {

  Optional<PinResetToken> findByResetTokenAndUsedFalse(String resetToken);
}
