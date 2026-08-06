package com.firstagent.backend.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Comparaison tolérante de chaînes (noms, prénoms) utilisée pour rapprocher les données
 * extraites d'une CNI de valeurs saisies ou de référence, en absorbant les différences
 * d'accents, de casse et les petites fautes de frappe.
 */
public final class StringSimilarity {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALNUM_SPACE = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern MULTI_SPACE = Pattern.compile(" +");

    private StringSimilarity() {
    }

    /**
     * Retourne un score de similarité entre 0.0 (aucun rapport) et 1.0 (identique)
     * basé sur la distance de Levenshtein normalisée, après normalisation des deux valeurs.
     */
    public static double similarity(String a, String b) {
        String normalizedA = normalize(a);
        String normalizedB = normalize(b);

        if (normalizedA.isEmpty() && normalizedB.isEmpty()) {
            return 1.0;
        }
        if (normalizedA.isEmpty() || normalizedB.isEmpty()) {
            return 0.0;
        }
        if (normalizedA.equals(normalizedB)) {
            return 1.0;
        }

        int distance = levenshteinDistance(normalizedA, normalizedB);
        int maxLength = Math.max(normalizedA.length(), normalizedB.length());
        return 1.0 - ((double) distance / maxLength);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        String upper = withoutDiacritics.toUpperCase();
        String alnumOnly = NON_ALNUM_SPACE.matcher(upper).replaceAll(" ");
        return MULTI_SPACE.matcher(alnumOnly).replaceAll(" ").trim();
    }

    private static int levenshteinDistance(String a, String b) {
        int[] previousRow = new int[b.length() + 1];
        int[] currentRow = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            currentRow[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                currentRow[j] = Math.min(
                    Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                    previousRow[j - 1] + cost
                );
            }
            System.arraycopy(currentRow, 0, previousRow, 0, currentRow.length);
        }

        return previousRow[b.length()];
    }
}
