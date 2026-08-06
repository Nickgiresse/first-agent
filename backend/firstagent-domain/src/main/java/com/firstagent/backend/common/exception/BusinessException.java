package com.firstagent.backend.common.exception;

/**
 * Refus motivé par une règle de gestion.
 *
 * <p>Porte la nature de l'erreur et, quand elle est connue, la référence de la
 * règle enfreinte sous la forme {@code RG-XXX-nnn}, comme le demande la charte
 * backend §3. Cette référence est ce qui permet à un exploitant de relier un
 * refus rencontré en production à la spécification qui l'a décidé, sans avoir
 * à lire le code.
 *
 * <p>Aucune dépendance technique : ni Spring, ni transport. Le statut HTTP
 * correspondant est déterminé par l'adaptateur web à partir du
 * {@link TypeErreurMetier}.
 */
public class BusinessException extends RuntimeException {

    private final TypeErreurMetier type;
    private final String codeRegle;

    /** Refus sans référence de règle : par défaut, une règle métier non satisfaite. */
    public BusinessException(String message) {
        this(message, TypeErreurMetier.REGLE_METIER, null);
    }

    public BusinessException(String message, TypeErreurMetier type) {
        this(message, type, null);
    }

    /**
     * @param codeRegle référence de la règle enfreinte, au format {@code RG-XXX-nnn},
     *                  ou {@code null} lorsque le refus ne renvoie à aucune règle
     *                  formalisée
     */
    public BusinessException(String message, TypeErreurMetier type, String codeRegle) {
        super(message);
        this.type = type == null ? TypeErreurMetier.REGLE_METIER : type;
        this.codeRegle = codeRegle;
    }

    public TypeErreurMetier getType() {
        return type;
    }

    public String getCodeRegle() {
        return codeRegle;
    }
}
