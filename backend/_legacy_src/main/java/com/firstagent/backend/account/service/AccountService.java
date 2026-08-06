package com.firstagent.backend.account.service;

import com.firstagent.backend.account.dto.AccountVerificationRequest;
import com.firstagent.backend.account.dto.AccountVerificationResponse;

public interface AccountService {
    AccountVerificationResponse verifyAccount(AccountVerificationRequest request);
}