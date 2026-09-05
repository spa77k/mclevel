package dev.spa.mclevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * レベル到達時のお祝い演出（メッセージ・タイトル・サウンド・花火）を担う。
 */
public final class LevelCelebration {
    private final JavaPlugin plugin;
    private final NamespacedKey celebrationFireworkKey;

    public LevelCelebration(JavaPlugin plugin) {
        this.plugin = plugin;
        this.celebrationFireworkKey = new NamespacedKey(plugin, "celebration-firework");
    }

    /**
     * お祝い演出用の花火であることを示すマーカーキー。
     * {@link LevelListener} が爆発ダメージを無効化する際の判定に使う。
     */
    public NamespacedKey getCelebrationFireworkKey() {
        return celebrationFireworkKey;
    }

    public void celebrate(Player player, LevelTier tier) {
        player.sendMessage(Component.text("★ レベルが " + tier.getValue() + " になりました！", NamedTextColor.GOLD));

        if (tier == LevelTier.LV3) {
            celebrateMaxLevel(player, tier);
            return;
        }

        CelebrationParams params = paramsFor(tier);
        player.showTitle(Title.title(
                Component.text("Lv" + tier.getValue() + " 達成！", params.titleColor()),
                Component.empty()
        ));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, params.pitch());

        for (int i = 0; i < params.fireworkCount(); i++) {
            spawnCelebrationFirework(player, tier);
        }
    }

    /** LV3（celebrateMaxLevel で個別演出）を除く各レベルの演出パラメータ。 */
    private CelebrationParams paramsFor(LevelTier tier) {
        return switch (tier) {
            case LV1 -> new CelebrationParams(NamedTextColor.YELLOW, 1.0f, 1);
            case LV2 -> new CelebrationParams(NamedTextColor.GOLD, 1.2f, 2);
            default  -> new CelebrationParams(NamedTextColor.WHITE, 1.0f, 1);
        };
    }

    private record CelebrationParams(NamedTextColor titleColor, float pitch, int fireworkCount) {
    }

    private void celebrateMaxLevel(Player player, LevelTier tier) {
        Bukkit.broadcast(Component.text(player.getName() + " が Lv3 に到達しました！", NamedTextColor.LIGHT_PURPLE));
        player.showTitle(Title.title(
                Component.text("Lv3 達成！！", NamedTextColor.LIGHT_PURPLE),
                Component.text("最高ランク到達", NamedTextColor.GOLD)
        ));

        Location base = player.getLocation();
        World world = player.getWorld();
        world.strikeLightningEffect(base);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0.0, 1.2, 0.0), 90, 1.2, 1.0, 1.2, 0.25);
        world.spawnParticle(Particle.END_ROD, base.clone().add(0.0, 1.0, 0.0), 120, 1.6, 1.1, 1.6, 0.08);

        player.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.4f, 0.8f);
        player.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.0f);
        player.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);

        for (int wave = 0; wave < 5; wave++) {
            int currentWave = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location waveBase = player.getLocation();
                player.playSound(waveBase, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f + currentWave * 0.08f);
                for (int i = 0; i < 4; i++) {
                    double angle = Math.toRadians((currentWave * 45.0) + (i * 90.0));
                    double radius = 2.0 + currentWave * 0.35;
                    Location fireworkLocation = waveBase.clone().add(Math.cos(angle) * radius, 0.4, Math.sin(angle) * radius);
                    spawnCelebrationFirework(fireworkLocation, tier, 2);
                }
            }, wave * 12L);
        }
    }

    private void spawnCelebrationFirework(Player player, LevelTier tier) {
        spawnCelebrationFirework(player.getLocation(), tier, 1);
    }

    private void spawnCelebrationFirework(Location location, LevelTier tier, int power) {
        FireworkEffect effect = switch (tier) {
            case LV2 -> FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE)
                    .withFade(Color.YELLOW)
                    .withFlicker()
                    .build();
            case LV3 -> FireworkEffect.builder()
                    .with(FireworkEffect.Type.STAR)
                    .withColor(Color.RED, Color.YELLOW, Color.WHITE)
                    .withFade(Color.ORANGE)
                    .withFlicker()
                    .withTrail()
                    .build();
            default -> FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL)
                    .withColor(Color.YELLOW)
                    .withFade(Color.WHITE)
                    .build();
        };
        Firework fw = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(effect);
        meta.setPower(power);
        fw.setFireworkMeta(meta);
        fw.getPersistentDataContainer().set(celebrationFireworkKey, PersistentDataType.BYTE, (byte) 1);
    }
}
