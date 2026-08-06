package com.firstagent.backend.onboarding.repository;

import com.firstagent.backend.onboarding.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  Optional<Customer> findByBankAccount_AccountNumber(String accountNumber);

  Optional<Customer> findByEmail(String email);

  Optional<Customer> findByPhoneNumber(String phoneNumber);

  Optional<Customer> findByOnboardingSession_Id(UUID onboardingSessionId);

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);
}
