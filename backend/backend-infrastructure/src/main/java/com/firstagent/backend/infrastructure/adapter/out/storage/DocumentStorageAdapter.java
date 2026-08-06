package com.firstagent.backend.infrastructure.adapter.out.storage;

import com.firstagent.backend.application.port.out.DocumentStoragePort;
import com.firstagent.backend.domain.model.valueobject.DocumentType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DocumentStorageAdapter implements DocumentStoragePort {

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> storeDocument(String sessionToken, DocumentType documentType, byte[] content, String fileName) {
        String key = buildKey(sessionToken, documentType);
        if (content != null) {
            storage.put(key, content);
        }
        return Mono.empty();
    }

    @Override
    public Mono<byte[]> getDocument(String sessionToken, DocumentType documentType) {
        String key = buildKey(sessionToken, documentType);
        byte[] bytes = storage.get(key);
        return bytes != null ? Mono.just(bytes) : Mono.empty();
    }

    private String buildKey(String sessionToken, DocumentType documentType) {
        String token = sessionToken != null ? sessionToken : "default";
        String type = documentType != null ? documentType.name() : "UNKNOWN";
        return token + ":" + type;
    }
}
