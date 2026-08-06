package com.firstagent.backend.infrastructure.adapter.out.whatsapp;

import com.firstagent.backend.application.port.out.WhatsAppBankingPort;
import com.firstagent.backend.domain.model.BankAccount;
import com.firstagent.backend.domain.model.valueobject.BankAccountNumber;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WhatsAppBankingAdapter implements WhatsAppBankingPort {

    private final WebClient webClient;
    private final String apiKey;

    public WhatsAppBankingAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${app.whatsapp-banking.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.whatsapp-banking.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "La clé API du WhatsApp banking (app.whatsapp-banking.api-key) est obligatoire."
            );
        }
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public Mono<BankAccount> readAccount(BankAccountNumber accountNumber) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/onboarding/account")
                        .queryParam("phone", "")
                        .queryParam("rib", accountNumber.value())
                        .build())
                .header("X-API-Key", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(remote -> {
                    if (remote == null || !Boolean.TRUE.equals(remote.get("exists"))) {
                        return Mono.empty();
                    }

                    String fullName = String.valueOf(remote.getOrDefault("name", "")).trim();
                    String lastName = fullName.isEmpty() ? "" : fullName.split("\\s+")[0];
                    String firstName = fullName.contains(" ") ? fullName.substring(lastName.length()).trim() : fullName;

                    BankAccount bankAccount = BankAccount.creer(
                            accountNumber,
                            fullName.isEmpty() ? accountNumber.value() : fullName,
                            firstName.isEmpty() ? "?" : firstName,
                            lastName.isEmpty() ? "?" : lastName,
                            true
                    );

                    return Mono.just(bankAccount);
                })
                .onErrorResume(e -> Mono.empty());
    }
}
