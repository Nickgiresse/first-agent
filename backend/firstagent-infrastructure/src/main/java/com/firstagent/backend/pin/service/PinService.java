package com.firstagent.backend.pin.service;

public interface PinService {
    String hashPin(String rawPin);
    boolean matches(String rawPin, String pinHash);
}