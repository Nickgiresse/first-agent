package com.firstagent.backend.pin.service;

import com.firstagent.backend.common.exception.AccountNotFoundException;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.InvalidPinException;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.pin.dto.PinResetConfirmRequest;
import com.firstagent.backend.pin.dto.PinResetRequest;
import com.firstagent.backend.pin.dto.PinResetResponse;
import com.firstagent.backend.pin.entity.PinResetToken;
import com.firstagent.backend.pin.repository.PinResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PinResetServiceImpl implements PinResetService {

    @Value("${app.mail.from-address}")
    private String fromAddress;

    private final CustomerRepository customerRepository;
    private final PinResetTokenRepository pinResetTokenRepository;
    private final PinService pinService;
    private final JavaMailSender mailSender;

    @Override
    @Transactional
    public PinResetResponse requestReset(PinResetRequest request) {
        Customer customer = customerRepository.findByBankAccount_AccountNumber(request.getAccountNumber())
            .orElseThrow(() -> new AccountNotFoundException("Aucun compte trouvé avec ce numéro"));

        if (customer.getEmail() == null) {
            return PinResetResponse.builder()
                .emailSent(false)
                .requiresBranchVisit(true)
                .message("Aucun email n'est associé à ce compte. Veuillez vous rendre en agence pour réinitialiser votre PIN.")
                .build();
        }

        String resetToken = UUID.randomUUID().toString();

        PinResetToken token = PinResetToken.builder()
            .customer(customer)
            .resetToken(resetToken)
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .used(false)
            .build();

        pinResetTokenRepository.save(token);
        sendResetEmail(customer.getEmail(), resetToken);

        return PinResetResponse.builder()
            .emailSent(true)
            .requiresBranchVisit(false)
            .message("Un lien de réinitialisation a été envoyé à votre adresse email")
            .build();
    }

    @Override
    @Transactional
    public void confirmReset(PinResetConfirmRequest request) {
        PinResetToken token = pinResetTokenRepository.findByResetTokenAndUsedFalse(request.getResetToken())
            .orElseThrow(() -> new BusinessException("Ce lien de réinitialisation est invalide ou a déjà été utilisé"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Ce lien de réinitialisation a expiré");
        }

        if (!request.getNewPin().equals(request.getNewPinConfirmation())) {
            throw new InvalidPinException("Le nouveau PIN et sa confirmation ne correspondent pas");
        }

        Customer customer = token.getCustomer();
        customer.setPinHash(pinService.hashPin(request.getNewPin()));
        customerRepository.save(customer);

        token.setUsed(true);
        pinResetTokenRepository.save(token);
    }

    private void sendResetEmail(String email, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Réinitialisation de votre PIN");
        message.setText("Voici votre lien de réinitialisation (valable 15 minutes) : "
            + "https://votre-app.com/forgot-pin/confirm?token=" + resetToken);
        mailSender.send(message);
    }
}