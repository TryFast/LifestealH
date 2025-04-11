package com.khan.lifestealH;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private final double DEFAULT_HEALTH = 20.0; // 10 hearts (each heart is 2 health points)
    private final double MIN_HEALTH = 14.0; // 7 hearts
    private final double MAX_HEALTH = 40.0; // 20 hearts
    private final int KILLS_PER_HEART = 5;
    private final double HEART_VALUE = 2.0; // 1 heart = 2 health points

    @Override
    public void onEnable() {
        // Create configuration files
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

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Load player kills data
        loadKillsData();

        getLogger().info("LifestealH has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save data on shutdown
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

        // Load health data for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            double savedHealth = playerData.getDouble("health." + player.getUniqueId().toString(), DEFAULT_HEALTH);
            player.setMaxHealth(savedHealth);
        }
    }

    private void saveKillsData() {
        // Save kills data
        for (UUID uuid : playerKills.keySet()) {
            playerData.set("kills." + uuid.toString(), playerKills.get(uuid));
        }

        // Save health data for online players
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

        // Initialize kills counter for new players
        if (!playerKills.containsKey(playerUUID)) {
            playerKills.put(playerUUID, 0);
        }

        // Set player's max health from saved data or default
        double savedHealth = playerData.getDouble("health." + playerUUID.toString(), DEFAULT_HEALTH);
        player.setMaxHealth(savedHealth);

        // Send welcome message with current stats
        Bukkit.getScheduler().runTaskLater(this, () -> {
            sendPlayerStats(player);
        }, 20L); // Delay for 1 second
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Handle victim losing a heart
        reducePlayerHealth(victim);

        // Handle killer getting a kill point (if killed by another player)
        if (killer != null && killer != victim) {
            addKillPoint(killer);
        }
    }

    private void reducePlayerHealth(Player player) {
        double currentMaxHealth = player.getMaxHealth();
        double newMaxHealth = Math.max(MIN_HEALTH, currentMaxHealth - HEART_VALUE);

        player.setMaxHealth(newMaxHealth);
        player.sendMessage(ChatColor.RED + "You lost a heart! Current max health: " +
                ChatColor.GOLD + (newMaxHealth / 2) + " hearts");

        // Save the new health value
        playerData.set("health." + player.getUniqueId().toString(), newMaxHealth);
        saveKillsData();
    }

    private void addKillPoint(Player player) {
        UUID playerUUID = player.getUniqueId();
        int currentKills = playerKills.getOrDefault(playerUUID, 0);
        currentKills++;
        playerKills.put(playerUUID, currentKills);

        player.sendMessage(ChatColor.GREEN + "Kill point gained! Current points: " +
                ChatColor.GOLD + currentKills + "/" + KILLS_PER_HEART);

        // Check if player can earn a heart
        if (currentKills >= KILLS_PER_HEART) {
            addPlayerHeart(player);
            playerKills.put(playerUUID, currentKills - KILLS_PER_HEART);
        }

        // Save the updated kills data
        saveKillsData();
    }

    private void addPlayerHeart(Player player) {
        double currentMaxHealth = player.getMaxHealth();

        // Check if player has reached max health
        if (currentMaxHealth >= MAX_HEALTH) {
            player.sendMessage(ChatColor.YELLOW + "You've reached the maximum of 20 hearts!");
            return;
        }

        double newMaxHealth = Math.min(MAX_HEALTH, currentMaxHealth + HEART_VALUE);

        player.setMaxHealth(newMaxHealth);
        player.sendMessage(ChatColor.GREEN + "Congratulations! You earned a heart! Current max health: " +
                ChatColor.GOLD + (newMaxHealth / 2) + " hearts");

        // Save the new health value
        playerData.set("health." + player.getUniqueId().toString(), newMaxHealth);
    }

    private void sendPlayerStats(Player player) {
        UUID playerUUID = player.getUniqueId();
        int kills = playerKills.getOrDefault(playerUUID, 0);
        double maxHealth = player.getMaxHealth();

        player.sendMessage(ChatColor.GOLD + "=== LifestealH Stats ===");
        player.sendMessage(ChatColor.YELLOW + "Max Health: " + ChatColor.WHITE + (maxHealth / 2) + " hearts");
        player.sendMessage(ChatColor.YELLOW + "Kill Points: " + ChatColor.WHITE + kills + "/" + KILLS_PER_HEART);

        if (maxHealth < MAX_HEALTH) {
            player.sendMessage(ChatColor.YELLOW + "Kills needed for next heart: " + ChatColor.WHITE +
                    (KILLS_PER_HEART - kills));
        } else {
            player.sendMessage(ChatColor.YELLOW + "You've reached the maximum of 20 hearts!");
        }
    }
}