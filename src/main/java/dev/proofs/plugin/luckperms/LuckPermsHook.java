package dev.proofs.plugin.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Хук к LuckPerms. Плагин работает и без LuckPerms (fallback на обычные права Bukkit),
 * но при наличии LuckPerms доступны:
 *  - /proofs editor              — веб-редактор прав (открывает /lp editor);
 *  - /proofs lp setgroup <группа>   — выдаёт все права Proofs выбранной группе;
 *  - /proofs lp cleargroup <группа> — снимает все права Proofs у группы.
 */
public final class LuckPermsHook {

    /** Все права плагина, которые можно назначать через редактор/группы. */
    public static final String[] PERMISSIONS = {
            "proofs.use",         // доступ к /proofs и меню
            "proofs.admin",       // уведомления о проверках
            "proofs.check.start", // вызывать игрока на проверку
            "proofs.check.end",   // завершать проверки
            "proofs.spectate",    // кнопка «Спек» в меню
            "proofs.editor"       // доступ к редактору прав (/proofs editor)
    };

    private final Plugin plugin;
    private LuckPerms luckPerms;

    public LuckPermsHook(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Пытается подключиться к LuckPerms. Возвращает true, если LuckPerms установлен. */
    public boolean hook() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return false;
        }
        try {
            luckPerms = LuckPermsProvider.get();
            plugin.getLogger().info("Интеграция с LuckPerms включена.");
            return true;
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("LuckPerms найден, но API недоступен: " + e.getMessage());
            return false;
        }
    }

    public boolean isAvailable() {
        return luckPerms != null;
    }

    /** Открывает веб-редактор прав LuckPerms от имени отправителя. */
    public boolean openEditor(CommandSender sender) {
        if (!isAvailable()) return false;
        // Делегируем встроенной команде /lp editor (требует право luckperms.editor у отправителя).
        return Bukkit.dispatchCommand(sender, "lp editor");
    }

    /** Выдаёт все права Proofs группе с указанным именем. */
    public boolean setGroupPermissions(String groupName) {
        if (!isAvailable()) return false;
        Group group = luckPerms.getGroupManager().getGroup(groupName.toLowerCase());
        if (group == null) return false;
        for (String perm : PERMISSIONS) {
            group.data().add(PermissionNode.builder(perm).build());
        }
        luckPerms.getGroupManager().saveGroup(group);
        return true;
    }

    /** Снимает все права Proofs у группы. */
    public boolean clearGroupPermissions(String groupName) {
        if (!isAvailable()) return false;
        Group group = luckPerms.getGroupManager().getGroup(groupName.toLowerCase());
        if (group == null) return false;
        group.data().clear(node -> node instanceof PermissionNode pn
                && pn.getKey().startsWith("proofs."));
        luckPerms.getGroupManager().saveGroup(group);
        return true;
    }

    /** Список имён групп LuckPerms (для tab-completion). */
    public java.util.List<String> getGroupNames() {
        if (!isAvailable()) return java.util.List.of();
        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .map(Group::getName)
                .sorted()
                .toList();
    }
}
