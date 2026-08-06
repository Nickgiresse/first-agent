package com.firstagent.backend.account.repository;

import com.firstagent.backend.account.entity.BankAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

  Optional<BankAccount> findByAccountNumber(String accountNumber);

  boolean existsByAccountNumber(String accountNumber);
}
