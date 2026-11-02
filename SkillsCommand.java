package fr.smpskills;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SkillsCommand implements CommandExecutor, TabCompleter {
    private final SMPSkills plugin;

    public SkillsCommand(SMPSkills plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    private void send(CommandSender s, String msg) {
        s.sendMessage(Text.c(prefix() + msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                send(sender, "&cCommande réservée aux joueurs.");
                return true;
            }
            plugin.gui().open(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> {
                Skill s = args.length > 1 ? Skill.from(args[1]) : null;
                if (s == null) {
                    send(sender, "&cUsage : /" + label + " top <" + skillNames() + ">");
                    return true;
                }
                sendTop(plugin, sender, s);
            }
            case "info" -> {
                if (!(sender instanceof Player p)) return true;
                Skill s = args.length > 1 ? Skill.from(args[1]) : null;
                if (s == null) {
                    send(sender, "&cUsage : /" + label + " info <" + skillNames() + ">");
                    return true;
                }
                PlayerData d = plugin.data().get(p);
                p.sendMessage(Text.c("&8&m      &r " + s.coloredName() + " &8&m      "));
                p.sendMessage(Text.c("&7Niveau : &e" + d.level(s) + "&7/&e" + plugin.skills().maxLevel()));
                if (d.level(s) < plugin.skills().maxLevel()) {
                    p.sendMessage(Text.c("&7XP : &a" + (int) d.xp(s) + "&7/&a" + (int) plugin.skills().xpForNext(d.level(s))));
                }
                for (String line : plugin.skills().describePerks(p, s)) p.sendMessage(Text.c(" " + line));
            }
            case "admin" -> admin(sender, label, args);
            case "reload" -> {
                if (!sender.hasPermission("smpskills.admin")) {
                    sender.sendMessage(Text.c(plugin.skills().msg("no-permission")));
                    return true;
                }
                plugin.reloadConfig();
                send(sender, "&aConfiguration rechargée.");
            }
            default -> send(sender, "&cUsage : /" + label + " [top|info|admin|reload]");
        }
        return true;
    }

    private void admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("smpskills.admin")) {
            sender.sendMessage(Text.c(plugin.skills().msg("no-permission")));
            return;
        }
        // /skills admin <set|add|addxp|reset> <joueur> <skill> [valeur]
        if (args.length < 4) {
            send(sender, "&cUsage : /" + label + " admin <set|add|addxp|reset> <joueur> <skill> [valeur]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, "&cJoueur introuvable (il doit être en ligne).");
            return;
        }
        Skill s = Skill.from(args[3]);
        if (s == null) {
            send(sender, plugin.getConfig().getString("messages.unknown-skill", "&cCompétence inconnue."));
            return;
        }

        int value = 0;
        if (!action.equals("reset")) {
            if (args.length < 5) {
                send(sender, "&cIl manque la valeur.");
                return;
            }
            try {
                value = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                send(sender, "&cValeur invalide.");
                return;
            }
        }

        PlayerData d = plugin.data().get(target);
        switch (action) {
            case "set" -> plugin.skills().setLevel(target, s, value);
            case "add" -> plugin.skills().setLevel(target, s, d.level(s) + value);
            case "addxp" -> plugin.skills().addXp(target, s, value);
            case "reset" -> plugin.skills().setLevel(target, s, 0);
            default -> {
                send(sender, "&cAction inconnue (set, add, addxp, reset).");
                return;
            }
        }
        send(sender, "&a" + s.display() + " de " + target.getName() + " : niveau " + d.level(s));
    }

    public static void sendTop(SMPSkills plugin, CommandSender to, Skill s) {
        plugin.data().saveAll();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerData> all = plugin.data().loadAll();
            all.sort(Comparator.<PlayerData>comparingInt(d -> d.level(s))
                    .thenComparingDouble(d -> d.xp(s)).reversed());
            Bukkit.getScheduler().runTask(plugin, () -> {
                to.sendMessage(Text.c("&8&m      &r &6&lTop " + s.color() + s.display() + " &8&m      "));
                int rank = 1;
                for (PlayerData d : all) {
                    if (rank > 10) break;
                    if (d.level(s) <= 0 && d.xp(s) <= 0) continue;
                    String name = d.name();
                    to.sendMessage(Text.c("&e#" + rank + " &f" + name + " &7- niveau &a" + d.level(s)));
                    rank++;
                }
                if (rank == 1) to.sendMessage(Text.c("&7Personne n'est encore classé."));
            });
        });
    }

    private static String skillNames() {
        return String.join("|", Arrays.stream(Skill.values()).map(Skill::key).toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("top", "info"));
            if (sender.hasPermission("smpskills.admin")) out.addAll(List.of("admin", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("info"))) {
            for (Skill s : Skill.values()) out.add(s.key());
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("smpskills.admin")) {
            if (args.length == 2) out.addAll(List.of("set", "add", "addxp", "reset"));
            else if (args.length == 3) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            else if (args.length == 4) for (Skill s : Skill.values()) out.add(s.key());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
