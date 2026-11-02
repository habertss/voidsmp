package fr.smpskills;

import org.bukkit.Material;

import java.text.Normalizer;
import java.util.Locale;

public enum Skill {
    MINING("mining", "Minage", "&b", Material.DIAMOND_PICKAXE, "minage", "mine"),
    WOODCUTTING("woodcutting", "Bûcheronnage", "&2", Material.DIAMOND_AXE, "bucheronnage", "bucheron", "bois"),
    FARMING("farming", "Agriculture", "&a", Material.DIAMOND_HOE, "agriculture", "ferme", "farm"),
    COMBAT("combat", "Combat", "&c", Material.DIAMOND_SWORD, "combat", "fight"),
    FISHING("fishing", "Pêche", "&9", Material.FISHING_ROD, "peche", "fish");

    private final String key;
    private final String display;
    private final String color;
    private final Material icon;
    private final String[] aliases;

    Skill(String key, String display, String color, Material icon, String... aliases) {
        this.key = key;
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.aliases = aliases;
    }

    public String key() { return key; }
    public String display() { return display; }
    public String color() { return color; }
    public Material icon() { return icon; }
    public String coloredName() { return color + "&l" + display; }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    public static Skill from(String input) {
        if (input == null) return null;
        String n = normalize(input);
        for (Skill s : values()) {
            if (s.key.equals(n)) return s;
            for (String a : s.aliases) if (a.equals(n)) return s;
        }
        return null;
    }
}
