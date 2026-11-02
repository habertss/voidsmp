package fr.smpskills;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final Map<Skill, Double> xp = new EnumMap<>(Skill.class);

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        for (Skill s : Skill.values()) {
            levels.put(s, 0);
            xp.put(s, 0.0);
        }
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public int level(Skill s) { return levels.get(s); }
    public void setLevel(Skill s, int level) { levels.put(s, level); }

    public double xp(Skill s) { return xp.get(s); }
    public void setXp(Skill s, double value) { xp.put(s, value); }
    public void addXp(Skill s, double value) { xp.put(s, xp.get(s) + value); }

    public int totalLevel() {
        int t = 0;
        for (int l : levels.values()) t += l;
        return t;
    }

    public static PlayerData load(File file, UUID uuid, String fallbackName) {
        PlayerData d = new PlayerData(uuid, fallbackName);
        if (!file.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        d.name = y.getString("name", fallbackName);
        for (Skill s : Skill.values()) {
            d.levels.put(s, y.getInt("skills." + s.key() + ".level", 0));
            d.xp.put(s, y.getDouble("skills." + s.key() + ".xp", 0));
        }
        return d;
    }

    public void save(File file) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        for (Skill s : Skill.values()) {
            y.set("skills." + s.key() + ".level", levels.get(s));
            y.set("skills." + s.key() + ".xp", xp.get(s));
        }
        try {
            y.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
