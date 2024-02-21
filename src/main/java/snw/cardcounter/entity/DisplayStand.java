package snw.cardcounter.entity;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import snw.cardcounter.CardCounter;

import javax.annotation.Nullable;
import java.util.*;

public class DisplayStand implements ConfigurationSerializable {
    @Getter
    private static final Set<DisplayStand> instances = new HashSet<>();
    private final int id;
    private final UUID worldUUID;
    private final UUID uuid;
    private final Location location;
    private ArmorStand entity;
    private int count;

    public static DisplayStand fromUUID(UUID uuid) {
        return instances.stream()
                .filter(it -> it.uuid.equals(uuid))
                .findFirst().orElse(null);
    }

    public static DisplayStand fromId(int id) {
        return instances.stream()
                .filter(it -> it.id == id)
                .findFirst().orElse(null);
    }

    public DisplayStand(int id, UUID worldUUID, UUID uuid, Location location) {
        this.id = id;
        this.worldUUID = worldUUID;
        this.uuid = uuid;
        this.location = location;
        instances.add(this);
    }

    public DisplayStand(int id, ArmorStand stand) {
        this.id = id;
        this.entity = stand;
        this.worldUUID = stand.getWorld().getUID();
        this.uuid = stand.getUniqueId();
        this.location = stand.getLocation();
        update();
        instances.add(this);
    }

    public @Nullable ArmorStand getEntity() {
        if (entity == null) {
            World world = Bukkit.getWorld(worldUUID);
            if (world == null) {
                return null;
            }
            Chunk chunk = world.getChunkAt(location); // causes the chunk to be loaded
            for (Entity entity : chunk.getEntities()) {
                if (entity.getUniqueId().equals(uuid)) {
                    if (entity instanceof ArmorStand detected) {
                        this.entity = detected;
                        update();
                        instances.add(this); // valid instance
                        break;
                    } else {
                        return null;
                    }
                }
            }
        }
        return entity;
    }

    public void increase(UUID thrower, int v) {
        // send tip
        Optional.ofNullable(Bukkit.getPlayerExact("Murasame_mao")).ifPresent(it -> {
            it.sendMessage(ChatColor.GREEN + "" + id + " 号装置更新！" + count + "+" + v + "-> " + (count + v));
            Optional.ofNullable(Bukkit.getPlayer(thrower)).ifPresent(a -> {
                it.sendMessage(ChatColor.GREEN + "投入物品的玩家: " + a.getName());
            });
        });
        count += v;
        update();
    }

    public void update() {
        String name = ChatColor.RED + "" + ChatColor.BOLD + "当前点数: " + count;
        final ArmorStand fetch = getEntity();
        if (fetch != null) {
            fetch.setGravity(false);
            fetch.setInvisible(true);
            fetch.setInvulnerable(true);
            fetch.setCustomName(name);
            fetch.setCustomNameVisible(true);
        }
    }

    public void save() {
        JavaPlugin.getPlugin(CardCounter.class).getConfig().set("entity_" + id, this);
    }

    @Override
    public Map<String, Object> serialize() {
        return ImmutableMap.<String, Object>builder()
                .put("id", id)
                .put("world", worldUUID.toString())
                .put("uuid", uuid.toString())
                .put("loc", location)
                .build();
    }

    public static DisplayStand deserialize(Map<String, Object> map) {
        return new DisplayStand(
                (int) map.get("id"),
                UUID.fromString((String) map.get("world")),
                UUID.fromString((String) map.get("uuid")),
                (Location) map.get("loc")
        );
    }

    public void destroy() {
        JavaPlugin.getPlugin(CardCounter.class).getConfig().set("entity_" + id, null);
        final ArmorStand theStand = getEntity();
        if (theStand != null) {
            theStand.remove();
        }
        instances.remove(this);
    }
}
