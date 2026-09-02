package com.warriorssmp.classes.menu;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.PlayerClass;
import com.warriorssmp.classes.model.SkillNode;
import com.warriorssmp.classes.model.XpTable;
import com.warriorssmp.classes.task.SkillTreeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MenuManager implements Listener {

    private static final String MAIN_TITLE = "§6§l⚔ Your Class";
    private static final String SELECT_TITLE = "§6§l⚔ Choose Your Class";
    private static final String CONFIRM_TITLE = "§c§l⚠ Confirm Class Change";
    private static final String TREE_TITLE_PREFIX = "§6§l🌳 Skill Tree — ";
    private static final String RESPEC_TITLE = "§c§l⚠ Confirm Respec";

    private final ClassesPlugin plugin;

    public MenuManager(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- MAIN MENU

    public void openMainMenu(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());

        if (data.chosenClass == null) {
            openClassSelectMenu(player);
            return;
        }

        int level = plugin.levelService().levelOf(data);
        long xpIntoLevel = data.totalXp - XpTable.xpForLevel(level);
        long xpForNext = XpTable.xpForNextLevel(level) - XpTable.xpForLevel(level);
        boolean maxLevel = level >= XpTable.MAX_LEVEL;
        int availablePoints = plugin.skillTreeService().availablePoints(data);

        Inventory gui = inv(27, MAIN_TITLE, "main", null);

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Level §f" + level);
        if (maxLevel) {
            infoLore.add("§eMAX LEVEL");
        } else {
            infoLore.add("§7XP: §f" + xpIntoLevel + " / " + xpForNext);
            infoLore.add(bar((double) xpIntoLevel / Math.max(1, xpForNext), 20));
        }
        infoLore.add(" ");
        infoLore.add("§7Skill Points Available: §e" + availablePoints);
        gui.setItem(4, item(classIcon(data.chosenClass), data.chosenClass.coloredName(), infoLore, null, null));

        gui.setItem(11, item(Material.ENCHANTED_BOOK, "§d🌳 Skill Tree",
                List.of("§7View and unlock abilities", "§eAvailable Points: " + availablePoints),
                "nav", "tree"));

        double respecCost = plugin.classConfig().respecCost();
        gui.setItem(13, item(Material.BARRIER, "§c🔄 Respec",
                List.of("§7Reset your skill tree", "§7and redistribute your points.",
                        "§7Cost: §f" + plugin.economy().format(respecCost)),
                "nav", "respec_confirm"));

        gui.setItem(15, item(Material.NETHER_STAR, "§c⚠ Change Class",
                List.of("§7Pick a different class.", "§c§lThis wipes your entire skill tree!"),
                "nav", "class_confirm"));

        gui.setItem(17, item(Material.WRITTEN_BOOK, "§b📖 Guide Book",
                List.of("§7Everything about the class system", "§724 hour cooldown per book"),
                "open_guide", null));

        List<String> baseKitLore = switch (data.chosenClass) {
            case MAGE -> List.of(
                    "§7Right-click a Blaze Rod: §cFireball",
                    "§7Shift-right-click a Blaze Rod: §eLightning",
                    "§7Thrown Snowballs: §bIceball §7(automatic)",
                    "§7Or use §f/cast fireball§7, §f/cast lightning");
            case WARRIOR -> List.of(
                    "§7Right-click any sword: §cWhirlwind Strike",
                    "§7Or use §f/cast whirlwind");
            case ARCHER -> List.of(
                    "§7Right-click a Spectral Arrow: §aPower Shot",
                    "§7Or use §f/cast powershot");
        };
        gui.setItem(9, item(Material.NETHERITE_INGOT, "§bYour Base Kit", baseKitLore, null, null));

        gui.setItem(22, closeButton());
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- CLASS SELECT

    public void openClassSelectMenu(Player player) {
        Inventory gui = inv(27, SELECT_TITLE, "class_select", null);

        gui.setItem(11, item(Material.IRON_SWORD, PlayerClass.WARRIOR.coloredName(),
                List.of("§7Melee-focused: self-buffs and target", "§7debuffs on hit, a Whirlwind Strike", "§7mana ability, plus a survival cooldown."),
                "pick_class", "WARRIOR"));
        gui.setItem(13, item(Material.BLAZE_ROD, PlayerClass.MAGE.coloredName(),
                List.of("§7Cast real spells with mana: Fireball,", "§7Lightning, and enhanced Snowball", "§7throws — free Blaze Rod included."),
                "pick_class", "MAGE"));
        gui.setItem(15, item(Material.BOW, PlayerClass.ARCHER.coloredName(),
                List.of("§7Bow-focused: bonus damage procs, a", "§7Power Shot mana ability, and real", "§7extra arrows fired alongside your shot."),
                "pick_class", "ARCHER"));

        gui.setItem(22, closeButton());
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    public void openClassChangeConfirm(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        double cost = plugin.classConfig().classChangeCost();
        long remaining = (data.lastClassChangeAt + plugin.classConfig().classChangeCooldownMillis()) - System.currentTimeMillis();

        Inventory gui = inv(27, CONFIRM_TITLE, "class_confirm", null);

        if (remaining > 0) {
            gui.setItem(13, item(Material.BARRIER, "§c§lOn Cooldown",
                    List.of("§7You can change class again in:", "§f" + formatDuration(remaining),
                            " ", "§7An admin can do this for you", "§7anytime, free, with no cooldown."),
                    null, null));
            gui.setItem(22, backButton("main"));
            for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
            player.openInventory(gui);
            return;
        }

        gui.setItem(13, item(Material.BARRIER, "§c§lThis wipes your entire skill tree!",
                List.of("§7Your level and XP are kept.",
                        "§7Only your unlocked nodes are lost.",
                        " ", "§7Cost: §f" + plugin.economy().format(cost),
                        "§7Cooldown after: " + (plugin.classConfig().classChangeCooldownMillis() / 86_400_000L) + " days",
                        " ", "§eClick a class below to confirm."),
                null, null));
        gui.setItem(11, item(Material.IRON_SWORD, PlayerClass.WARRIOR.coloredName(), List.of(), "confirm_class_change", "WARRIOR"));
        gui.setItem(15, item(Material.BLAZE_ROD, PlayerClass.MAGE.coloredName(), List.of(), "confirm_class_change", "MAGE"));
        gui.setItem(4, item(Material.BOW, PlayerClass.ARCHER.coloredName(), List.of(), "confirm_class_change", "ARCHER"));
        gui.setItem(22, backButton("main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- RESPEC

    public void openRespecConfirm(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        double cost = plugin.classConfig().respecCost();
        long remaining = (data.lastRespecAt + plugin.classConfig().respecCooldownMillis()) - System.currentTimeMillis();

        Inventory gui = inv(27, RESPEC_TITLE, "respec_confirm", null);

        if (remaining > 0) {
            gui.setItem(13, item(Material.BARRIER, "§c§lOn Cooldown",
                    List.of("§7You can respec again in:", "§f" + formatDuration(remaining),
                            " ", "§7An admin can do this for you", "§7anytime, free, with no cooldown."),
                    null, null));
            gui.setItem(22, backButton("main"));
            for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
            player.openInventory(gui);
            return;
        }

        gui.setItem(13, item(Material.BARRIER, "§c§lReset your skill tree?",
                List.of("§7Every unlocked node will be cleared.",
                        "§7Your points can then be redistributed.",
                        " ", "§7Cost: §f" + plugin.economy().format(cost),
                        "§7Cooldown after: " + (plugin.classConfig().respecCooldownMillis() / 86_400_000L) + " days"),
                null, null));
        gui.setItem(11, item(Material.LIME_WOOL, "§a✓ Confirm", List.of(), "do_respec", null));
        gui.setItem(15, item(Material.RED_WOOL, "§c✗ Cancel", List.of(), "nav", "main"));
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    // ---------------------------------------------------------------- SKILL TREE

    public void openSkillTree(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) {
            openClassSelectMenu(player);
            return;
        }

        List<SkillNode> nodes = plugin.classConfig().nodesFor(data.chosenClass);
        int level = plugin.levelService().levelOf(data);
        int availablePoints = plugin.skillTreeService().availablePoints(data);

        Inventory gui = inv(54, TREE_TITLE_PREFIX + data.chosenClass.coloredName(), "tree", null);

        // Layout: roots (row 2), their upgrades directly below (row 3),
        // 1 capstone centered (row 4), passive stat buffs (row 5). Root/
        // upgrade counts aren't hardcoded to 5 — Warrior and Archer each
        // have a 6th (their active-spell upgrade path), so slots are spaced
        // evenly across the 9-wide row for however many nodes that class
        // actually has. The tree has more in it than any player's points
        // can ever fully cover (up to 50 to invest in absolutely everything
        // vs. a max of 19 earned by level 99) — that's deliberate, not a
        // bug, so every player's build is a real set of trade-offs.
        int capstoneSlot = 31;

        List<SkillNode> roots = new ArrayList<>();
        List<SkillNode> upgrades = new ArrayList<>();
        List<SkillNode> buffs = new ArrayList<>();
        SkillNode capstone = null;
        for (SkillNode node : nodes) {
            if (node.capstone()) capstone = node;
            else if (node.isMultiPoint()) buffs.add(node);
            else if (node.prerequisites().isEmpty()) roots.add(node);
            else upgrades.add(node);
        }

        int[] rootSlots = evenSlots(9, roots.size());
        int[] upgradeSlots = evenSlots(18, upgrades.size());
        int[] buffSlots = evenSlots(36, buffs.size());

        for (int i = 0; i < roots.size(); i++) {
            gui.setItem(rootSlots[i], nodeItem(data, roots.get(i), level, availablePoints));
        }
        for (int i = 0; i < upgrades.size(); i++) {
            gui.setItem(upgradeSlots[i], nodeItem(data, upgrades.get(i), level, availablePoints));
        }
        if (capstone != null) {
            gui.setItem(capstoneSlot, nodeItem(data, capstone, level, availablePoints));
        }
        for (int i = 0; i < buffs.size(); i++) {
            gui.setItem(buffSlots[i], nodeItem(data, buffs.get(i), level, availablePoints));
        }

        int totalCost = 0;
        for (SkillNode node : nodes) totalCost += node.cost() * node.maxPoints();
        gui.setItem(4, item(Material.EXPERIENCE_BOTTLE, "§eAvailable Points: " + availablePoints,
                List.of("§7Earned 1 point every " + plugin.classConfig().pointsPerLevelInterval() + " levels.",
                        "§7Investing in everything costs " + totalCost + " points —",
                        "§7more than you can ever fully earn.",
                        "§7Build your own path.",
                        " ",
                        "§7Bottom row: passive stat buffs (0-" + (buffs.isEmpty() ? 5 : buffs.get(0).maxPoints()) + " points each,",
                        "§7always-on bonuses, no chance roll or cooldown)."),
                null, null));

        // Base kit — always available from level 1, not tree nodes at all.
        // Shown here so nothing feels missing just because it isn't a
        // spendable node (Iceball especially has no upgrade path at all).
        List<String> baseKitLore = switch (data.chosenClass) {
            case MAGE -> List.of(
                    "§7Right-click a Blaze Rod: §cFireball",
                    "§7Shift-right-click a Blaze Rod: §eLightning",
                    "§7Thrown Snowballs: §bIceball §7(automatic, no upgrade)",
                    "§7Or use §f/cast fireball§7, §f/cast lightning");
            case WARRIOR -> List.of(
                    "§7Right-click any sword: §cWhirlwind Strike",
                    "§7Or use §f/cast whirlwind");
            case ARCHER -> List.of(
                    "§7Right-click a Spectral Arrow: §aPower Shot",
                    "§7Or use §f/cast powershot");
        };
        gui.setItem(8, item(Material.NETHER_STAR, "§bYour Base Kit (always available)", baseKitLore, null, null));

        gui.setItem(49, backButton("main"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    private ItemStack nodeItem(PlayerClassData data, SkillNode node, int level, int availablePoints) {
        int invested = data.investmentIn(node.id());
        boolean maxed = invested >= node.maxPoints();
        boolean hasPrereqs = plugin.skillTreeService().hasPrerequisites(data, node);
        boolean levelOk = level >= node.minLevel();
        boolean canAfford = availablePoints >= node.cost();
        boolean investable = !maxed && hasPrereqs && levelOk && canAfford;

        List<String> lore = new ArrayList<>();
        lore.add(node.description());
        lore.add(" ");
        if (node.isMultiPoint()) {
            lore.add("§7Invested: §e" + invested + "/" + node.maxPoints());
            lore.add("§7Cost per point: §e" + node.cost());
        } else {
            lore.add("§7Cost: §e" + node.cost() + " point" + (node.cost() == 1 ? "" : "s"));
        }
        lore.add("§7Requires Level: §f" + node.minLevel());
        if (!node.prerequisites().isEmpty()) {
            List<String> prereqNames = new ArrayList<>();
            for (String id : node.prerequisites()) {
                SkillNode p = plugin.classConfig().node(id);
                prereqNames.add(p != null ? p.displayName() : id);
            }
            lore.add("§7Requires: §f" + String.join("§7, §f", prereqNames));
        }
        lore.add(" ");

        String name;
        if (maxed) {
            name = "§a✓ " + node.displayName();
            lore.add(node.isMultiPoint() ? "§aFully invested" : "§aUnlocked");
        } else if (investable) {
            name = "§e" + node.displayName();
            lore.add(node.isMultiPoint() ? "§eClick to invest another point!" : "§eClick to unlock!");
        } else {
            name = (invested > 0 ? "§e" : "§7🔒 ") + node.displayName();
            if (!levelOk) lore.add("§cRequires Level " + node.minLevel());
            else if (!hasPrereqs) lore.add("§cMissing prerequisite");
            else if (!canAfford) lore.add("§cNot enough points");
        }

        return item(node.icon(), name, lore, maxed ? null : "invest_node", node.id());
    }

    // ---------------------------------------------------------------- helpers

    private Material classIcon(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> Material.IRON_SWORD;
            case MAGE -> Material.BLAZE_ROD;
            case ARCHER -> Material.BOW;
        };
    }

    private String bar(double fraction, int length) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        return "§a" + "█".repeat(filled) + "§7" + "░".repeat(length - filled);
    }

    private String formatDuration(long ms) {
        long totalMinutes = ms / 60_000L;
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /** Mages need a Blaze Rod, Archers need a Spectral Arrow — give the
     *  right item for free on picking or switching to that class, rather
     *  than making a player's core class kit depend on finding one first.
     *  Warrior needs nothing extra (any sword already works). Only gives one
     *  if they don't already have one, so re-picking a class or admin-forcing
     *  it repeatedly doesn't flood their inventory. */
    private void giveClassWandIfNeeded(Player player, PlayerClass chosen) {
        Material wand = switch (chosen) {
            case MAGE -> Material.BLAZE_ROD;
            case ARCHER -> Material.SPECTRAL_ARROW;
            case WARRIOR -> null;
        };
        if (wand == null) return;
        if (player.getInventory().contains(wand)) return;
        var leftover = player.getInventory().addItem(new ItemStack(wand));
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        String howTo = switch (chosen) {
            case MAGE -> "right-click to cast Fireball, shift-right-click for Lightning";
            case ARCHER -> "right-click to fire a Power Shot";
            case WARRIOR -> "";
        };
        player.sendMessage("§bYou've been given a " + wand.name().replace("_", " ").toLowerCase()
                + " — " + howTo + ".");
    }

    /** Evenly spaces `count` slots across a 9-wide inventory row starting at
     *  `rowStart` (e.g. evenSlots(9, 5) spans the row edge-to-edge with gaps;
     *  evenSlots(9, 6) packs tighter with smaller gaps). Capped at 9 — no
     *  class's node counts are expected to need more than that in one row. */
    private int[] evenSlots(int rowStart, int count) {
        if (count <= 0) return new int[0];
        count = Math.min(count, 9);
        int[] slots = new int[count];
        double spacing = 9.0 / count;
        for (int i = 0; i < count; i++) {
            slots[i] = rowStart + (int) Math.round(i * spacing + spacing / 2 - 0.5);
        }
        return slots;
    }

    private Inventory inv(int size, String title, String navKey, String navData) {
        return Bukkit.createInventory(null, size, title);
    }

    private ItemStack item(Material material, String name, List<String> lore, String action, String data) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            if (action != null) {
                meta.getPersistentDataContainer().set(actionKey(), org.bukkit.persistence.PersistentDataType.STRING, action);
            }
            if (data != null) {
                meta.getPersistentDataContainer().set(dataKey(), org.bukkit.persistence.PersistentDataType.STRING, data);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private org.bukkit.NamespacedKey actionKey() {
        return new org.bukkit.NamespacedKey(plugin, "action");
    }

    private org.bukkit.NamespacedKey dataKey() {
        return new org.bukkit.NamespacedKey(plugin, "data");
    }

    private ItemStack backButton(String target) {
        return item(Material.ARROW, "§7◀ Back", List.of(), "nav", target);
    }

    private ItemStack closeButton() {
        return item(Material.BARRIER, "§c❌ Close", List.of(), "close", null);
    }

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---------------------------------------------------------------- ADMIN PANEL

    private static final String ADMIN_TITLE = "§6§l⚙ Classes Admin Panel";

    public void openAdminPanel(Player player) {
        Inventory gui = inv(27, ADMIN_TITLE, "admin", null);

        int total = plugin.classConfig().allNodes().size();
        gui.setItem(11, item(Material.BOOK, "§6Node Summary",
                List.of("§7" + total + " skill tree node(s) loaded",
                        "§7(" + plugin.classConfig().nodesFor(PlayerClass.WARRIOR).size() + " Warrior, "
                                + plugin.classConfig().nodesFor(PlayerClass.MAGE).size() + " Mage, "
                                + plugin.classConfig().nodesFor(PlayerClass.ARCHER).size() + " Archer)",
                        " ",
                        "§7Per-node numbers (chance, cooldown,",
                        "§7damage, effects) are edited in",
                        "§7config.yml, not this panel — reload",
                        "§7after saving to apply changes."),
                null, null));

        gui.setItem(13, item(Material.LIME_DYE, "§a🔄 Reload Config",
                List.of("§7Reloads config.yml and rebuilds", "§7every skill tree node."),
                "admin_reload", null));

        gui.setItem(15, item(Material.PLAYER_HEAD, "§bBrowse Online Players",
                List.of("§7Click a player to view and edit", "§7their class, level, and tree.",
                        " ", "§7For offline players, use:",
                        "§f/classeditor view <player>", "§f/classeditor setclass <player> <class>",
                        "§f/classeditor setlevel <player> <level>", "§f/classeditor respec <player>",
                        "§f/classeditor reset <player>"),
                "nav", "admin_players"));

        gui.setItem(22, closeButton());
        for (int i = 0; i < 27; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    private static final String PLAYER_LIST_TITLE = "§6§l⚙ Browse Players";
    private static final String PLAYER_EDIT_TITLE_PREFIX = "§6§l⚙ Editing: ";

    public void openPlayerBrowser(Player admin) {
        Inventory gui = inv(54, PLAYER_LIST_TITLE, "admin_players", null);
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            PlayerClassData data = plugin.dataStore().get(online.getUniqueId());
            int level = plugin.levelService().levelOf(data);
            List<String> lore = new ArrayList<>();
            lore.add("§7Class: §f" + (data.chosenClass == null ? "none" : data.chosenClass.coloredName()));
            lore.add("§7Level: §f" + level);
            lore.add(" ");
            lore.add("§eClick to view and edit");
            gui.setItem(slot++, item(Material.PLAYER_HEAD, "§f" + online.getName(), lore, "admin_edit_player", online.getUniqueId().toString()));
        }
        if (slot == 0) {
            gui.setItem(22, item(Material.BARRIER, "§7No players online", List.of(), null, null));
        }
        gui.setItem(49, backButton("admin"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    public void openPlayerEdit(Player admin, java.util.UUID targetUuid) {
        Player target = Bukkit.getPlayer(targetUuid);
        String targetName = target != null ? target.getName() : targetUuid.toString().substring(0, 8);
        PlayerClassData data = plugin.dataStore().get(targetUuid);
        int level = plugin.levelService().levelOf(data);
        int points = plugin.skillTreeService().availablePoints(data);

        Inventory gui = inv(45, PLAYER_EDIT_TITLE_PREFIX + targetName, "admin_edit_player_view", targetUuid.toString());

        gui.setItem(4, item(Material.PLAYER_HEAD, "§f" + targetName,
                List.of("§7Class: §f" + (data.chosenClass == null ? "none" : data.chosenClass.coloredName()),
                        "§7Level: §f" + level + " §7(XP: " + data.totalXp + ")",
                        "§7Available Points: §f" + points,
                        "§7Unlocked Nodes: §f" + data.nodeInvestment.size()),
                null, null));

        gui.setItem(19, item(Material.IRON_SWORD, "§cSet Warrior", List.of("§7Free, wipes their tree"), "admin_set_class", targetUuid + ":WARRIOR"));
        gui.setItem(20, item(Material.BLAZE_ROD, "§bSet Mage", List.of("§7Free, wipes their tree"), "admin_set_class", targetUuid + ":MAGE"));
        gui.setItem(21, item(Material.BOW, "§aSet Archer", List.of("§7Free, wipes their tree"), "admin_set_class", targetUuid + ":ARCHER"));

        gui.setItem(23, item(Material.EXPERIENCE_BOTTLE, "§a+5 Levels", List.of(), "admin_adjust_level", targetUuid + ":5"));
        gui.setItem(24, item(Material.EXPERIENCE_BOTTLE, "§a+1 Level", List.of(), "admin_adjust_level", targetUuid + ":1"));
        gui.setItem(25, item(Material.GLASS_BOTTLE, "§c-1 Level", List.of(), "admin_adjust_level", targetUuid + ":-1"));
        gui.setItem(26, item(Material.GLASS_BOTTLE, "§c-5 Levels", List.of(), "admin_adjust_level", targetUuid + ":-5"));

        gui.setItem(29, item(Material.LIME_DYE, "§aForce Respec", List.of("§7Free, no cooldown applied"), "admin_respec", targetUuid.toString()));
        gui.setItem(33, item(Material.TNT, "§c§lReset Everything", List.of("§cWipes ALL progress for this player"), "admin_reset_player", targetUuid.toString()));

        gui.setItem(40, backButton("admin_players"));
        for (int i = 0; i < 45; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- GUIDE BOOK

    private void openGuideBook(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        long cooldownMs = plugin.classConfig().guideBookCooldownMillis();
        long remaining = (data.lastGuideBookAt + cooldownMs) - System.currentTimeMillis();
        if (remaining > 0) {
            long hours = remaining / 3_600_000L;
            long minutes = (remaining / 60_000L) % 60;
            player.sendMessage("§cYou can request the Guide Book again in " + hours + "h " + minutes + "m.");
            return;
        }

        try {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            ItemMeta rawMeta = book.getItemMeta();
            if (!(rawMeta instanceof org.bukkit.inventory.meta.BookMeta meta)) {
                player.sendMessage("§cCouldn't create the guide book — WRITTEN_BOOK has no BookMeta on this server. Tell an admin to check console.");
                plugin.getLogger().severe("Guide Book: ItemMeta for WRITTEN_BOOK was not a BookMeta instance (was " + rawMeta + ").");
                return;
            }

            double respecCost = plugin.classConfig().respecCost();
            double classChangeCost = plugin.classConfig().classChangeCost();
            long respecDays = plugin.classConfig().respecCooldownMillis() / 86_400_000L;
            long classChangeDays = plugin.classConfig().classChangeCooldownMillis() / 86_400_000L;

            meta.setTitle("Classes Guide");
            meta.setAuthor("Guild Master");
            meta.setPages(
                    "§6§lCLASSES GUIDE\n\n§7Welcome! This book covers Warrior, Mage, and Archer: leveling, the skill tree, respecs, and class changes.\n\n§8Flip the page \u27a1",
                    "§6§lPicking a Class\n\n§7Warrior fights up close, Mage casts real spells, Archer fights at range with a bow. Pick one from the Class menu - you can change later, but it isn't free or instant (see page 6).",
                    "§6§lLeveling & XP\n\n§7XP comes mostly from PvP damage dealt to other players, with a small trickle from PvE kills too. Same 1-99 curve as every other WSMP skill. A killing blow on a player also grants bonus XP.",
                    "§6§lMana & Active Spells\n\n§7All three classes get mana, regenerating over time and shown on your action bar. Warrior: right-click any sword for Whirlwind Strike. Mage: right-click a free Blaze Rod for Fireball, shift-right-click for Lightning. Archer: right-click a free Spectral Arrow for Power Shot. Thrown Snowballs are also automatically enhanced into Iceballs for Mage. None of this needs splash potions.",
                    "§6§lThe Skill Tree\n\n§7Every " + plugin.classConfig().pointsPerLevelInterval() + " levels grants 1 skill point. Most nodes are single-unlock abilities; a few (Vitality, Swiftness, Fortitude, Might, and the proc-chance buff) can be invested in repeatedly, 0-5 points each, for a permanent passive bonus instead of a triggered ability. You can't unlock everything - the tree has more in it than your points can ever fully cover, so build your own path. The capstone at the bottom only needs level 70 and 5 points - it doesn't care which other nodes you've picked.",
                    "§6§lRespec & Changing Class\n\n§7Respec clears your unlocked nodes so you can redistribute points - costs " + plugin.economy().format(respecCost) + " and can only be done once every " + respecDays + " days. Changing class wipes your tree entirely and costs " + plugin.economy().format(classChangeCost) + ", also limited to once every " + classChangeDays + " days. An admin can do either for you anytime, free, with no cooldown.",
                    "§6§lTips\n\n§7\u2022 Upgraded nodes replace their base version - you don't get both at once\n§7\u2022 Triggered abilities are chance + cooldown gated, nothing is spammable\n§7\u2022 Passive buff points are always-on, no roll or cooldown at all\n§7\u2022 Think carefully before respeccing - it isn't cheap or quick"
            );

            book.setItemMeta(meta);

            var leftover = player.getInventory().addItem(book);
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
            player.sendMessage("§aCheck your inventory — the Classes Guide book has been added. Right-click it to read.");
            data.lastGuideBookAt = System.currentTimeMillis();
        } catch (Exception e) {
            player.sendMessage("§cSomething went wrong creating the guide book. Tell an admin to check console.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create/give the Guide Book", e);
        }
    }

    // ---------------------------------------------------------------- CLICK ROUTING

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isOurs = title.equals(MAIN_TITLE) || title.equals(SELECT_TITLE) || title.equals(CONFIRM_TITLE)
                || title.equals(RESPEC_TITLE) || title.equals(ADMIN_TITLE) || title.startsWith(TREE_TITLE_PREFIX)
                || title.equals(PLAYER_LIST_TITLE) || title.startsWith(PLAYER_EDIT_TITLE_PREFIX);
        if (!isOurs) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();

        String action = meta.getPersistentDataContainer().get(actionKey(), org.bukkit.persistence.PersistentDataType.STRING);
        String data = meta.getPersistentDataContainer().get(dataKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (action == null) return;

        PlayerClassData pdata = plugin.dataStore().get(player.getUniqueId());

        switch (action) {
            case "close" -> player.closeInventory();
            case "open_guide" -> openGuideBook(player);
            case "admin_reload" -> {
                plugin.reloadConfig();
                plugin.classConfig().load();
                player.sendMessage("§aConfig reloaded — " + plugin.classConfig().allNodes().size() + " node(s) loaded.");
                openAdminPanel(player);
            }
            case "nav" -> {
                switch (data) {
                    case "main" -> openMainMenu(player);
                    case "tree" -> openSkillTree(player);
                    case "class_confirm" -> openClassChangeConfirm(player);
                    case "respec_confirm" -> openRespecConfirm(player);
                    case "admin" -> openAdminPanel(player);
                    case "admin_players" -> openPlayerBrowser(player);
                }
            }
            case "admin_edit_player" -> {
                try {
                    openPlayerEdit(player, java.util.UUID.fromString(data));
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "admin_set_class" -> {
                String[] parts = data.split(":");
                if (parts.length != 2) return;
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(parts[0]);
                    PlayerClass chosen = PlayerClass.valueOf(parts[1]);
                    PlayerClassData targetData = plugin.dataStore().get(targetUuid);
                    targetData.chosenClass = chosen;
                    targetData.resetTree();
                    Player targetOnline = Bukkit.getPlayer(targetUuid);
                    if (targetOnline != null) plugin.abilityService().applyPassiveStats(targetOnline, targetData);
                    player.sendMessage("§aSet their class to " + chosen.displayName() + " (tree reset, free).");
                    openPlayerEdit(player, targetUuid);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "admin_adjust_level" -> {
                String[] parts = data.split(":");
                if (parts.length != 2) return;
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(parts[0]);
                    int delta = Integer.parseInt(parts[1]);
                    PlayerClassData targetData = plugin.dataStore().get(targetUuid);
                    int newLevel = Math.max(1, Math.min(com.warriorssmp.classes.model.XpTable.MAX_LEVEL,
                            plugin.levelService().levelOf(targetData) + delta));
                    targetData.totalXp = com.warriorssmp.classes.model.XpTable.xpForLevel(newLevel);
                    openPlayerEdit(player, targetUuid);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "admin_respec" -> {
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(data);
                    PlayerClassData targetData = plugin.dataStore().get(targetUuid);
                    plugin.skillTreeService().respec(targetData);
                    Player targetOnline = Bukkit.getPlayer(targetUuid);
                    if (targetOnline != null) plugin.abilityService().applyPassiveStats(targetOnline, targetData);
                    player.sendMessage("§aRespecced their skill tree (free, no cooldown applied).");
                    openPlayerEdit(player, targetUuid);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "admin_reset_player" -> {
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(data);
                    PlayerClassData targetData = plugin.dataStore().get(targetUuid);
                    targetData.resetAll();
                    Player targetOnline = Bukkit.getPlayer(targetUuid);
                    if (targetOnline != null) plugin.abilityService().applyPassiveStats(targetOnline, targetData);
                    player.sendMessage("§cReset all progress for that player.");
                    openPlayerBrowser(player);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "pick_class" -> {
                try {
                    PlayerClass chosen = PlayerClass.valueOf(data);
                    pdata.chosenClass = chosen;
                    giveClassWandIfNeeded(player, chosen);
                    plugin.manaBarManager().show(player, pdata);
                    player.sendMessage("§a§lCLASS CHOSEN §7— §f" + chosen.coloredName());
                    openMainMenu(player);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "confirm_class_change" -> {
                long remaining = (pdata.lastClassChangeAt + plugin.classConfig().classChangeCooldownMillis()) - System.currentTimeMillis();
                if (remaining > 0) {
                    player.sendMessage("§cClass change is on cooldown for " + formatDuration(remaining) + ".");
                    openMainMenu(player);
                    return;
                }
                double cost = plugin.classConfig().classChangeCost();
                if (!plugin.economy().withdraw(player, cost)) {
                    player.sendMessage("§cYou can't afford a class change (" + plugin.economy().format(cost) + ").");
                    openMainMenu(player);
                    return;
                }
                try {
                    PlayerClass chosen = PlayerClass.valueOf(data);
                    pdata.chosenClass = chosen;
                    pdata.resetTree();
                    pdata.lastClassChangeAt = System.currentTimeMillis();
                    plugin.abilityService().applyPassiveStats(player, pdata);
                    giveClassWandIfNeeded(player, chosen);
                    plugin.manaBarManager().show(player, pdata);
                    player.sendMessage("§a§lCLASS CHANGED §7— §f" + chosen.coloredName() + " §7(skill tree reset)");
                    openMainMenu(player);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "do_respec" -> {
                long remaining = (pdata.lastRespecAt + plugin.classConfig().respecCooldownMillis()) - System.currentTimeMillis();
                if (remaining > 0) {
                    player.sendMessage("§cRespec is on cooldown for " + formatDuration(remaining) + ".");
                    openMainMenu(player);
                    return;
                }
                double cost = plugin.classConfig().respecCost();
                if (!plugin.economy().withdraw(player, cost)) {
                    player.sendMessage("§cYou can't afford the respec (" + plugin.economy().format(cost) + ").");
                    openMainMenu(player);
                    return;
                }
                plugin.skillTreeService().respec(pdata);
                pdata.lastRespecAt = System.currentTimeMillis();
                plugin.abilityService().applyPassiveStats(player, pdata);
                player.sendMessage("§a§lRESPEC COMPLETE §7— your skill tree has been reset.");
                openMainMenu(player);
            }
            case "invest_node" -> {
                var node = plugin.classConfig().node(data);
                if (node == null) return;
                SkillTreeService.UnlockResult result = plugin.skillTreeService().tryInvest(player, pdata, node);
                switch (result) {
                    case NOT_ENOUGH_POINTS -> player.sendMessage("§cNot enough skill points.");
                    case LEVEL_TOO_LOW -> player.sendMessage("§cYou need to be level " + node.minLevel() + ".");
                    case MISSING_PREREQUISITE -> player.sendMessage("§cUnlock its prerequisite first.");
                    case ALREADY_MAXED -> player.sendMessage("§cAlready fully invested.");
                    case WRONG_CLASS, NO_CLASS_CHOSEN -> player.sendMessage("§cThat's not available to your class.");
                    case SUCCESS -> plugin.abilityService().applyPassiveStats(player, pdata);
                }
                openSkillTree(player);
            }
        }
    }
}
