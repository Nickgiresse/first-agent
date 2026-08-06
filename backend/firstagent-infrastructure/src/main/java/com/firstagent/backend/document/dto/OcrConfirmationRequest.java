package com.firstagent.backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OcrConfirmationRequest {

  @NotBlank(message = "Le prénom est obligatoire")
  private String firstName;

  @NotBlank(message = "Le nom est obligatoire")
  private String lastName;

  // Optionnel : uniquement présent sur une CNI définitive, absent sur un titre provisoire ou un
  // récépissé (qui ont un numéro de kit / identifiant de demande à la place).
  private String documentNumber;

  private String sex;

  // Optionnels au niveau du DTO : un récépissé de paiement n'a ni date de naissance ni date
  // d'expiration (seulement une date de paiement). Le caractère obligatoire pour CNI/titre
  // provisoire est vérifié dans OcrServiceImpl, propre à chaque type de document.
  @Past(message = "La date de naissance doit être dans le passé")
  private LocalDate birthDate;

  private LocalDate expiryDate;

  private String birthPlace;
  private String fatherName;
  private String motherName;
  private String kitNumber;
  private String requestIdentifier;

  // Récépissé de paiement uniquement :
  private String paymentAmount;
  private LocalDate paymentDate;
}
