package com.firstagent.backend.vision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Champs KYC extraits par le moteur OCR de la DEI (voir AFB_GI_OCR_DEI). Dates volontairement
 * typées en String : le format renvoyé n'est pas garanti par le contrat, le parsing tolérant se
 * fait côté {@code DeiOcrProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeiOcrFields(
    @JsonProperty("last_name") String lastName,
    @JsonProperty("first_name") String firstName,
    @JsonProperty("birth_date") String birthDate,
    @JsonProperty("cni_number") String cniNumber,
    @JsonProperty("identity_document_number") String identityDocumentNumber,
    @JsonProperty("identity_issue_date") String identityIssueDate,
    @JsonProperty("identity_expiry_date") String identityExpiryDate,
    @JsonProperty("sex") String sex,
    @JsonProperty("possible_dates") List<String> possibleDates,
    @JsonProperty("email") String email,
    @JsonProperty("phone") String phone,
    @JsonProperty("niu") String niu,
    @JsonProperty("rib") String rib,
    @JsonProperty("rccm") String rccm,
    @JsonProperty("company_name") String companyName
) {
    public String documentNumber() {
        return cniNumber != null && !cniNumber.isBlank() ? cniNumber : identityDocumentNumber;
    }
}
