package dev.proofs.plugin.command;

import dev.proofs.plugin.check.CheckManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

/**
 * GUI-меню плагина:
 *  1. Главное меню "Proofs" — головы всех игроков онлайн.
 *  2. Меню игрока "Proofs: <ник>" — кнопки "Вызвать на проверку" и "Спек".
 *
 * Клики обрабатываются через PersistentDataContainer предметов,
 * поэтому не зависят от языка/названия меню.
 */
public final class ProofsMenus implements Listener {

    private static final String KEY_ACTION = "proofs_action";
    private static final String KEY_TARGET = "proofs_target";

    private static final String ACTION_HEAD = "head";
    private static final String ACTION_CALL = "call";
    private static final String ACTION_SPECTATE = "spectate";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_REFRESH = "refresh";

    private final Plugin plugin;
    private final CheckManager checkManager;

    public ProofsMenus(Plugin plugin, CheckManager checkManager) {
        this.plugin = plugin;
        this.checkManager = checkManager;
    }

    // ==================== Открытие меню ====================

    /** Главное меню со всеми игроками онлайн. */
    public void openMainMenu(Player admin) {
        List<Player> online = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(admin.getUniqueId())) online.add(p);
        }

        int size = Math.max(9, (int) Math.ceil((online.size() + 1) / 9.0) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size,
                Component.text("Proofs", NamedTextColor.DARK_AQUA));

        for (Player target : online) {
            if (inv.getSize() <= inv.firstEmpty() + 1 && inv.firstEmpty() == -1) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.YELLOW));
            meta.lore(List.of(
                    Component.text("Клик — открыть действия", NamedTextColor.GRAY),
                    checkManager.isChecking(target.getUniqueId())
                            ? Component.text("Статус: уже проверяется", NamedTextColor.RED)
                            : Component.text("Статус: не проверяется", NamedTextColor.GREEN)
            ));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, KEY_ACTION),
                    PersistentDataType.STRING, ACTION_HEAD);
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, KEY_TARGET),
                    PersistentDataType.STRING, target.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.addItem(head);
        }

        // Кнопка обновления меню.
        ItemStack refresh = makeButton(Material.CLOCK, "Обновить список",
                NamedTextColor.AQUA, ACTION_REFRESH);
        inv.setItem(size - 1, refresh);

        admin.openInventory(inv);
    }

    /** Меню действий по выбранному игроку. */
    public void openPlayerMenu(Player admin, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            admin.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
            openMainMenu(admin);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Proofs: ", NamedTextColor.DARK_AQUA)
                        .append(Component.text(target.getName(), NamedTextColor.YELLOW)));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta hmeta = (SkullMeta) head.getItemMeta();
        hmeta.setOwningPlayer(target);
        hmeta.displayName(Component.text(target.getName(), NamedTextColor.GOLD));
        head.setItemMeta(hmeta);
        inv.setItem(13, head);

        // Кнопка "Вызвать" — запускает проверку.
        ItemStack call = makeButton(Material.LIME_DYE,
                Component.text("Вызвать на проверку", NamedTextColor.GREEN),
                List.of(Component.text("или /proofs " + target.getName(), NamedTextColor.GRAY)),
                ACTION_CALL, targetId);
        inv.setItem(11, call);

        // Кнопка "Спек" — телепорт админа к игроку в режиме наблюдателя.
        ItemStack spectate = makeButton(Material.ENDER_EYE,
                Component.text("Спек", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("Телепорт к игроку в режиме", NamedTextColor.GRAY)),
                ACTION_SPECTATE, targetId);
        inv.setItem(15, spectate);

        ItemStack back = makeButton(Material.ARROW,
                Component.text("Назад", NamedTextColor.RED),
                List.of(), ACTION_BACK, null);
        inv.setItem(22, back);

        admin.openInventory(inv);
    }

    // ==================== Фабрика кнопок ====================

    private ItemStack makeButton(Material material, String name, NamedTextColor color, String action) {
        return makeButton(material, Component.text(name, color), List.of(), action, null);
    }

    private ItemStack makeButton(Material material, Component name,
                                 List<Component> lore, String action, UUID targetId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, KEY_ACTION),
                PersistentDataType.STRING, action);
        if (targetId != null) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, KEY_TARGET),
                    PersistentDataType.STRING, targetId.toString());
        }
        item.setItemMeta(meta);
        return item;
    }

    // ==================== Обработка кликов ====================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.startsWith("Proofs")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, KEY_ACTION), PersistentDataType.STRING);
        if (action == null) return;

        String targetRaw = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, KEY_TARGET), PersistentDataType.STRING);
        UUID targetId = targetRaw != null ? UUID.fromString(targetRaw) : null;

        switch (action) {
            case ACTION_HEAD -> {
                if (targetId != null) openPlayerMenu(admin, targetId);
            }
            case ACTION_CALL -> {
                if (targetId != null) {
                    if (!admin.hasPermission("proofs.check.start")) {
                        admin.sendMessage(Component.text(
                                "Недостаточно прав (proofs.check.start).", NamedTextColor.RED));
                        return;
                    }
                    Player target = Bukkit.getPlayer(targetId);
                    if (target == null) {
                        admin.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
                        return;
                    }
                    if (checkManager.startCheck(target)) {
                        admin.sendMessage(Component.text("Вы вызвали ", NamedTextColor.GREEN)
                                .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                                .append(Component.text(" на проверку!", NamedTextColor.GREEN)));
                        admin.closeInventory();
                    } else {
                        admin.sendMessage(Component.text("Этот игрок уже проверяется.",
                                NamedTextColor.RED));
                    }
                }
            }
            case ACTION_SPECTATE -> {
                if (targetId != null) {
                    if (!admin.hasPermission("proofs.spectate")) {
                        admin.sendMessage(Component.text(
                                "Недостаточно прав (proofs.spectate).", NamedTextColor.RED));
                        return;
                    }
                    Player target = Bukkit.getPlayer(targetId);
                    if (target == null) {
                        admin.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
                        return;
                    }
                    admin.closeInventory();
                    admin.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    admin.teleport(target);
                    admin.sendMessage(Component.text("Вы телепортировались к ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" в режиме наблюдателя.", NamedTextColor.LIGHT_PURPLE)));
                }
            }
            case ACTION_BACK -> openMainMenu(admin);
            case ACTION_REFRESH -> openMainMenu(admin);
            default -> { }
        }
    }
}
