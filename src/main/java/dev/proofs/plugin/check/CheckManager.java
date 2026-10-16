package dev.proofs.plugin.check;

import dev.proofs.plugin.ProofsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет активными проверками игроков.
 *
 * Во время проверки:
 *  - каждые N секунд (по умолчанию 10) проверяемому показывается красный текст
 *    "Проверка на читы" (title + action bar), а в чат приходит сообщение
 *    "Напишите ваш discord или anydesk";
 *  - игрок получает неуязвимость;
 *  - запрещено ломать/ставить блоки, использовать предметы, менять слоты
 *    и взаимодействовать с инвентарём;
 *  - если игрок выходит во время проверки — он умирает и теряет все ресурсы.
 *
 * После окончания проверки все ограничения и эффекты снимаются.
 * Подсветка (glowing) игрока НЕ включается.
 */
public class CheckManager implements Listener {

    public static final Component CHECK_TITLE = Component.text("Проверка на читы", NamedTextColor.RED);

    public static final Component CHAT_REMINDER = Component.text("⚠ Напишите ваш discord или anydesk! ", NamedTextColor.RED);

    private final ProofsPlugin plugin;
    private final Map<UUID, ActiveCheck> checks = new HashMap<>();

    public CheckManager(ProofsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Состояние активной проверки игрока. */
    private static final class ActiveCheck {
        final UUID uuid;
        BukkitTask task;
        GameMode savedGameMode;
        boolean savedInvulnerable;
        int savedFoodLevel;
        float savedExhaustion;

        ActiveCheck(UUID uuid) {
            this.uuid = uuid;
        }
    }

    public boolean isChecking(UUID uuid) {
        return checks.containsKey(uuid);
    }

    /** Запускает проверку игрока. Возвращает false, если игрок уже проверяется. */
    public boolean startCheck(Player target) {
        if (target == null || !target.isOnline()) return false;
        if (isChecking(target.getUniqueId())) return false;

        ActiveCheck check = new ActiveCheck(target.getUniqueId());
        check.savedGameMode = target.getGameMode();
        check.savedInvulnerable = target.isInvulnerable();
        check.savedFoodLevel = target.getFoodLevel();
        check.savedExhaustion = target.getExhaustion();

        // Неуязвимость на время проверки. Подсветку (setGlowing) не трогаем.
        target.setInvulnerable(true);

        long periodTicks = 20L * Math.max(1,
                plugin.getConfig().getInt("check.reminder-interval-seconds", 10));

        check.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player p = plugin.getServer().getPlayer(check.uuid);
            if (p == null) return; // выход обрабатывается в onQuit
            showCheckTitle(p);
            p.sendMessage(CHAT_REMINDER);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
        }, 0L, periodTicks);

        checks.put(target.getUniqueId(), check);

        plugin.getComponentLogger().info("[Proofs] Начата проверка игрока {}", target.getName());
        notifyAdmins(Component.text("Начата проверка игрока ", NamedTextColor.YELLOW)
                .append(Component.text(target.getName(), NamedTextColor.RED)));
        return true;
    }

    /** Показывает красный текст "Проверка на читы" на экране. */
    public void showCheckTitle(Player p) {
        Title title = Title.title(
                CHECK_TITLE,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(4000), Duration.ofMillis(500)));
        p.showTitle(title);
        p.sendActionBar(CHECK_TITLE);
    }

    /**
     * Останавливает проверку.
     *
     * @param deathPenalty если true — игрок умирает и теряет всё имущество
     *                     (наказание за выход из игры во время проверки).
     */
    public void endCheck(UUID uuid, boolean deathPenalty) {
        ActiveCheck check = checks.remove(uuid);
        if (check == null) return;
        if (check.task != null) check.task.cancel();

        Player p = plugin.getServer().getPlayer(uuid);

        if (deathPenalty) {
            if (p != null) {
                dropAllItems(p);
                p.setInvulnerable(false);
                p.setHealth(0.0);
            }
            plugin.getComponentLogger().info(
                    "[Proofs] Игрок {} вышел во время проверки — смерть и потеря ресурсов.",
                    p != null ? p.getName() : uuid.toString());
            notifyAdmins(Component.text((p != null ? p.getName() : "?") + " вышел во время проверки и потерял все ресурсы", NamedTextColor.RED));
            return;
        }

        if (p != null) {
            p.setInvulnerable(check.savedInvulnerable);
            p.setFoodLevel(check.savedFoodLevel);
            p.setExhaustion(check.savedExhaustion);
            p.hideTitle();
            p.sendActionBar(Component.empty());
            p.sendMessage(Component.text("✔ Проверка завершена. Спасибо за ожидание!", NamedTextColor.GREEN));
        }
        plugin.getComponentLogger().info("[Proofs] Проверка игрока {} завершена.",
                p != null ? p.getName() : uuid.toString());
    }

    private void notifyAdmins(Component message) {
        Component prefix = Component.text("[Proofs] ", NamedTextColor.GOLD);
        for (Player admin : plugin.getServer().getOnlinePlayers()) {
            if (admin.hasPermission("proofs.admin")) {
                admin.sendMessage(prefix.append(message));
            }
        }
    }

    /** Останавливает все проверки (при выключении плагина). */
    public void endAllChecks(boolean deathPenalty) {
        for (UUID uuid : new HashMap<>(checks).keySet()) {
            endCheck(uuid, deathPenalty);
        }
    }

    // ==================== Блокировка действий во время проверки ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            endCheck(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // Во время проверки нельзя менять игровой режим (в т.ч. командой /gamemode).
        if (isChecking(event.getPlayer().getUniqueId())) {
            ActiveCheck c = checks.get(event.getPlayer().getUniqueId());
            if (c != null && event.getNewGameMode() != c.savedGameMode) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!isChecking(p.getUniqueId())) return;
        // Клики по меню самого плагина разрешены (например, кнопка "Завершить").
        if (isProofsMenu(event)) return;
        event.setCancelled(true);
        p.closeInventory();
        p.sendMessage(CHAT_REMINDER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (isChecking(p.getUniqueId()) && !isProofsMenu(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (!isChecking(p.getUniqueId())) return;
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.startsWith("Proofs")) {
            event.setCancelled(true);
        }
    }

    private boolean isProofsMenu(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        return title.startsWith("Proofs");
    }

    private boolean isProofsMenu(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        return title.startsWith("Proofs");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHotbarSwitch(PlayerSwapHandItemsEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!isChecking(p.getUniqueId())) return;
        if (event.getMaterial() == null) return;
        // ЛКМ по воздуху без предмета разрешаем.
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                && event.getMaterial().isAir()) {
            return;
        }
        event.setCancelled(true);
        showCheckTitle(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            showCheckTitle(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            showCheckTitle(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && isChecking(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (isChecking(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandExecute(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!isChecking(p.getUniqueId())) return;
        String cmd = event.getMessage().toLowerCase();
        // Разрешаем только команду proofs (для завершения/сообщений админу).
        if (cmd.startsWith("/proofs")) return;
        event.setCancelled(true);
        p.sendMessage(Component.text("Во время проверки использование команд запрещено!", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && isChecking(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Выбивает все предметы игрока в мир (используется при смерти-наказании). */
    private static void dropAllItems(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && !it.getType().isAir()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
                contents[i] = null;
            }
        }
        p.updateInventory();
    }
}
