package com.firstagent.backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.firstagent.backend.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** Confinement du stockage des pièces au répertoire de dépôt. */
class LocalStorageServiceImplTest {

  @TempDir Path depot;

  private LocalStorageServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new LocalStorageServiceImpl();
    ReflectionTestUtils.setField(service, "uploadDir", depot.toString());
  }

  @Test
  @DisplayName("un nom de fichier piégé n'écrit pas hors du répertoire de dépôt")
  void store_nomDeFichierPiege_resteDansLeDepot() {
    // « Extension » contenant des séparateurs : l'ancienne implémentation la
    // reprenait telle quelle et sortait du dossier.
    MockMultipartFile fichier =
        new MockMultipartFile(
            "file", "photo.jpg/../../../evade.txt", "image/jpeg", "contenu".getBytes());

    String chemin = service.store(fichier, "cni");

    assertThat(Path.of(chemin).normalize()).startsWithRaw(depot);
  }

  @Test
  @DisplayName("une extension non autorisée est remplacée par une extension neutre")
  void store_extensionNonAutorisee_estNeutralisee() {
    MockMultipartFile fichier =
        new MockMultipartFile("file", "charge.jsp", "image/jpeg", "contenu".getBytes());

    assertThat(service.store(fichier, "cni")).endsWith(".bin");
  }

  @Test
  @DisplayName("une extension légitime est conservée")
  void store_extensionLegitime_estConservee() {
    MockMultipartFile fichier =
        new MockMultipartFile("file", "piece.PNG", "image/png", "contenu".getBytes());

    assertThat(service.store(fichier, "cni")).endsWith(".png");
  }

  @Test
  @DisplayName("un sous-dossier piégé est assaini")
  void store_sousDossierPiege_estAssaini() {
    MockMultipartFile fichier =
        new MockMultipartFile("file", "piece.jpg", "image/jpeg", "contenu".getBytes());

    String chemin = service.store(fichier, "../../ailleurs");

    assertThat(Path.of(chemin).normalize()).startsWithRaw(depot);
  }

  @Test
  @DisplayName("la lecture d'un fichier hors du dépôt est refusée")
  void read_horsDuDepot_estRefusee() {
    assertThatThrownBy(() -> service.read(depot.resolve("../secret.txt").toString()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("invalide");
  }

  @Test
  @DisplayName("la suppression d'un fichier hors du dépôt est refusée")
  void delete_horsDuDepot_estRefusee() {
    assertThatThrownBy(() -> service.delete(depot.resolve("../secret.txt").toString()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("un fichier déposé se relit correctement")
  void store_puis_read_restitueLeContenu() throws IOException {
    MockMultipartFile fichier =
        new MockMultipartFile("file", "piece.jpg", "image/jpeg", "contenu".getBytes());

    String chemin = service.store(fichier, "cni");

    assertThat(Files.exists(Path.of(chemin))).isTrue();
    assertThat(service.read(chemin)).isEqualTo("contenu".getBytes());
  }
}
