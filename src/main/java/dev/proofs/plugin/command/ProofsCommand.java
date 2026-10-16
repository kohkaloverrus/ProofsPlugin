package dev.proofs.plugin.command;

import dev.proofs.plugin.check.CheckManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Реализует команду /proofs:
 *  - /proofs           — открывает меню с головами игроков онлайн;
 *  - /proofs <ник>     — вызывает игрока на проверку (как кнопка "Вызвать");
 *  - /proofs end <ник> — завершает проверку игрока;
 *  - /proofs stop      — завершает все активные проверки.
 */
public class ProofsCommand implements CommandExecutor, TabCompleter {

    private final CheckManager checkManager;
    private final ProofsMenus menus;

    public ProofsCommand(CheckManager checkManager, ProofsMenus menus) {
        this.checkManager = checkManager;
        this.menus = menus;
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String low = args[0].toLowerCase();
            for (String sub : new String[]{"end", "stop"}) {
                if (sub.startsWith(low)) result.add(sub);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(low)) result.add(p.getName());
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
            List<String> names = new ArrayList<>();
            String low = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(low)) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
