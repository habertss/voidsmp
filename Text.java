package fr.smpskills;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    public static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> c(List<String> list) {
        List<Component> out = new ArrayList<>();
        for (String s : list) out.add(c(s));
        return out;
    }

    public static String bar(double ratio, int size) {
        ratio = Math.max(0, Math.min(1, ratio));
        int filled = (int) Math.round(ratio * size);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < size; i++) {
            if (i == filled) sb.append("&8");
            sb.append("|");
        }
        return sb.toString();
    }
}
