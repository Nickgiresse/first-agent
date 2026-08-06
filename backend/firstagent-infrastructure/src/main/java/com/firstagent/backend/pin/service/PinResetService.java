package com.firstagent.backend.pin.service;

import com.firstagent.backend.pin.dto.PinResetConfirmRequest;
import com.firstagent.backend.pin.dto.PinResetRequest;
import com.firstagent.backend.pin.dto.PinResetResponse;

public interface PinResetService {
  PinResetResponse requestReset(PinResetRequest request);

  void confirmReset(PinResetConfirmRequest request);
}
