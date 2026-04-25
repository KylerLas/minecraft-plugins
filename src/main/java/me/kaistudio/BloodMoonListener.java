package me.kaistudio;

import org.bukkit.Material;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.UUID;

public class BloodMoonListener implements Listener {

    private static final double BLOODMOON_HEALTH_MULT  = 1.35;
    private static final double BOSS_HEALTH_MULT       = 1.35 * 2.5; // 3.375x base
    private static final double BOSS_SPEED_MULT        = 1.20;
    private static final double BLOODMOON_DAMAGE_MULT  = 1.25;
    private static final double BOSS_DAMAGE_MULT       = 1.80;
    private static final int    BOSS_CHANCE            = 20;          // 1 in 20
    private static final double LOOT_MIN              = 1.5;
    private static final double LOOT_MAX              = 2.0;
    private static final double NUGGET_CHANCE_REGULAR = 0.10;
    private static final double NUGGET_CHANCE_BOSS    = 0.25;

    private final AnnouncePlugin plugin;
    private final Random random = new Random();

    public BloodMoonListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        BloodMoonManager manager = plugin.getBloodMoonManager();
        if (!manager.isBloodMoonActive()) return;

        // Only boost hostile mobs; skip plugin-spawned entities (bank teller, skulls, etc.)
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof Slime)) return;

        LivingEntity mob = (LivingEntity) event.getEntity();
        boolean isBoss = random.nextInt(BOSS_CHANCE) == 0;
        double healthMult = isBoss ? BOSS_HEALTH_MULT : BLOODMOON_HEALTH_MULT;

        Attribute maxHealthAttr = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));
        AttributeInstance healthAttr = maxHealthAttr != null ? mob.getAttribute(maxHealthAttr) : null;
        if (healthAttr != null) {
            double newMax = healthAttr.getBaseValue() * healthMult;
            healthAttr.setBaseValue(newMax);
            // Delay by 1 tick so the entity is fully placed in the world before setting current health
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid()) mob.setHealth(newMax);
            }, 1L);
        }

        // Attack damage — bosses use 1.8x, regular bloodmoon mobs use 1.25x
        double damageMult = isBoss ? BOSS_DAMAGE_MULT : BLOODMOON_DAMAGE_MULT;
        Attribute attackDmgAttr = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("attack_damage"));
        AttributeInstance damageAttr = attackDmgAttr != null ? mob.getAttribute(attackDmgAttr) : null;
        if (damageAttr != null) damageAttr.setBaseValue(damageAttr.getBaseValue() * damageMult);

        if (isBoss) {
            Attribute moveSpeedAttr = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ATTRIBUTE)
                .get(NamespacedKey.minecraft("movement_speed"));
            AttributeInstance speedAttr = moveSpeedAttr != null ? mob.getAttribute(moveSpeedAttr) : null;
            if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() * BOSS_SPEED_MULT);
            mob.setCustomName("§4☠ §c§lBOSS");
            mob.setCustomNameVisible(true);
        }

        manager.trackMob(mob.getUniqueId(), isBoss);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        BloodMoonManager manager = plugin.getBloodMoonManager();
        LivingEntity entity = event.getEntity();
        UUID uuid = entity.getUniqueId();

        boolean wasBloodMoonMob = manager.isBloodMoonMob(uuid);
        boolean wasBoss = manager.isBoss(uuid);
        if (wasBloodMoonMob) manager.untrackMob(uuid);

        if (!manager.isBloodMoonActive()) return;

        // Record kill for leaderboard
        Player killer = entity.getKiller();
        if (killer != null) manager.recordKill(killer, wasBoss);

        // Loot multiplier: 1.5x–2x per drop
        double mult = LOOT_MIN + random.nextDouble() * (LOOT_MAX - LOOT_MIN);
        for (ItemStack drop : event.getDrops()) {
            if (drop == null) continue;
            drop.setAmount(Math.min((int) Math.round(drop.getAmount() * mult), drop.getMaxStackSize()));
        }

        // Gold nugget bonus drop
        double nuggetChance = wasBoss ? NUGGET_CHANCE_BOSS : NUGGET_CHANCE_REGULAR;
        if (random.nextDouble() < nuggetChance) {
            int count = wasBoss
                ? (5 + random.nextInt(11))  // 5–15
                : (2 + random.nextInt(3));   // 2–4
            event.getDrops().add(new ItemStack(Material.GOLD_NUGGET, count));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        BloodMoonManager manager = plugin.getBloodMoonManager();
        if (manager.isBloodMoonActive()) {
            manager.applyBoardToPlayer(event.getPlayer());
        }
    }
}
