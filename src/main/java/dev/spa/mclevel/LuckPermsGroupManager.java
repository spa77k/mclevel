package dev.spa.mclevel;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * McLevel用のLuckPermsグループを初期化し、プレイヤーのレベルと同期する。
 *
 * グループ作成は起動のたびに存在確認を行うが、既存グループの他の権限は変更しない。
 */
public final class LuckPermsGroupManager {
    public static final String BUILD_PERMISSION = "multiverse.access.build";
    public static final String LEVEL_2_SHOP_PERMISSION = "spsmc.quickshop.level2";

    private static final String LEVEL_1_GROUP = "mclevel_lv1";
    private static final String LEVEL_2_GROUP = "mclevel_lv2";
    private static final String LEVEL_3_GROUP = "mclevel_lv3";
    private static final Set<String> MANAGED_GROUPS = Set.of(
            LEVEL_1_GROUP,
            LEVEL_2_GROUP,
            LEVEL_3_GROUP
    );

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final CompletableFuture<Void> setupFuture;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> userSyncs = new ConcurrentHashMap<>();

    public LuckPermsGroupManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = LuckPermsProvider.get();
        this.setupFuture = ensureGroups();
        this.setupFuture.whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "LuckPermsのMcLevel用グループ初期化に失敗しました。", failure);
            } else {
                plugin.getLogger().info("LuckPermsのMcLevel用グループを確認しました。建築ワールド権限: "
                        + BUILD_PERMISSION + ", Lv2ショップ権限: " + LEVEL_2_SHOP_PERMISSION);
            }
        });
    }

    /**
     * プレイヤーのMcLevel専用グループだけを現在レベルに合わせる。
     * 管理者グループなど、McLevelが管理しない親グループは保持する。
     */
    public CompletableFuture<Void> syncPlayer(Player player, int level) {
        return syncUser(player.getUniqueId(), player.getName(), level);
    }

    private CompletableFuture<Void> syncUser(UUID uuid, String username, int level) {
        String targetGroup = groupFor(level);
        CompletableFuture<Void> previous = userSyncs.getOrDefault(uuid, CompletableFuture.completedFuture(null));

        CompletableFuture<Void> current = previous.handle((ignored, failure) -> null)
                .thenCompose(ignored -> setupFuture)
                .thenCompose(ignored -> loadUser(uuid, username))
                .thenCompose(user -> applyUserGroup(user, targetGroup));

        userSyncs.put(uuid, current);
        current.whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING,
                        "LuckPermsのMcLevelグループ同期に失敗しました: " + username, failure);
            }
            userSyncs.remove(uuid, current);
        });
        return current;
    }

    private CompletableFuture<User> loadUser(UUID uuid, String username) {
        UserManager userManager = luckPerms.getUserManager();
        User loaded = userManager.getUser(uuid);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded);
        }
        return userManager.loadUser(uuid, username);
    }

    private CompletableFuture<Void> applyUserGroup(User user, String targetGroup) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && MANAGED_GROUPS.contains(inheritanceNode.getGroupName()));

        if (targetGroup != null) {
            user.data().add(InheritanceNode.builder(targetGroup).build());
        }

        return luckPerms.getUserManager().saveUser(user);
    }

    private String groupFor(int level) {
        return switch (LevelTier.fromValue(level)) {
            case LV1 -> LEVEL_1_GROUP;
            case LV2 -> LEVEL_2_GROUP;
            case LV3 -> LEVEL_3_GROUP;
            default -> null;
        };
    }

    private CompletableFuture<Void> ensureGroups() {
        return ensureGroup(LEVEL_1_GROUP)
                .thenCompose(group -> configureGroup(group,
                        target -> target.data().add(PermissionNode.builder()
                                .permission(BUILD_PERMISSION)
                                .value(true)
                                .build())))
                .thenCompose(ignored -> ensureGroup(LEVEL_2_GROUP))
                .thenCompose(group -> configureGroup(group,
                        target -> {
                            target.data().add(InheritanceNode.builder()
                                    .group(LEVEL_1_GROUP)
                                    .build());
                            target.data().add(PermissionNode.builder()
                                    .permission(LEVEL_2_SHOP_PERMISSION)
                                    .value(true)
                                    .build());
                        }))
                .thenCompose(ignored -> ensureGroup(LEVEL_3_GROUP))
                .thenCompose(group -> configureGroup(group,
                        target -> target.data().add(InheritanceNode.builder()
                                .group(LEVEL_2_GROUP)
                                .build())));
    }

    private CompletableFuture<Group> ensureGroup(String name) {
        GroupManager groupManager = luckPerms.getGroupManager();
        Group loaded = groupManager.getGroup(name);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded);
        }

        return groupManager.loadGroup(name).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(existing.get());
            }
            return groupManager.createAndLoadGroup(name);
        });
    }

    private CompletableFuture<Void> configureGroup(Group group, Consumer<Group> configurator) {
        configurator.accept(group);
        return luckPerms.getGroupManager().saveGroup(group);
    }
}
