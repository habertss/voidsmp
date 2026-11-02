package fr.smpskills;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SkillListener implements Listener {
    private static final Set<Material> NON_AGEABLE_CROPS = Set.of(Material.SUGAR_CANE, Material.MELON, Material.PUMPKIN);
    private static final Set<Material> REPLANTABLE = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART);
    private static final Material[] TREASURES = {
            Material.EMERALD, Material.EMERALD, Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_INGOT,
            Material.NAME_TAG, Material.SADDLE, Material.EXPERIENCE_BOTTLE, Material.EXPERIENCE_BOTTLE,
            Material.IRON_INGOT, Material.IRON_INGOT, Material.DIAMOND
    };
    private static final int MAX_TRACKED = 100_000;

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block b) {
            return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final SMPSkills plugin;
    private final SkillManager sm;
    private final Set<BlockKey> placed = new HashSet<>();
    private final Map<UUID, BlockKey> placedBroken = new HashMap<>();

    public SkillListener(SMPSkills plugin) {
        this.plugin = plugin;
        this.sm = plugin.skills();
    }

    // ------------------------------------------------------------ join / quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().load(e.getPlayer());
        sm.refreshAttributes(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.data().unload(e.getPlayer());
        placedBroken.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ anti-abus : blocs posés

    private boolean isTracked(Material m) {
        return sm.hasXpEntry(Skill.MINING, m) || sm.hasXpEntry(Skill.WOODCUTTING, m) || NON_AGEABLE_CROPS.contains(m);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!isTracked(e.getBlock().getType())) return;
        if (placed.size() > MAX_TRACKED) placed.clear();
        placed.add(BlockKey.of(e.getBlock()));
    }

    // ------------------------------------------------------------ minage / bûcheronnage / agriculture

    private static boolean survival(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!survival(p)) return;

        Block b = e.getBlock();
        Material type = b.getType();
        BlockKey key = BlockKey.of(b);
        boolean wasPlaced = placed.remove(key);
        if (wasPlaced) {
            placedBroken.put(p.getUniqueId(), key);
            return;
        }

        double xp = sm.xpValue(Skill.MINING, type.name());
        if (xp > 0) {
            sm.addXp(p, Skill.MINING, xp);
            return;
        }

        xp = sm.xpValue(Skill.WOODCUTTING, type.name());
        if (xp > 0) {
            sm.addXp(p, Skill.WOODCUTTING, xp);
            return;
        }

        xp = sm.xpValue(Skill.FARMING, type.name());
        if (xp > 0 && isMatureCrop(b.getBlockData(), type)) {
            sm.addXp(p, Skill.FARMING, xp);
            if (REPLANTABLE.contains(type) && sm.canAutoReplant(p)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (b.getType() == Material.AIR) {
                        b.setType(type);
                        if (b.getBlockData() instanceof Ageable a) {
                            a.setAge(0);
                            b.setBlockData(a);
                        }
                    }
                });
            }
        }
    }

    private static boolean isMatureCrop(org.bukkit.block.data.BlockData data, Material type) {
        if (NON_AGEABLE_CROPS.contains(type)) return true;
        return data instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (!survival(p)) return;

        BlockState state = e.getBlockState();
        Material type = state.getType();
        BlockKey key = BlockKey.of(e.getBlock());
        BlockKey pending = placedBroken.get(p.getUniqueId());
        if (key.equals(pending)) {
            placedBroken.remove(p.getUniqueId());
            return;
        }

        Skill skill = null;
        if (sm.hasXpEntry(Skill.MINING, type)) skill = Skill.MINING;
        else if (sm.hasXpEntry(Skill.WOODCUTTING, type)) skill = Skill.WOODCUTTING;
        else if (sm.hasXpEntry(Skill.FARMING, type) && isMatureCrop(state.getBlockData(), type)) skill = Skill.FARMING;
        if (skill == null) return;

        double chance = sm.doubleDropChance(p, skill);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() * 100 >= chance) return;

        var loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (Item item : e.getItems()) {
            ItemStack copy = item.getItemStack().clone();
            e.getBlock().getWorld().dropItemNaturally(loc, copy);
        }
    }

    // ------------------------------------------------------------ combat

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || !survival(killer)) return;
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;

        double xp = sm.xpValue(Skill.COMBAT, dead.getType().name());
        if (xp <= 0 && dead instanceof Monster) xp = sm.xpValue(Skill.COMBAT, "default");
        if (xp <= 0) return;

        if (dead.getEntitySpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER) {
            xp *= plugin.getConfig().getDouble("spawner-xp-multiplier", 0.25);
        }
        sm.addXp(killer, Skill.COMBAT, xp);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = null;
        Entity damager = e.getDamager();
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) attacker = p;
        }
        if (attacker == null) return;
        if (e.getEntity() instanceof Player && !plugin.getConfig().getBoolean("pvp-bonus", false)) return;

        double bonus = sm.combatDamageBonus(attacker);
        if (bonus > 0) e.setDamage(e.getDamage() * (1 + bonus));
    }

    // ------------------------------------------------------------ pêche

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (e.getState() == PlayerFishEvent.State.FISHING) {
            double red = sm.fishingSpeedReduction(p);
            if (red > 0) {
                FishHook hook = e.getHook();
                hook.setMinWaitTime(Math.max(20, (int) (hook.getMinWaitTime() * (1 - red))));
                hook.setMaxWaitTime(Math.max(40, (int) (hook.getMaxWaitTime() * (1 - red))));
            }
            return;
        }
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        double xp = sm.xpValue(Skill.FISHING, "default");
        double chance = sm.fishingTreasureChance(p);
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            Material m = TREASURES[ThreadLocalRandom.current().nextInt(TREASURES.length)];
            int amount = m == Material.EXPERIENCE_BOTTLE ? 3 : 1;
            Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(m, amount));
            left.values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
            p.sendMessage(Text.c(plugin.getConfig().getString("messages.prefix", "") + "&bTrésor pêché ! &7(" + m.name().toLowerCase() + ")"));
            xp += sm.xpValue(Skill.FISHING, "treasure");
        }
        sm.addXp(p, Skill.FISHING, xp);
    }

    // ------------------------------------------------------------ tâche périodique (bonus passifs)

    public void startPerkTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    Material hand = p.getInventory().getItemInMainHand().getType();
                    if (Tag.ITEMS_PICKAXES.isTagged(hand)) {
                        int amp = sm.hasteAmplifier(p, Skill.MINING);
                        if (amp >= 0) applyHaste(p, amp);
                    } else if (Tag.ITEMS_AXES.isTagged(hand)) {
                        int amp = sm.hasteAmplifier(p, Skill.WOODCUTTING);
                        if (amp >= 0) applyHaste(p, amp);
                    }
                    if (sm.hasResistance(p)) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.RESISTANCE, 120, 0, true, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 80L);
    }

    private void applyHaste(Player p, int amp) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.HASTE, 120, amp, true, false, false));
    }
}
