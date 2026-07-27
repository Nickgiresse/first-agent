package com.firstagent.backend.auth.service;

import com.firstagent.backend.auth.dto.LoginRequest;
import com.firstagent.backend.auth.dto.LoginResponse;
import com.firstagent.backend.common.enums.CustomerStatus;
import com.firstagent.backend.common.exception.AccountNotFoundException;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.InvalidPinException;
import com.firstagent.backend.common.security.TokenProvider;
import com.firstagent.backend.onboarding.dto.CustomerResponse;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.pin.service.PinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long ACCESS_TOKEN_DURATION_MILLIS = 15 * 60 * 1000L;      // 15 min
    private static final long REFRESH_TOKEN_DURATION_MILLIS = 7 * 24 * 3600 * 1000L; // 7 jours

    private final CustomerRepository customerRepository;
    private final PinService pinService;
    private final TokenProvider tokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByBankAccount_AccountNumber(request.getAccountNumber())
            .orElseThrow(() -> new AccountNotFoundException("Numéro de compte ou PIN incorrect"));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException("Ce compte est actuellement bloqué ou suspendu");
        }

        if (!pinService.matches(request.getPin(), customer.getPinHash())) {
            throw new InvalidPinException("Numéro de compte ou PIN incorrect");
        }

        Map<String, Object> claims = Map.of("customerId", customer.getId().toString());
        String accessToken = tokenProvider.generateToken(claims, ACCESS_TOKEN_DURATION_MILLIS);
        String refreshToken = tokenProvider.generateToken(claims, REFRESH_TOKEN_DURATION_MILLIS);

        CustomerResponse customerResponse = CustomerResponse.builder()
            .id(customer.getId())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .email(customer.getEmail())
            .phoneNumber(customer.getPhoneNumber())
            .hasEmail(customer.getEmail() != null)
            .build();

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresInSeconds(ACCESS_TOKEN_DURATION_MILLIS / 1000)
            .customer(customerResponse)
            .build();
    }
}