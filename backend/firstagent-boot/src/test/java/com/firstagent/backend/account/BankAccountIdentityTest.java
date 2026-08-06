package com.firstagent.backend.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.account.repository.BankAccountRepository;
import com.firstagent.backend.common.enums.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "app.jwt.secret=test-secret-that-is-long-enough-for-hmac-sha256",
      "spring.mail.host=localhost"
    })
class BankAccountIdentityTest {

  @Autowired private BankAccountRepository bankAccountRepository;

  @Test
  void exposesSeparateFirstAndLastNamesForTheSeededAccount() {
    BankAccount account =
        bankAccountRepository.findByAccountNumber("10005123451234567890123").orElseThrow();

    assertThat(account.getFirstName()).isEqualTo("Jean");
    assertThat(account.getLastName()).isEqualTo("Dupont");
    assertThat(CustomerStatus.valueOf("USER")).isEqualTo(CustomerStatus.USER);
  }
}
