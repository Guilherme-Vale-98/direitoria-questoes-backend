package com.direitoria.questoes.admin;

import java.text.Normalizer;
import java.util.Locale;

/** How the form's typed names are compared with, and stored as, canonicals. */
final class CanonicalNames {

    private CanonicalNames() {
    }

    /** Comparison key: accents stripped, lower-case, whitespace collapsed ("Fundação  X" ≡ "fundacao x"). */
    static String key(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** How a NEW canonical is stored: trimmed, inner whitespace collapsed, accents and case kept. */
    static String display(String name) {
        return name.replaceAll("\\s+", " ").trim();
    }
}
