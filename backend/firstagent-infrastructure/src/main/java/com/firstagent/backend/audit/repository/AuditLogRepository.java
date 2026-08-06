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

  /** Parcours chronologique borné dans le temps, pour l'export vers un SIEM. */
  @Query(
      """
      SELECT e FROM AuditLogEntry e
      WHERE (:depuis IS NULL OR e.timestamp >= :depuis)
      ORDER BY e.timestamp ASC, e.id ASC
      """)
  List<AuditLogEntry> depuis(@Param("depuis") Instant depuis, Limit taille);
}
