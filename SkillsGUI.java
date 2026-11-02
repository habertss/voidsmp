package fr.smpskills;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SkillsGUI implements Listener {
    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    private final SMPSkills plugin;

    public SkillsGUI(SMPSkills plugin) {
        this.plugin = plugin;
    }

    public static class Holder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Text.c("&8Tes compétences"));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Text.c(" "));
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        PlayerData d = plugin.data().get(p);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(Text.c("&6&l" + p.getName()));
        sm.lore(Text.c(List.of("&7Niveau total : &e" + d.totalLevel()
                + "&7/&e" + plugin.skills().maxLevel() * Skill.values().length)));
        head.setItemMeta(sm);
        inv.setItem(4, head);

        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length; i++) {
            inv.setItem(SLOTS[i], skillItem(p, d, skills[i]));
        }
        p.openInventory(inv);
    }

    private ItemStack skillItem(Player p, PlayerData d, Skill s) {
        ItemStack item = new ItemStack(s.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(s.coloredName()));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());

        int lvl = d.level(s);
        int max = plugin.skills().maxLevel();
        List<String> lore = new ArrayList<>();
        lore.add("&7Niveau : &e" + lvl + "&7/&e" + max);
        if (lvl >= max) {
            lore.add("&6&lNIVEAU MAX !");
        } else {
            double need = plugin.skills().xpForNext(lvl);
            lore.add("&7XP : " + Text.bar(d.xp(s) / need, 20));
            lore.add("&8" + (int) d.xp(s) + " / " + (int) need);
        }
        lore.add("");
        lore.add("&6Bonus :");
        for (String line : plugin.skills().describePerks(p, s)) lore.add(" " + line);
        lore.add("");
        lore.add("&8Clic : voir le classement");
        meta.lore(Text.c(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        for (int i = 0; i < SLOTS.length; i++) {
            if (e.getSlot() == SLOTS[i]) {
                Skill s = Skill.values()[i];
                p.closeInventory();
                SkillsCommand.sendTop(plugin, p, s);
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
