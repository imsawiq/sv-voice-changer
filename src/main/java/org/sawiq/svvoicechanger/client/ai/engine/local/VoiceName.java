package org.sawiq.svvoicechanger.client.ai.engine.local;

import java.util.Locale;
import java.util.Map;

/**
 * Turns what a player types as a voice's name into one the engine accepts as
 * a file name: Latin letters, digits, {@code -} and {@code _}, at most 40.
 *
 * <p>Russian and Ukrainian names are spelled out in Latin letters rather than
 * dropped, so "Мой голос" becomes "moy-golos" and not an empty name.</p>
 */
public final class VoiceName {
    public static final int MAX_LENGTH = 40;

    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('ґ', "g"), Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "yo"),
            Map.entry('є', "ye"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('і', "i"), Map.entry('ї', "yi"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""),
            Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
            Map.entry('я', "ya"));

    private VoiceName() {
    }

    /** @return the engine-safe name, empty when nothing usable was typed */
    public static String sanitize(String typed) {
        StringBuilder name = new StringBuilder();
        for (char c : typed.strip().toLowerCase(Locale.ROOT).toCharArray()) {
            String latin = CYRILLIC.get(c);
            if (latin != null) {
                name.append(latin);
            } else if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                name.append(c);
            } else if ((c == '-' || Character.isWhitespace(c)) && !name.isEmpty() && name.charAt(name.length() - 1) != '-') {
                name.append('-');
            }
        }
        while (!name.isEmpty() && name.charAt(name.length() - 1) == '-') {
            name.setLength(name.length() - 1);
        }
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name.toString();
    }
}
