package com.firstagent.backend.document.service;

import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.TypeErreurMetier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stockage des pièces sur le système de fichiers local.
 *
 * <p>Le nom fourni par le client n'est jamais réutilisé comme chemin, comme l'exige la charte
 * frontend §14.1 et son équivalent backend : chaque fichier reçoit un identifiant tiré au sort, et
 * seule l'extension est conservée, après contrôle strict.
 *
 * <p>Toute lecture, écriture ou suppression est en outre confinée au répertoire de dépôt : un
 * chemin qui en sortirait est refusé, quelle qu'en soit l'origine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageServiceImpl implements StorageService {

  /**
   * Extensions acceptées pour une pièce d'identité ou un selfie.
   *
   * <p>Liste blanche et non liste noire : l'inventaire de ce qui est dangereux est sans fin, celui
   * de ce dont le parcours a besoin tient en cinq entrées.
   */
  private static final Set<String> EXTENSIONS_AUTORISEES =
      Set.of(".jpg", ".jpeg", ".png", ".webp", ".pdf");

  private static final String EXTENSION_PAR_DEFAUT = ".bin";

  @Value("${app.storage.upload-dir}")
  private String uploadDir;

  @Override
  public String store(MultipartFile file, String subFolder) {
    try {
      Path racine = racineDeDepot();
      Path targetDir = confiner(racine.resolve(sousDossierAssaini(subFolder)), racine);
      Files.createDirectories(targetDir);

      // Identifiant tiré au sort : le nom d'origine ne sert qu'à déduire
      // l'extension, jamais à composer le chemin.
      String storedFileName = UUID.randomUUID() + extensionSure(file.getOriginalFilename());
      Path targetPath = confiner(targetDir.resolve(storedFileName), racine);

      Files.copy(file.getInputStream(), targetPath);

      return targetPath.toString();
    } catch (IOException e) {
      throw new BusinessException("Erreur lors de l'enregistrement du fichier");
    }
  }

  @Override
  public byte[] read(String filePath) {
    try {
      return Files.readAllBytes(confiner(Paths.get(filePath), racineDeDepot()));
    } catch (IOException e) {
      throw new BusinessException("Erreur lors de la lecture du fichier");
    }
  }

  @Override
  public void delete(String filePath) {
    try {
      Files.deleteIfExists(confiner(Paths.get(filePath), racineDeDepot()));
    } catch (IOException e) {
      log.warn("Impossible de supprimer l'ancien fichier {}", filePath, e);
    }
  }

  private Path racineDeDepot() {
    return Paths.get(uploadDir).toAbsolutePath().normalize();
  }

  /**
   * Refuse tout chemin qui sortirait du répertoire de dépôt.
   *
   * <p>La normalisation résout les {@code ..} avant la comparaison : sans elle, un chemin
   * syntaxiquement contenu pourrait désigner un fichier situé ailleurs.
   */
  private Path confiner(Path chemin, Path racine) {
    Path normalise = chemin.toAbsolutePath().normalize();
    if (!normalise.startsWith(racine)) {
      log.warn("Chemin refusé, hors du répertoire de dépôt : {}", chemin);
      throw new BusinessException(
          "Chemin de fichier invalide.", TypeErreurMetier.VALIDATION, "RG-DOC-001");
    }
    return normalise;
  }

  /** Ne conserve du sous-dossier que des caractères inoffensifs. */
  private String sousDossierAssaini(String subFolder) {
    if (subFolder == null || subFolder.isBlank()) {
      return "divers";
    }
    String nettoye = subFolder.replaceAll("[^A-Za-z0-9_-]", "");
    return nettoye.isEmpty() ? "divers" : nettoye;
  }

  /**
   * Extension retenue, ou une extension neutre.
   *
   * <p>L'ancienne version reprenait tout ce qui suivait le dernier point du nom fourni par le
   * client. Un nom tel que {@code photo.jpg/../../../ailleurs} produisait donc une « extension »
   * contenant des séparateurs, et le fichier s'écrivait hors du dossier de dépôt. C'est le défaut
   * que SpotBugs signalait en PATH_TRAVERSAL_IN.
   */
  private String extensionSure(String originalFileName) {
    if (originalFileName == null) {
      return EXTENSION_PAR_DEFAUT;
    }
    int point = originalFileName.lastIndexOf('.');
    if (point < 0) {
      return EXTENSION_PAR_DEFAUT;
    }
    String extension = originalFileName.substring(point).toLowerCase(Locale.ROOT);
    return EXTENSIONS_AUTORISEES.contains(extension) ? extension : EXTENSION_PAR_DEFAUT;
  }
}
