package com.firstagent.backend.application.port.out;

import com.firstagent.backend.domain.model.BankAccount;
import com.firstagent.backend.domain.model.valueobject.BankAccountId;
import com.firstagent.backend.domain.model.valueobject.BankAccountNumber;
import reactor.core.publisher.Mono;

public interface BankAccountRepositoryPort {
    Mono<BankAccount> findById(BankAccountId id);
    Mono<BankAccount> findByAccountNumber(BankAccountNumber accountNumber);
    Mono<BankAccount> save(BankAccount bankAccount);
}
