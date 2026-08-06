package com.firstagent.backend.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.firstagent.backend.account.dto.AccountVerificationRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class AccountSuffixValidationTest {
  @Test
  void acceptsExactlyEighteenDigits() {
    AccountVerificationRequest request = new AccountVerificationRequest();
    request.setAccountSuffix("123451234567890123");

    assertThat(Validation.buildDefaultValidatorFactory().getValidator().validate(request))
        .isEmpty();
  }
}
