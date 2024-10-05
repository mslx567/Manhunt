package com.manhuntplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.*;


public class ManhuntPlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private List<UUID> runners = new ArrayList<>();
    private List<UUID> hunters = new ArrayList<>();
    private boolean gameStarted = false;
    private Map<UUID, Map<PotionEffectType, Integer>> hunterEffects = new HashMap<>();
    private Map<UUID, Map<PotionEffectType, Integer>> runnerEffects = new HashMap<>();
    private Map<UUID, ItemStack> hunterTrackers = new HashMap<>(); // ذخیره کامپس ترکر برای هر هانتر

    @Override
    public void onEnable() {
        getCommand("hunter").setExecutor(this);
        getCommand("runner").setExecutor(this);
        getCommand("mstop").setExecutor(this);
        getCommand("meffect").setExecutor(this);
        getCommand("meffect").setTabCompleter(this); // تب کامپلیتر برای meffect
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("hunter") && args.length == 2) {
            if (args[0].equalsIgnoreCase("add")) {
                Player player = Bukkit.getPlayer(args[1]);
                if (player != null) {
                    hunters.add(player.getUniqueId());
                    sender.sendMessage(ChatColor.GREEN + player.getName() + " has been added as a Hunter.");
                    giveTracker(player); // به محض اضافه شدن به عنوان هانتر، ترکر داده می‌شود
                }
            } else if (args[0].equalsIgnoreCase("remove")) {
                Player player = Bukkit.getPlayer(args[1]);
                if (player != null) {
                    hunters.remove(player.getUniqueId());
                    sender.sendMessage(ChatColor.RED + player.getName() + " has been removed from the Hunters.");
                }
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("runner") && args.length == 2) {
            if (args[0].equalsIgnoreCase("add")) {
                Player player = Bukkit.getPlayer(args[1]);
                if (player != null) {
                    runners.add(player.getUniqueId());
                    sender.sendMessage(ChatColor.GREEN + player.getName() + " has been added as a Runner.");
                }
            } else if (args[0].equalsIgnoreCase("remove")) {
                Player player = Bukkit.getPlayer(args[1]);
                if (player != null) {
                    runners.remove(player.getUniqueId());
                    sender.sendMessage(ChatColor.RED + player.getName() + " has been removed from the Runners.");
                }
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("mstop")) {
            gameStarted = false;
            runners.clear();
            hunters.clear();
            hunterTrackers.clear(); // پاک کردن تمام کامپس‌ها
            sender.sendMessage(ChatColor.YELLOW + "Manhunt has been stopped.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("meffect") && args.length >= 3) {
            String effectName = args[0].toUpperCase();
            PotionEffectType effectType = PotionEffectType.getByName(effectName);
            int power;

            try {
                power = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Power must be an integer.");
                return true;
            }

            String team = args[2].toLowerCase();

            if (effectType != null) {
                if (team.equals("runner")) {
                    applyEffectToTeam(runners, effectType, power, runnerEffects);
                } else if (team.equals("hunter")) {
                    applyEffectToTeam(hunters, effectType, power, hunterEffects);
                }
                sender.sendMessage(ChatColor.GREEN + "Effect applied to " + team + " team.");
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid effect type.");
            }
            return true;
        }

        return false;
    }

    private void applyEffectToTeam(List<UUID> team, PotionEffectType effectType, int power, Map<UUID, Map<PotionEffectType, Integer>> effectsMap) {
        for (UUID playerId : team) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.addPotionEffect(new PotionEffect(effectType, Integer.MAX_VALUE, power, true, false)); // دائمی
                effectsMap.putIfAbsent(playerId, new HashMap<>());
                effectsMap.get(playerId).put(effectType, power); // ذخیره قدرت افکت
            }
        }
    }

    private void giveTracker(Player hunter) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Runner Tracker");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Right click to track runner");
            meta.setLore(lore);
            compass.setItemMeta(meta);
        }
        hunter.getInventory().addItem(compass);
        hunterTrackers.put(hunter.getUniqueId(), compass); // ذخیره کامپس ترکر برای این هانتر
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.COMPASS && hunters.contains(player.getUniqueId())) {
            ItemStack tracker = hunterTrackers.get(player.getUniqueId());

            if (tracker != null && item.isSimilar(tracker)) { // فقط اگر کامپس همان ترکر اصلی باشد
                Location closestRunnerLocation = null;
                double closestDistance = Double.MAX_VALUE;

                for (UUID runnerId : runners) {
                    Player runner = Bukkit.getPlayer(runnerId);
                    if (runner != null) {
                        double distance = player.getLocation().distance(runner.getLocation());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestRunnerLocation = runner.getLocation();
                        }
                    }
                }

                if (closestRunnerLocation != null) {
                    player.setCompassTarget(closestRunnerLocation);
                    player.sendMessage(ChatColor.GREEN + "Tracking the nearest Runner.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        if (hunters.contains(player.getUniqueId())) {
            ItemStack tracker = hunterTrackers.get(player.getUniqueId());

            if (tracker != null && droppedItem.isSimilar(tracker)) {
                event.setCancelled(true); // جلوگیری از دراپ کردن کامپس ترکر
                player.sendMessage(ChatColor.RED + "You cannot drop the Runner Tracker!");
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        if (hunters.contains(playerId)) {
            player.getInventory().remove(Material.COMPASS); // Remove compass tracker
            hunterTrackers.remove(playerId); // Remove the tracker from the map
            removeEffects(player, hunterEffects.get(playerId));
        } else if (runners.contains(playerId)) {
            removeEffects(player, runnerEffects.get(playerId));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (hunters.contains(playerId)) {
            reapplyEffects(player, hunterEffects.get(playerId));
            giveTracker(player); // Give a new tracker on respawn
        } else if (runners.contains(playerId)) {
            reapplyEffects(player, runnerEffects.get(playerId));
        }
    }

    private void removeEffects(Player player, Map<PotionEffectType, Integer> effects) {
        if (effects != null) {
            for (PotionEffectType effect : effects.keySet()) {
                player.removePotionEffect(effect); // Remove effect
            }
        }
    }

    private void reapplyEffects(Player player, Map<PotionEffectType, Integer> effects) {
        if (effects != null) {
            for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
                player.addPotionEffect(new PotionEffect(entry.getKey(), Integer.MAX_VALUE, entry.getValue(), true, false)); // Reapply effect
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            // فقط وقتی که رانر به هانتر آسیب می‌زند، بازی را شروع کن
            if (runners.contains(attacker.getUniqueId()) && hunters.contains(victim.getUniqueId())) {
                startGame(); // اینجا متد startGame() را صدا می‌زنیم
            }
        }
    }

    private void startGame() {
        if (!gameStarted) {
            gameStarted = true;

            for (UUID runnerId : runners) {
                Player runner = Bukkit.getPlayer(runnerId);
                if (runner != null) {
                    runner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1)); // سرعت به رانر
                }
            }

            for (UUID hunterId : hunters) {
                Player hunter = Bukkit.getPlayer(hunterId);
                if (hunter != null) {
                    hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 1)); // کُندی به هانتر
                }
            }

            // افکت درخشان برای هر دو تیم
            for (UUID runnerId : runners) {
                Player runner = Bukkit.getPlayer(runnerId);
                if (runner != null) {
                    runner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 1)); // افکت درخشان به رانر
                }
            }

            for (UUID hunterId : hunters) {
                Player hunter = Bukkit.getPlayer(hunterId);
                if (hunter != null) {
                    hunter.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 1)); // افکت درخشان به هانتر
                }
            }

            Bukkit.broadcastMessage(ChatColor.GREEN + "The Manhunt has started!"); // پیام شروع بازی
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("meffect") && args.length == 1) {
            for (PotionEffectType effectType : PotionEffectType.values()) {
                if (effectType != null) {
                    completions.add(effectType.getName());
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("meffect") && args.length == 2) {
            completions.add("1");
            completions.add("2");
            completions.add("3");
            completions.add("4");
            completions.add("5");
        } else if (cmd.getName().equalsIgnoreCase("hunter") || cmd.getName().equalsIgnoreCase("runner")) {
            if (args.length == 1) {
                completions.add("add");
                completions.add("remove");
            } else if (args.length == 2) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }

}
