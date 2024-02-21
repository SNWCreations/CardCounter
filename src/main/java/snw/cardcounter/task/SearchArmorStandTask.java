package snw.cardcounter.task;

import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import snw.cardcounter.CardCounter;
import snw.cardcounter.entity.DisplayStand;

@RequiredArgsConstructor
public class SearchArmorStandTask extends BukkitRunnable {
    private final Player player;
    private final int flag;

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        for (Entity nearby : player.getNearbyEntities(2.5, 3, 2.5)) {
            if (nearby instanceof ArmorStand stand) {
                if (stand.getScoreboardTags().contains("flag_" + flag)) {
                    final DisplayStand wrapped = new DisplayStand(flag, stand);
                    wrapped.save();
                    final boolean done = JavaPlugin.getPlugin(CardCounter.class).getWaitingStand().remove(player.getUniqueId()) != null;
                    if (done) {
                        player.sendMessage(ChatColor.GREEN + "第 " + flag + " 号装置完全就绪！");
                    }
                    cancel();
                    break;
                }
            }
        }
    }

    public void start() {
        runTaskTimer(JavaPlugin.getPlugin(CardCounter.class), 0L, 10L);
    }
}
