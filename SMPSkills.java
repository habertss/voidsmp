package fr.smpskills;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SMPSkills extends JavaPlugin {
    private DataManager data;
    private SkillManager skills;
    private SkillsGUI gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        data = new DataManager(this);
        skills = new SkillManager(this);
        gui = new SkillsGUI(this);

        SkillListener listener = new SkillListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(gui, this);
        listener.startPerkTask();

        SkillsCommand cmd = new SkillsCommand(this);
        getCommand("skills").setExecutor(cmd);
        getCommand("skills").setTabCompleter(cmd);

        // Sauvegarde automatique toutes les 5 minutes
        getServer().getScheduler().runTaskTimer(this, () -> data.saveAll(), 6000L, 6000L);

        // Rechargement à chaud : charger les joueurs déjà connectés
        for (Player p : getServer().getOnlinePlayers()) {
            data.load(p);
            skills.refreshAttributes(p);
        }
        getLogger().info("SMPSkills activé !");
    }

    @Override
    public void onDisable() {
        if (data != null) data.saveAll();
    }

    public DataManager data() { return data; }
    public SkillManager skills() { return skills; }
    public SkillsGUI gui() { return gui; }
}
