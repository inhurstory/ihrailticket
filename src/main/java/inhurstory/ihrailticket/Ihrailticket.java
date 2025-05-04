package inhurstory.ihrailticket;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Ihrailticket extends JavaPlugin {
    private Economy economy;
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("歡迎使用IHRail票務系統");

        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault 未安裝，插件無法運行");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            getLogger().severe("未找到經濟插件，插件無法運行");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("IHRail票務系統關機中");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;

        // ===== 確認傳送 =====
        if (label.equalsIgnoreCase("ihtpconfirm") && isPlayer) {
            UUID uuid = player.getUniqueId();
            if (!pendingTeleports.containsKey(uuid)) {
                player.sendMessage(ChatColor.RED + "你沒有待處理的旅行請求。");
                return true;
            }

            PendingTeleport pending = pendingTeleports.remove(uuid);
            if (economy.has(player, pending.cost)) {
                economy.withdrawPlayer(player, pending.cost);
                player.teleport(pending.destination);
                player.sendMessage(ChatColor.GREEN + "歡迎搭乘IHRail鐵路系統，正在前往車站編號 " + pending.warpName + "，車資: " + String.format("%.2f", pending.cost));
            } else {
                player.sendMessage(ChatColor.RED + "餘額不足，需 " + String.format("%.2f", pending.cost));
            }
            return true;
        }

        // ===== 取消傳送 =====
        if (label.equalsIgnoreCase("ihtpcancel") && isPlayer) {
            if (pendingTeleports.containsKey(player.getUniqueId())) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "已取消本次旅行請求。");
            } else {
                player.sendMessage(ChatColor.RED + "你沒有待處理的旅行請求。");
            }
            return true;
        }

        // ===== 主指令入口 =====
        if (!label.equalsIgnoreCase("ihrailticket") && !label.equalsIgnoreCase("ihrailgps") && !label.equalsIgnoreCase("iht")) {
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "請輸入子指令: gps 或 tp");
            return true;
        }

        String sub = args[0];

        if (sub.equalsIgnoreCase("gps")) {
            if (args.length == 2) {
                if (player != null && !player.hasPermission("ihrailticket.gps")) {
                    player.sendMessage(ChatColor.RED + "你沒有權限查看玩家座標。");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null && target.isOnline()) {
                    Location loc = target.getLocation();
                    sender.sendMessage(ChatColor.GREEN + "玩家 " + target.getName() + " 當前座標: " +
                            "X: " + loc.getBlockX() +
                            " Y: " + loc.getBlockY() +
                            " Z: " + loc.getBlockZ() +
                            " 世界: " + loc.getWorld().getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在線上或不存在。");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "用法: /iht gps <玩家名稱>");
            }
            return true;
        }

        if (sub.equalsIgnoreCase("tp")) {
            if (args.length == 2 && isPlayer) {
                if (!player.hasPermission("ihrailticket.tp.self")) {
                    player.sendMessage(ChatColor.RED + "你沒有IHRail鐵路系統使用權限");
                    return true;
                }

                String warpName = args[1];
                double[] coords = CmiWarpReader.getWarpLocation(warpName);
                if (coords == null) {
                    player.sendMessage(ChatColor.RED + "找不到目標車站 " + warpName);
                    return true;
                }

                Location dest = new Location(player.getWorld(), coords[0], coords[1], coords[2]);
                double distance = player.getLocation().distance(dest);
                double pricePerBlock = getConfig().getDouble("price_per_block", 1.0);
                double cost = distance * pricePerBlock;

                pendingTeleports.put(player.getUniqueId(), new PendingTeleport(warpName, cost, dest));
                player.spigot().sendMessage(getConfirmationMessage(cost));
                return true;
            }

            // 後台強制傳送他人
            if (args.length == 3) {
                if (!sender.hasPermission("ihrailticket.tp.others")) {
                    sender.sendMessage(ChatColor.RED + "你沒有權限操作IHRail鐵路系統運送其他玩家。");
                    return true;
                }

                String targetName = args[1];
                String warpName = args[2];
                Player target = Bukkit.getPlayer(targetName);

                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "找不到玩家 " + targetName);
                    return true;
                }

                double[] coords = CmiWarpReader.getWarpLocation(warpName);
                if (coords == null) {
                    sender.sendMessage(ChatColor.RED + "找不到目標車站 " + warpName);
                    return true;
                }

                Location dest = new Location(target.getWorld(), coords[0], coords[1], coords[2]);
                double distance = target.getLocation().distance(dest);
                double pricePerBlock = getConfig().getDouble("price_per_block", 1.0);
                double cost = distance * pricePerBlock;

                pendingTeleports.put(target.getUniqueId(), new PendingTeleport(warpName, cost, dest));
                target.spigot().sendMessage(getConfirmationMessage(cost));
                sender.sendMessage(ChatColor.GREEN + "已發送車票給 " + target.getName() + "，等待其確認。");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "用法: /iht tp <warp> 或 /iht tp <player> <warp>");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "未知指令: " + sub);
        return true;
    }

    private TextComponent getConfirmationMessage(double cost) {
        TextComponent message = new TextComponent("本次車資為 " + String.format("%.2f", cost) + " 元 ");
        TextComponent confirm = new TextComponent("[確認]");
        confirm.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ihtpconfirm"));

        TextComponent cancel = new TextComponent("[取消]");
        cancel.setColor(net.md_5.bungee.api.ChatColor.RED);
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ihtpcancel"));

        message.addExtra(" ");
        message.addExtra(confirm);
        message.addExtra(" ");
        message.addExtra(cancel);

        return message;
    }

    public static class PendingTeleport {
        public final String warpName;
        public final double cost;
        public final Location destination;

        public PendingTeleport(String warpName, double cost, Location destination) {
            this.warpName = warpName;
            this.cost = cost;
            this.destination = destination;
        }
    }
}
