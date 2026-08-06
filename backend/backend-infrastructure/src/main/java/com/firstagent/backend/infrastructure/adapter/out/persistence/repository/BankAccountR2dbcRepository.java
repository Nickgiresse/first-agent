package com.firstagent.backend.infrastructure.adapter.out.persistence.repository;

import com.firstagent.backend.infrastructure.adapter.out.persistence.entity.BankAccountEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface BankAccountR2dbcRepository extends ReactiveCrudRepository<BankAccountEntity, UUID> {
    Mono<BankAccountEntity> findByAccountNumber(String accountNumber);
}
