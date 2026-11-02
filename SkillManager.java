package fr.smpskills;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SkillManager {
    private final SMPSkills plugin;

    public SkillManager(SMPSkills plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public int maxLevel() {
        return cfg().getInt("max-level", 100);
    }

    public double xpForNext(int level) {
        return cfg().getDouble("xp-formula.base", 100) + (double) level * level * cfg().getDouble("xp-formula.multiplier", 8);
    }

    public double xpValue(Skill s, String key) {
        return cfg().getDouble("xp." + s.key() + "." + key, 0);
    }

    public boolean hasXpEntry(Skill s, Material m) {
        return cfg().contains("xp." + s.key() + "." + m.name());
    }

    public String msg(String path) {
        return cfg().getString("messages.prefix", "") + cfg().getString("messages." + path, "");
    }

    // ------------------------------------------------------------------ XP

    public void addXp(Player p, Skill s, double amount) {
        if (amount <= 0) return;
        PlayerData d = plugin.data().get(p);
        int max = maxLevel();
        if (d.level(s) >= max) return;

        amount *= cfg().getDouble("xp-multiplier", 1.0);
        d.addXp(s, amount);

        boolean leveled = false;
        while (d.level(s) < max && d.xp(s) >= xpForNext(d.level(s))) {
            d.setXp(s, d.xp(s) - xpForNext(d.level(s)));
            d.setLevel(s, d.level(s) + 1);
            leveled = true;
            levelUp(p, s, d.level(s));
        }
        if (d.level(s) >= max) d.setXp(s, 0);

        if (!leveled && cfg().getBoolean("action-bar", true)) {
            int lvl = d.level(s);
            String line = s.color() + "&l" + s.display() + " &e" + lvl
                    + " &7(+" + String.format(Locale.US, "%.1f", amount) + " xp) &8| &a"
                    + (int) d.xp(s) + "&7/&a" + (int) xpForNext(lvl);
            p.sendActionBar(Text.c(line));
        }
    }

    public void setLevel(Player p, Skill s, int level) {
        PlayerData d = plugin.data().get(p);
        d.setLevel(s, Math.max(0, Math.min(maxLevel(), level)));
        d.setXp(s, 0);
        refreshAttributes(p);
    }

    private void levelUp(Player p, Skill s, int level) {
        String m = msg("level-up").replace("{skill}", s.color() + s.display()).replace("{level}", String.valueOf(level));
        p.sendMessage(Text.c(m));
        if (cfg().getBoolean("level-up-effects", true)) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
        }
        if (level % 25 == 0) {
            Bukkit.broadcast(Text.c(cfg().getString("messages.prefix", "") + "&e" + p.getName()
                    + " &7a atteint le niveau &e" + level + " &7en " + s.color() + s.display() + "&7 !"));
        }
        if (s == Skill.COMBAT) refreshAttributes(p);
    }

    // ------------------------------------------------------------------ Perks

    public double doubleDropChance(Player p, Skill s) {
        double perLevel = cfg().getDouble("perks." + s.key() + ".double-drop-per-level", 0);
        return Math.min(100, plugin.data().get(p).level(s) * perLevel);
    }

    public double combatDamageBonus(Player p) {
        return plugin.data().get(p).level(Skill.COMBAT) * cfg().getDouble("perks.combat.damage-per-level", 0) / 100.0;
    }

    public double fishingSpeedReduction(Player p) {
        double red = plugin.data().get(p).level(Skill.FISHING) * cfg().getDouble("perks.fishing.speed-per-level", 0) / 100.0;
        return Math.min(0.75, red);
    }

    public double fishingTreasureChance(Player p) {
        return plugin.data().get(p).level(Skill.FISHING) * cfg().getDouble("perks.fishing.treasure-per-level", 0);
    }

    public int hasteAmplifier(Player p, Skill s) {
        int lvl = plugin.data().get(p).level(s);
        if (s == Skill.MINING) {
            if (lvl >= cfg().getInt("perks.mining.haste-2-level", 60)) return 1;
            if (lvl >= cfg().getInt("perks.mining.haste-1-level", 25)) return 0;
        } else if (s == Skill.WOODCUTTING) {
            if (lvl >= cfg().getInt("perks.woodcutting.haste-level", 30)) return 0;
        }
        return -1;
    }

    public boolean hasResistance(Player p) {
        return plugin.data().get(p).level(Skill.COMBAT) >= cfg().getInt("perks.combat.resistance-level", 50);
    }

    public boolean canAutoReplant(Player p) {
        return plugin.data().get(p).level(Skill.FARMING) >= cfg().getInt("perks.farming.auto-replant-level", 15);
    }

    public void refreshAttributes(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "combat_hearts");
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(key)) inst.removeModifier(m);
        }
        int every = cfg().getInt("perks.combat.heart-every-levels", 20);
        int hearts = every > 0 ? plugin.data().get(p).level(Skill.COMBAT) / every : 0;
        if (hearts > 0) {
            inst.addModifier(new AttributeModifier(key, hearts * 2.0, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    // ------------------------------------------------------------------ Description (GUI / info)

    public List<String> describePerks(Player p, Skill s) {
        int lvl = plugin.data().get(p).level(s);
        List<String> out = new ArrayList<>();
        switch (s) {
            case MINING -> {
                out.add("&7Double drop : &a" + fmt(doubleDropChance(p, s)) + "%");
                int h1 = cfg().getInt("perks.mining.haste-1-level", 25);
                int h2 = cfg().getInt("perks.mining.haste-2-level", 60);
                out.add(lvl >= h2 ? "&7Célérité : &aII" : lvl >= h1 ? "&7Célérité : &aI &8(II au niv. " + h2 + ")"
                        : "&7Célérité : &8verrouillée (niv. " + h1 + ")");
            }
            case WOODCUTTING -> {
                out.add("&7Double drop : &a" + fmt(doubleDropChance(p, s)) + "%");
                int h = cfg().getInt("perks.woodcutting.haste-level", 30);
                out.add(lvl >= h ? "&7Célérité à la hache : &aI" : "&7Célérité à la hache : &8verrouillée (niv. " + h + ")");
            }
            case FARMING -> {
                out.add("&7Double récolte : &a" + fmt(doubleDropChance(p, s)) + "%");
                int r = cfg().getInt("perks.farming.auto-replant-level", 15);
                out.add(lvl >= r ? "&7Replantation auto : &aActive" : "&7Replantation auto : &8verrouillée (niv. " + r + ")");
            }
            case COMBAT -> {
                out.add("&7Dégâts bonus : &a+" + fmt(combatDamageBonus(p) * 100) + "%");
                int every = cfg().getInt("perks.combat.heart-every-levels", 20);
                out.add("&7Cœurs bonus : &a+" + (every > 0 ? lvl / every : 0) + " &8(1 tous les " + every + " niv.)");
                int r = cfg().getInt("perks.combat.resistance-level", 50);
                out.add(lvl >= r ? "&7Résistance passive : &aActive" : "&7Résistance passive : &8verrouillée (niv. " + r + ")");
            }
            case FISHING -> {
                out.add("&7Pêche plus rapide : &a-" + fmt(fishingSpeedReduction(p) * 100) + "% d'attente");
                out.add("&7Chance de trésor : &a" + fmt(fishingTreasureChance(p)) + "%");
            }
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
