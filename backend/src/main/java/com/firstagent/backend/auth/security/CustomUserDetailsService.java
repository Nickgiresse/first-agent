package com.firstagent.backend.auth.security;

import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    @Override
    public UserDetails loadUserByUsername(String customerId) throws UsernameNotFoundException {
        Customer customer = customerRepository.findById(UUID.fromString(customerId))
            .orElseThrow(() -> new UsernameNotFoundException("Client introuvable"));

        return new CustomUserDetails(customer);
    }
}