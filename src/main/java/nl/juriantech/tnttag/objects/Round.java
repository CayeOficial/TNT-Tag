package nl.juriantech.tnttag.objects;

import nl.juriantech.tnttag.Tnttag;
import nl.juriantech.tnttag.api.PlayerLostRoundEvent;
import nl.juriantech.tnttag.enums.GameState;
import nl.juriantech.tnttag.enums.PlayerType;
import nl.juriantech.tnttag.managers.GameManager;
import nl.juriantech.tnttag.utils.ChatUtils;
import nl.juriantech.tnttag.utils.ParticleUtils;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class Round {

    private final Tnttag plugin;
    private final GameManager gameManager;
    private int roundDuration;

    public Round(Tnttag plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.roundDuration = gameManager.arena.getRoundDuration();
    }
    public void start() {
        for (Map.Entry<Player, PlayerType> player : gameManager.playerManager.getPlayers().entrySet()) {
            if (player.getValue().equals(PlayerType.SPECTATOR)) continue;

            player.getKey().playSound(player.getKey().getLocation(), Sound.valueOf(ChatUtils.getRaw("sounds.round-start").toUpperCase()), 1, 1);
            ChatUtils.sendTitle(player.getKey(), "titles.round-start", 20L, 20L, 20L);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                roundDuration--;
                for (Player player : gameManager.playerManager.getPlayers().keySet()) {
                    player.setLevel(Math.max(roundDuration, 0));
                    if (gameManager.playerManager.getPlayers().get(player) == PlayerType.TAGGER) {
                        updateCompass(player);
                        ChatUtils.sendActionBarMessage(player, ChatUtils.getRaw("actionBarMessages.tagger"));
                    } else if (gameManager.playerManager.getPlayers().get(player) == PlayerType.SURVIVOR) {
                        ChatUtils.sendActionBarMessage(player, ChatUtils.getRaw("actionBarMessages.survivor"));
                    }
                }

                if (roundDuration == 0) {
                    cancel();
                    end();
                    if (gameManager.playerManager.getPlayerCount() == 1) {
                        gameManager.setGameState(GameState.ENDING);
                    } else {
                        //Start a new round
                        gameManager.startRound();
                    }
                } else if (roundDuration < 0) {
                    //The game has crashed due to an error
                    cancel();
                    end();
                    Bukkit.getLogger().severe("This round is on a crashed state. Something has caused an error and made the round unable to continue. Stopping game...");
                    Bukkit.getLogger().severe("Please report the stacktrace above to our discord!");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void end() {
        gameManager.playerManager.broadcast(ChatUtils.getRaw("arena.round-ended"));
        for (Map.Entry<Player, PlayerType> entry : gameManager.playerManager.getPlayers().entrySet()) {
            Player player = entry.getKey();

            player.playSound(player.getLocation(), Sound.valueOf(ChatUtils.getRaw("sounds.round-end").toUpperCase()), 1, 1);
            ChatUtils.sendTitle(player, "titles.round-end", 20L, 20L, 20L);
            if (entry.getValue() == PlayerType.SPECTATOR) continue; //This should NOT affect spectators.
            if (entry.getValue() == PlayerType.TAGGER) {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                // Execute the round command for tnt players:
                for (String cmd : Tnttag.configfile.getStringList("round-finish-commands.taggers")) {
                    Bukkit.dispatchCommand(console, cmd.replace("%player%", player.getName()));
                }

                PlayerLostRoundEvent event = new PlayerLostRoundEvent(player, gameManager.arena.getName());
                Bukkit.getPluginManager().callEvent(event);
                player.getWorld().createExplosion(player.getLocation(), 0.5F, false, false);
                gameManager.playerManager.broadcast(ChatUtils.getRaw("arena.player-blew-up").replace("{player}", player.getName()));
                player.getInventory().setHelmet(new ItemStack(Material.AIR, 1));
                player.getInventory().setItem(0, new ItemStack(Material.AIR, 1));

                gameManager.playerManager.setPlayerType(player, PlayerType.SPECTATOR);
                ChatUtils.sendMessage(player, "player.lost-game");
                continue;
            }

            //The other people, survivors.
            for (String cmd : Tnttag.configfile.getStringList("round-finish-commands.survivors")) {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, cmd.replace("%player%", player.getName()));
            }

            ParticleUtils.Firework(player.getLocation(), 0);
        }
    }

    public void updateCompass(Player player) {
        ItemStack compass = player.getInventory().getItem(7);
        Player nearestPlayer = getNearestSurvivor(player);
        if (compass != null && nearestPlayer != null && compass.getItemMeta() != null) {
            ItemMeta meta = compass.getItemMeta();
            meta.setDisplayName((ChatUtils.colorize("&6" + (int) player.getLocation().distance(nearestPlayer.getLocation()) + "m")));
            compass.setItemMeta(meta);
        }
    }

    /**
     * Returns the nearest tagger to another player, or null if there is no other player in this world
     * @param player Player to check
     * @return Nearest other tagger, or null if there is no other player in this world
     */
    public Player getNearestSurvivor(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();
        ArrayList<Player> playersInWorld = new ArrayList<>(world.getEntitiesByClass(Player.class));
        if (playersInWorld.size() == 1) {
            return null;
        }

        playersInWorld.remove(player);
        playersInWorld.removeIf(p -> p != null && gameManager.playerManager.getPlayers().get(p).equals(PlayerType.SURVIVOR));
        playersInWorld.sort(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(location)));
        return playersInWorld.isEmpty() ? null : playersInWorld.get(0);
    }
}
