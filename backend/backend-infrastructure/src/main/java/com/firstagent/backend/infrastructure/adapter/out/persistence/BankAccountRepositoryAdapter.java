package com.firstagent.backend.infrastructure.adapter.out.persistence;

import com.firstagent.backend.application.port.out.BankAccountRepositoryPort;
import com.firstagent.backend.domain.model.BankAccount;
import com.firstagent.backend.domain.model.valueobject.BankAccountId;
import com.firstagent.backend.domain.model.valueobject.BankAccountNumber;
import com.firstagent.backend.infrastructure.adapter.out.persistence.entity.BankAccountEntity;
import com.firstagent.backend.infrastructure.adapter.out.persistence.repository.BankAccountR2dbcRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class BankAccountRepositoryAdapter implements BankAccountRepositoryPort {

    private final BankAccountR2dbcRepository repository;

    public BankAccountRepositoryAdapter(BankAccountR2dbcRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Mono<BankAccount> findById(BankAccountId id) {
        return repository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public Mono<BankAccount> findByAccountNumber(BankAccountNumber accountNumber) {
        return repository.findByAccountNumber(accountNumber.value())
                .map(this::toDomain);
    }

    @Override
    public Mono<BankAccount> save(BankAccount bankAccount) {
        return repository.save(toEntity(bankAccount))
                .map(this::toDomain);
    }

    private BankAccount toDomain(BankAccountEntity entity) {
        return BankAccount.reconstituer(
                new BankAccountId(entity.getId()),
                new BankAccountNumber(entity.getAccountNumber()),
                entity.getOwnerFullName(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.isEligible(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private BankAccountEntity toEntity(BankAccount domain) {
        BankAccountEntity entity = new BankAccountEntity();
        entity.setId(domain.getId().value());
        entity.setAccountNumber(domain.getAccountNumber().value());
        entity.setOwnerFullName(domain.getOwnerFullName());
        entity.setFirstName(domain.getFirstName());
        entity.setLastName(domain.getLastName());
        entity.setEligible(domain.isEligible());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
