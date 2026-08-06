package com.firstagent.backend.audit.repository;

import com.firstagent.backend.audit.entity.AuditLogEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Accès au journal forensique. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

  /** Dernier maillon de la chaîne, sur lequel s'accroche la prochaine entrée. */
  Optional<AuditLogEntry> findFirstByOrderByTimestampDescIdDesc();

  /**
   * Page suivante dans l'ordre du chaînage, à partir d'une position donnée.
   *
   * <p>Pagination par clé, et non par {@code OFFSET}. La différence n'est pas de style : le coût
   * d'un OFFSET croît avec la profondeur, le moteur devant parcourir puis jeter toutes les lignes
   * précédentes. À la dix-millionième entrée, chaque page en coûterait dix millions.
   *
   * <p>La comparaison porte sur le couple (timestamp, id) car deux entrées peuvent partager la même
   * microseconde ; l'identifiant les départage et rend l'ordre total.
   */
  @Query(
      """
      SELECT e FROM AuditLogEntry e
      WHERE e.timestamp > :horodatage
         OR (e.timestamp = :horodatage AND e.id > :identifiant)
      ORDER BY e.timestamp ASC, e.id ASC
      """)
  List<AuditLogEntry> pageApres(
      @Param("horodatage") Instant horodatage,
      @Param("identifiant") UUID identifiant,
      Limit taille);

  /** Première page du parcours, quand aucune position n'a encore été atteinte. */
  @Query("SELECT e FROM AuditLogEntry e ORDER BY e.timestamp ASC, e.id ASC")
  List<AuditLogEntry> premierePage(Limit taille);

  /**
   * Compte les échecs par numéro de client, pour les règles d'alerte.
   *
   * <p>Le comptage est fait par la base et non en mémoire : ramener les entrées pour les compter
   * côté application ferait grossir la requête avec le trafic, alors que c'est précisément quand le
   * trafic est anormal que les règles doivent répondre vite.
   */
  @Query(
      """
      SELECT e.phone AS telephone, COUNT(e) AS occurrences FROM AuditLogEntry e
      WHERE e.eventType IN :types
        AND e.status = 'FAILURE'
        AND e.timestamp >= :depuis
        AND e.phone <> ''
      GROUP BY e.phone
      """)
  List<ComptageParTelephone> compterEchecsParTelephone(
      @Param("types") List<String> types, @Param("depuis") Instant depuis);

  /**
   * Résultat d'un comptage, sous forme nommée.
   *
   * <p>Une projection plutôt qu'un {@code Object[]} : avec un tableau, l'ordre des colonnes est un
   * contrat implicite que rien ne vérifie, et intervertir deux colonnes dans la requête produit un
   * transtypage qui n'échoue qu'à l'exécution.
   */
  interface ComptageParTelephone {
    String getTelephone();

    long getOccurrences();
  }

  /**
   * Une alerte de même référence a-t-elle déjà été émise depuis cet instant ?
   *
   * <p>Sans cette question, chaque balayage réécrirait les mêmes alertes tant que la cause dure, et
   * la répétition finirait par rendre le signal inaudible.
   */
  boolean existsByEventTypeAndReferenceAndTimestampGreaterThanEqual(
      String eventType, String reference, Instant depuis);

  /** Parcours chronologique borné dans le temps, pour l'export vers un SIEM. */
  @Query(
      """
      SELECT e FROM AuditLogEntry e
      WHERE (:depuis IS NULL OR e.timestamp >= :depuis)
      ORDER BY e.timestamp ASC, e.id ASC
      """)
  List<AuditLogEntry> depuis(@Param("depuis") Instant depuis, Limit taille);
}
