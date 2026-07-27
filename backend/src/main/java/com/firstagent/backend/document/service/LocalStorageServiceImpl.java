package com.firstagent.backend.document.service;

import com.firstagent.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalStorageServiceImpl implements StorageService {

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String subFolder) {
        try {
            Path targetDir = Paths.get(uploadDir, subFolder);
            Files.createDirectories(targetDir);

            String extension = getExtension(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID() + extension;
            Path targetPath = targetDir.resolve(storedFileName);

            Files.copy(file.getInputStream(), targetPath);

            return targetPath.toString();
        } catch (IOException e) {
            throw new BusinessException("Erreur lors de l'enregistrement du fichier");
        }
    }

    private String getExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf("."));
    }
}