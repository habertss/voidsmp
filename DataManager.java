package fr.smpskills;

import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {
    private final SMPSkills plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(SMPSkills plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    public PlayerData get(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), id -> PlayerData.load(file(id), id, p.getName()));
    }

    public PlayerData load(Player p) {
        PlayerData d = PlayerData.load(file(p.getUniqueId()), p.getUniqueId(), p.getName());
        d.setName(p.getName());
        cache.put(p.getUniqueId(), d);
        return d;
    }

    public void unload(Player p) {
        PlayerData d = cache.remove(p.getUniqueId());
        if (d != null) d.save(file(d.uuid()));
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) d.save(file(d.uuid()));
    }

    /** Lit tous les fichiers joueurs (à appeler hors du thread principal). */
    public List<PlayerData> loadAll() {
        List<PlayerData> list = new ArrayList<>();
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return list;
        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().replace(".yml", ""));
                list.add(PlayerData.load(f, id, "?"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return list;
    }
}
