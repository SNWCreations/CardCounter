package snw.cardcounter;

import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import snw.cardcounter.entity.DisplayStand;
import snw.cardcounter.task.SearchArmorStandTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public final class CardCounter extends JavaPlugin implements Listener {
    // Fuck you Bukkit, I'm just lazy to wait for the plugin instance
    @SuppressWarnings("deprecation")
    private static final NamespacedKey FLAG =
            new NamespacedKey(CardCounter.class.getSimpleName().toLowerCase(),
                    "bound");
    private final Map<UUID, Integer> pendingFlag = new HashMap<>();
    @Getter
    private final Map<UUID, Integer> waitingStand = new HashMap<>();
    private File confFile;
    private YamlConfiguration conf;
    private boolean on;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // only one command, so no check
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "参数呢？");
        } else {
            if (sender instanceof Player player) {
                if (player.isOp()) {
                    UUID uuid = player.getUniqueId();
                    if (pendingFlag.containsKey(uuid) || waitingStand.containsKey(uuid)) {
                        sender.sendMessage(ChatColor.RED + "你好像还没配置完？先配置完这个再继续吧！");
                        return true;
                    }
                    String d = args[0];
                    int v;
                    try {
                        v = Integer.parseInt(d);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "不是数字，啊这...");
                        return true;
                    }
                    pendingFlag.put(uuid, v);
                    new SearchArmorStandTask(player, v).start();
                    sender.sendMessage(ChatColor.GREEN +
                            "OK. 现在请用木棍敲击一个漏斗，然后靠近(5格内)带有 'flag_" + v + "' 标签的盔甲架，即可完成设置！");
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public FileConfiguration getConfig() {
        return conf;
    }

    @Override
    public void saveConfig() {
        try {
            getConfig().save(confFile);
        } catch (IOException e) {
            getLogger().severe("Error saving configuration!");
            e.printStackTrace();
        }
    }

    @Override
    public void saveDefaultConfig() {
    }

    @Override
    public void onLoad() {
        confFile = new File(getDataFolder(), "conf.yml");
        ConfigurationSerialization.registerClass(DisplayStand.class);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        Objects.requireNonNull(getCommand("ccswitch")).setExecutor((sender, command, label, args) -> {
            on = !on;
            for (DisplayStand instance : DisplayStand.getInstances()) {
                ArmorStand entity = instance.getEntity();
                if (entity != null) {
                    entity.setCustomNameVisible(on);
                }
            }
            return true;
        });
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskLater(this, () -> { // let this run in the first server tick
            conf = new YamlConfiguration();
            try {
                conf.load(confFile); // delay! because onLoad calls before worlds are loaded?
            } catch (FileNotFoundException ignored) {
            } catch (IOException | InvalidConfigurationException e) {
                getLogger().severe("Error loading configuration");
                e.printStackTrace();
            }
        }, 1L);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (DisplayStand instance : DisplayStand.getInstances()) {
            instance.save();
            ArmorStand entity = instance.getEntity();
            if (entity != null) {
                entity.setCustomNameVisible(false);
            }
        }
        saveConfig();
        DisplayStand.getInstances().clear();
        pendingFlag.clear();
        waitingStand.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        final InventoryHolder source = event.getInventory().getHolder();
        if (source instanceof Hopper hopper) {
            final Item itemEntity = event.getItem();
            final ItemStack item = itemEntity.getItemStack();
            final Material type = item.getType();
            final NamespacedKey key = type.getKey();
            if (key.getNamespace().equals("tzz")) {
                final String itemKey = key.getKey();
                if (itemKey.startsWith("card_")) {
                    final int i = itemKey.lastIndexOf('_');
                    final int parsed = Integer.parseInt(itemKey.substring(i + 1));
                    final PersistentDataContainer hopperPDC = hopper.getPersistentDataContainer();
                    final boolean has = hopperPDC.has(FLAG, PersistentDataType.INTEGER);
                    if (has) {
                        @SuppressWarnings("DataFlowIssue") final int flag = hopperPDC.get(FLAG, PersistentDataType.INTEGER);
                        final DisplayStand stand = DisplayStand.fromId(flag);
                        stand.increase(itemEntity.getThrower(), parsed);
                        getServer().getScheduler().runTaskLater(this, () -> event.getInventory().clear(), 1L);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        final Block block = event.getBlock();
        final Player player = event.getPlayer();
        if (block.getType() == Material.HOPPER) {
            Hopper state = (Hopper) block.getState();
            if (player.isOp() && player.getGameMode() == GameMode.CREATIVE) {
                final ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
                if (itemInMainHand.getType() == Material.STICK) {
                    final Integer remove = pendingFlag.remove(player.getUniqueId());
                    if (remove != null) {
                        event.setCancelled(true);
                        final PersistentDataContainer pdc = state.getPersistentDataContainer();
                        pdc.set(FLAG, PersistentDataType.INTEGER, remove);
                        state.update(true, false);
                        player.sendMessage(ChatColor.GREEN + "操作成功。这是第 " + remove + " 号装置。");
                        final DisplayStand stand = DisplayStand.fromId(remove);
                        if (stand != null) {
                            player.sendMessage(ChatColor.GREEN + "第 " + remove + " 号装置完全就绪！");
                        } else {
                            waitingStand.put(player.getUniqueId(), remove);
                        }
                        return;
                    }
                }
            }
            if (state.getPersistentDataContainer().has(FLAG, PersistentDataType.INTEGER)) {
                Integer i = state.getPersistentDataContainer().get(FLAG, PersistentDataType.INTEGER);
                DisplayStand.fromId(i).destroy();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStandDeath(EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        final DisplayStand stand = DisplayStand.fromUUID(entity.getUniqueId());
        if (stand != null) {
            stand.destroy();
        }
    }
}
