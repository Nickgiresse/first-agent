package com.firstagent.backend.pin.service;

import com.firstagent.backend.pin.port.PinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PinServiceImpl implements PinService {

  private final PasswordEncoder passwordEncoder;

  @Override
  public String hashPin(String rawPin) {
    return passwordEncoder.encode(rawPin);
  }

  @Override
  public boolean matches(String rawPin, String pinHash) {
    return passwordEncoder.matches(rawPin, pinHash);
  }
}
