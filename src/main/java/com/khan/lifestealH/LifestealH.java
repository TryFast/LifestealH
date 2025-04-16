package com.khan.lifestealH;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class LifestealH extends JavaPlugin implements Listener {

    private File playerDataFile;
    private FileConfiguration playerData;
    private HashMap<UUID, Integer> playerKills = new HashMap<>();

    private final double DEFAULT_HEALTH = 20.0;
    private final double MIN_HEALTH = 14.0;
    private final double MAX_HEALTH = 40.0;
    private final int KILLS_PER_HEART = 3;
    private final double HEART_VALUE = 2.0;
    private final double MINIMUM_STEALABLE_HEALTH = 14.0;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create playerdata.yml!");
                e.printStackTrace();
            }
        }

        playerData = YamlConfiguration.loadConfiguration(playerDataFile);

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("addhearts").setExecutor(new AddHeartsCommand());
        getCommand("removeheart").setExecutor(new RemoveHeartCommand());

        loadKillsData();

        getLogger().info("LifestealH has been enabled!");
    }

    @Override
    public void onDisable() {
        saveKillsData();
        getLogger().info("LifestealH has been disabled!");
    }

    private void loadKillsData() {
        if (playerData.getConfigurationSection("kills") != null) {
            for (String uuidString : playerData.getConfigurationSection("kills").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                int kills = playerData.getInt("kills." + uuidString);
                playerKills.put(uuid, kills);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            double savedHealth = playerData.getDouble("health." + player.getUniqueId().toString(), DEFAULT_HEALTH);
            player.setMaxHealth(savedHealth);
        }
    }

    private void saveKillsData() {
        for (UUID uuid : playerKills.keySet()) {
            playerData.set("kills." + uuid.toString(), playerKills.get(uuid));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            playerData.set("health." + player.getUniqueId().toString(), player.getMaxHealth());
        }

        try {
            playerData.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save playerdata.yml!");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!playerKills.containsKey(playerUUID)) {
            playerKills.put(playerUUID, 0);
        }

        double savedHealth = playerData.getDouble("health." + playerUUID.toString(), DEFAULT_HEALTH);
        player.setMaxHealth(savedHealth);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            sendPlayerStats(player);
        }, 20L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            return;
        }

        double victimHealth = victim.getMaxHealth();

        getLogger().info("Victim " + victim.getName() + " has exact health value of: " + victimHealth);
        getLogger().info("MINIMUM_STEALABLE_HEALTH constant is: " + MINIMUM_STEALABLE_HEALTH);

        reducePlayerHealth(victim);

        if (killer != null && killer != victim) {
            if (victimHealth - MINIMUM_STEALABLE_HEALTH > 0.001) {
                getLogger().info("Kill point awarded to " + killer.getName());
                addKillPoint(killer);
            } else {
                getLogger().info("No kill point awarded - victim had 7 or fewer hearts");
                killer.sendMessage(ChatColor.YELLOW + "No kill point awarded - player had 7 or fewer hearts.");
            }
        }
    }

    private void reducePlayerHealth(Player player) {
        double currentMaxHealth = player.getMaxHealth();
        double newMaxHealth = Math.max(MIN_HEALTH, currentMaxHealth - HEART_VALUE);

        player.setMaxHealth(newMaxHealth);
        player.sendMessage(ChatColor.RED + "You lost a heart! Current max health: " +
                ChatColor.GOLD + (newMaxHealth / 2) + " hearts");

        playerData.set("health." + player.getUniqueId().toString(), newMaxHealth);
        saveKillsData();
    }

    private void addKillPoint(Player player) {
        UUID playerUUID = player.getUniqueId();
        double currentMaxHealth = player.getMaxHealth();

        if (currentMaxHealth < DEFAULT_HEALTH) {
            addPlayerHeart(player);
            return;
        }

        int currentKills = playerKills.getOrDefault(playerUUID, 0);
        currentKills++;
        playerKills.put(playerUUID, currentKills);

        player.sendMessage(ChatColor.GREEN + "Kill point gained! Current points: " +
                ChatColor.GOLD + currentKills + "/" + KILLS_PER_HEART);

        if (currentKills >= KILLS_PER_HEART) {
            addPlayerHeart(player);
            playerKills.put(playerUUID, currentKills - KILLS_PER_HEART);
        }

        saveKillsData();
    }

    private void addPlayerHeart(Player player) {
        double currentMaxHealth = player.getMaxHealth();

        if (currentMaxHealth >= MAX_HEALTH) {
            player.sendMessage(ChatColor.YELLOW + "You've reached the maximum of 20 hearts!");
            return;
        }

        double newMaxHealth = Math.min(MAX_HEALTH, currentMaxHealth + HEART_VALUE);

        player.setMaxHealth(newMaxHealth);
        player.sendMessage(ChatColor.GREEN + "Congratulations! You earned a heart! Current max health: " +
                ChatColor.GOLD + (newMaxHealth / 2) + " hearts");

        playerData.set("health." + player.getUniqueId().toString(), newMaxHealth);
        saveKillsData();
    }

    private void sendPlayerStats(Player player) {
        UUID playerUUID = player.getUniqueId();
        int kills = playerKills.getOrDefault(playerUUID, 0);
        double maxHealth = player.getMaxHealth();

        player.sendMessage(ChatColor.GOLD + "=== LifestealH Stats ===");
        player.sendMessage(ChatColor.YELLOW + "Max Health: " + ChatColor.WHITE + (maxHealth / 2) + " hearts");

        if (maxHealth < DEFAULT_HEALTH) {
            player.sendMessage(ChatColor.YELLOW + "Hearts Status: " + ChatColor.WHITE +
                    "You need only 1 kill to gain a heart!");
        } else if (maxHealth < MAX_HEALTH) {
            player.sendMessage(ChatColor.YELLOW + "Kill Points: " + ChatColor.WHITE + kills + "/" + KILLS_PER_HEART);
            player.sendMessage(ChatColor.YELLOW + "Kills needed for next heart: " + ChatColor.WHITE +
                    (KILLS_PER_HEART - kills));
        } else {
            player.sendMessage(ChatColor.YELLOW + "You've reached the maximum of 20 hearts!");
        }
    }

    private class AddHeartsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("lifestealh.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /addhearts <player> <amount>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online!");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    sender.sendMessage(ChatColor.RED + "Please enter a positive number of hearts!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Please enter a valid number!");
                return true;
            }

            double currentMaxHealth = target.getMaxHealth();
            double newMaxHealth = Math.min(MAX_HEALTH, currentMaxHealth + (amount * HEART_VALUE));

            target.setMaxHealth(newMaxHealth);
            target.sendMessage(ChatColor.GREEN + "An admin has given you " + amount +
                    " heart(s)! Current max health: " + ChatColor.GOLD + (newMaxHealth / 2) + " hearts");

            playerData.set("health." + target.getUniqueId().toString(), newMaxHealth);
            saveKillsData();

            sender.sendMessage(ChatColor.GREEN + "Added " + amount + " heart(s) to " + target.getName() +
                    "! Their current health is now " + (newMaxHealth / 2) + " hearts.");

            return true;
        }
    }

    private class RemoveHeartCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("lifestealh.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /removeheart <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online!");
                return true;
            }

            double currentMaxHealth = target.getMaxHealth();

            if (currentMaxHealth <= MIN_HEALTH) {
                sender.sendMessage(ChatColor.RED + target.getName() + " is already at minimum health (7 hearts)!");
                return true;
            }

            double newMaxHealth = Math.max(MIN_HEALTH, currentMaxHealth - HEART_VALUE);

            target.setMaxHealth(newMaxHealth);
            target.sendMessage(ChatColor.RED + "An admin has removed a heart! Current max health: " +
                    ChatColor.GOLD + (newMaxHealth / 2) + " hearts");

            playerData.set("health." + target.getUniqueId().toString(), newMaxHealth);
            saveKillsData();

            sender.sendMessage(ChatColor.GREEN + "Removed a heart from " + target.getName() +
                    "! Their current health is now " + (newMaxHealth / 2) + " hearts.");

            return true;
        }
    }
}