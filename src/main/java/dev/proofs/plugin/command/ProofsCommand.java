package dev.proofs.plugin.command;

import dev.proofs.plugin.check.CheckManager;
import dev.proofs.plugin.luckperms.LuckPermsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Реализует команду /proofs:
 *  - /proofs                    — открывает меню с головами игроков онлайн;
 *  - /proofs <ник>              — вызывает игрока на проверку (как кнопка "Вызвать");
 *  - /proofs end <ник>          — завершает проверку игрока;
 *  - /proofs stop               — завершает все активные проверки;
 *  - /proofs editor             — веб-редактор прав LuckPerms (/lp editor);
 *  - /proofs lp setgroup <группа>   — выдать все права Proofs группе LuckPerms;
 *  - /proofs lp cleargroup <группа> — снять все права Proofs у группы LuckPerms.
 */
public class ProofsCommand implements CommandExecutor, TabCompleter {

    private final CheckManager checkManager;
    private final ProofsMenus menus;
    private final LuckPermsHook luckPerms;

    public ProofsCommand(CheckManager checkManager, ProofsMenus menus, LuckPermsHook luckPerms) {
        this.checkManager = checkManager;
        this.menus = menus;
        this.luckPerms = luckPerms;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("proofs.use")) {
            sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            menus.openMainMenu(admin);
            return true;
        }

        // /proofs end <ник>
        if (args[0].equalsIgnoreCase("end")) {
            if (!admin.hasPermission("proofs.check.end")) {
                admin.sendMessage(Component.text("Недостаточно прав (proofs.check.end).",
                        NamedTextColor.RED));
                return true;
            }
            if (args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    admin.sendMessage(Component.text("Игрок не найден или не в сети.",
                            NamedTextColor.RED));
                    return true;
                }
                if (!checkManager.isChecking(target.getUniqueId())) {
                    admin.sendMessage(Component.text("Этот игрок не проверяется.",
                            NamedTextColor.YELLOW));
                    return true;
                }
                checkManager.endCheck(target.getUniqueId(), false);
                admin.sendMessage(Component.text("Проверка игрока ", NamedTextColor.GREEN)
                        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" завершена.", NamedTextColor.GREEN)));
            } else {
                admin.sendMessage(Component.text("Использование: /proofs end <ник>",
                        NamedTextColor.YELLOW));
            }
            return true;
        }

        // /proofs stop — завершить все проверки
        if (args[0].equalsIgnoreCase("stop")) {
            checkManager.endAllChecks(false);
            admin.sendMessage(Component.text("Все активные проверки завершены.",
                    NamedTextColor.GREEN));
            return true;
        }

        // /proofs editor — веб-редактор прав LuckPerms
        if (args[0].equalsIgnoreCase("editor")) {
            handleEditor(sender, "proofs.editor");
            return true;
        }

        // /proofs lp setgroup|cleargroup <группа> — массовая выдача/снятие прав Proofs
        if (args[0].equalsIgnoreCase("lp")) {
            handleLp(sender, args);
            return true;
        }

        // /proofs <ник> — вызвать на проверку
        String name = args[0];
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            admin.sendMessage(Component.text("Игрок «" + name + "» не найден или не в сети.",
                    NamedTextColor.RED));
            return true;
        }
        if (target.getUniqueId().equals(admin.getUniqueId())) {
            admin.sendMessage(Component.text("Нельзя вызвать самого себя на проверку.",
                    NamedTextColor.RED));
            return true;
        }
        if (!admin.hasPermission("proofs.check.start")) {
            admin.sendMessage(Component.text("Недостаточно прав (proofs.check.start).",
                    NamedTextColor.RED));
            return true;
        }

        if (checkManager.startCheck(target)) {
            admin.sendMessage(Component.text("Вы вызвали ", NamedTextColor.GREEN)
                    .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" на проверку!", NamedTextColor.GREEN)));
        } else {
            admin.sendMessage(Component.text("Этот игрок уже проверяется.",
                    NamedTextColor.RED));
        }
        return true;
    }

    private void handleEditor(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Component.text("Недостаточно прав (" + permission + ").",
                    NamedTextColor.RED));
            return;
        }
        if (!luckPerms.isAvailable()) {
            sender.sendMessage(Component.text("LuckPerms не установлен — редактор прав недоступен.",
                    NamedTextColor.RED));
            return;
        }
        luckPerms.openEditor(sender);
    }

    private void handleLp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("proofs.editor")) {
            sender.sendMessage(Component.text("Недостаточно прав (proofs.editor).",
                    NamedTextColor.RED));
            return;
        }
        if (!luckPerms.isAvailable()) {
            sender.sendMessage(Component.text("LuckPerms не установлен.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Использование: /proofs lp <setgroup|cleargroup> <группа>",
                    NamedTextColor.YELLOW));
            return;
        }
        String op = args[1].toLowerCase();
        String group = args[2];
        if (op.equals("setgroup")) {
            if (luckPerms.setGroupPermissions(group)) {
                sender.sendMessage(Component.text("Группе ", NamedTextColor.GREEN)
                        .append(Component.text(group, NamedTextColor.YELLOW))
                        .append(Component.text(" выданы все права Proofs.", NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text("Группа не найдена: " + group,
                        NamedTextColor.RED));
            }
        } else if (op.equals("cleargroup")) {
            if (luckPerms.clearGroupPermissions(group)) {
                sender.sendMessage(Component.text("У группы ", NamedTextColor.GREEN)
                        .append(Component.text(group, NamedTextColor.YELLOW))
                        .append(Component.text(" сняты все права Proofs.", NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text("Группа не найдена: " + group,
                        NamedTextColor.RED));
            }
        } else {
            sender.sendMessage(Component.text(
                    "Неизвестная операция. Используйте: setgroup | cleargroup",
                    NamedTextColor.RED));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("end", "stop", "editor", "lp").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
            return java.util.stream.Stream.concat(subs.stream(), names.stream()).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lp")) {
            return List.of("setgroup", "cleargroup").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lp")
                && (args[1].equalsIgnoreCase("setgroup") || args[1].equalsIgnoreCase("cleargroup"))) {
            String prefix = args[2].toLowerCase();
            return luckPerms.getGroupNames().stream()
                    .filter(g -> g.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
