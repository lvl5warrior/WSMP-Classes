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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuManager implements Listener {

    private static final String MAIN_TITLE = "§6§l⚔ Your Class";
    private static final String SELECT_TITLE = "§6§l⚔ Choose Your Class";
    private static final String CONFIRM_TITLE = "§c§l⚠ Confirm Class Change";
    private static final String TREE_TITLE_PREFIX = "§6§l🌳 Skill Tree — ";
    private static final String PATH_SELECT_TITLE_PREFIX = "§6§lChoose Your Path — ";
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
                    "§7Shift-left-click a Blaze Rod: §bIceball",
                    "§7Or use §f/cast fireball§7, §f/cast lightning");
            case WARRIOR -> List.of(
                    "§7Right-click any sword: §cWhirlwind Strike",
                    "§7Shift+left-click any sword: §6Charge",
                    "§7Or use §f/cast whirlwind§7, §f/cast charge");
            case ARCHER -> List.of(
                    "§7Sneak + left-click while holding a bow: §aPower Shot",
                    "§7Right-click a Tripwire Hook on the ground: §2Trap",
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
                    List.of("§7You can change class again in:", "§f" + formatDuration(remaining)),
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

    /** Shown immediately after picking or changing class, and any time
     *  openSkillTree() is opened with no path chosen yet — forces the
     *  choice up front, with a real preview of each path's early skills,
     *  rather than letting a player stumble into locking themselves out of
     *  two paths by clicking the wrong node in a browse-everything tree. */
    public void openPathSelectMenu(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) {
            openClassSelectMenu(player);
            return;
        }
        if (data.chosenPath != null) {
            openSkillTree(player);
            return;
        }

        List<SkillNode> nodes = plugin.classConfig().nodesFor(data.chosenClass);
        Map<String, List<String>> skillsByPath = new LinkedHashMap<>();
        Map<String, Material> iconByPath = new LinkedHashMap<>();
        for (SkillNode n : nodes) {
            if (n.isShared()) continue;
            skillsByPath.computeIfAbsent(n.path(), k -> new ArrayList<>());
            if (skillsByPath.get(n.path()).size() < 4 && n.tier() <= 2 && !n.capstone()) {
                skillsByPath.get(n.path()).add(n.displayName());
            }
            iconByPath.putIfAbsent(n.path(), n.icon());
        }

        Inventory gui = inv(27, PATH_SELECT_TITLE_PREFIX + data.chosenClass.coloredName(), "path_select", null);
        int[] slots = {11, 13, 15};
        int i = 0;
        for (var entry : skillsByPath.entrySet()) {
            if (i >= slots.length) break;
            String path = entry.getKey();
            List<String> lore = new ArrayList<>();
            lore.add("§7A glimpse of this path's skills:");
            for (String skill : entry.getValue()) lore.add("§f\u2022 " + skill);
            String uniquePerk = switch (path) {
                case "ice" -> "§bUnique: permanent Water Breathing";
                case "fire" -> "§cUnique: walk on lava unharmed";
                case "lightning" -> "§9Unique: swap-hands to blink (1/min)";
                case "berserker" -> "§4Unique: immune to Slow & Weakness";
                case "guardian" -> "§8Unique: total knockback immunity";
                case "warlord" -> "§6Unique: swap-hands for Rallying Cry (1/min)";
                case "marksman" -> "§2Unique: nearby enemies glow through walls";
                case "skirmisher" -> "§3Unique: immune to fall damage";
                case "barrage" -> "§6Unique: swap-hands for Storm of Arrows (1/min)";
                default -> null;
            };
            if (uniquePerk != null) {
                lore.add(" ");
                lore.add(uniquePerk);
                lore.add("§7— free just for picking this path.");
            }
            lore.add(" ");
            lore.add("§cPicking a path locks out the other");
            lore.add("§ctwo until you respec.");
            gui.setItem(slots[i++], item(iconByPath.get(path), "§d" + prettyPath(path) + " Path", lore, "choose_path", path));
        }
        for (int s = 0; s < 27; s++) if (gui.getItem(s) == null) gui.setItem(s, filler());
        player.openInventory(gui);
    }

    public void openSkillTree(Player player) {
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) {
            openClassSelectMenu(player);
            return;
        }
        if (data.chosenPath == null) {
            openPathSelectMenu(player);
            return;
        }

        List<SkillNode> nodes = plugin.classConfig().nodesFor(data.chosenClass);
        int level = plugin.levelService().levelOf(data);
        int availablePoints = plugin.skillTreeService().availablePoints(data);

        // Path filter: chosenPath is guaranteed non-null here (the guard
        // above redirects to openPathSelectMenu otherwise) — only that
        // path's nodes, plus the handful of shared/universal ones, are
        // shown at all. The other two paths just aren't part of this view.
        String chosen = data.chosenPath;
        nodes = nodes.stream().filter(n -> n.isShared() || chosen.equals(n.path())).toList();

        Inventory gui = inv(54, TREE_TITLE_PREFIX + data.chosenClass.coloredName(), "tree", null);

        // Layout is tier-driven: every non-capstone, non-buff node has an
        // explicit tier (1-3, reduced from 4 for more build flexibility)
        // in config, and a prerequisite chain, so as long as config.yml
        // lists chains in the same relative order at every tier (which it
        // does), the same column index at each tier IS that chain's next
        // stage — literally sitting below its own prerequisite on screen.
        // Row 3 holds the capstone centered plus the points-summary info
        // item; row 4 holds the passive stat buffs; row 5 holds the back
        // button and base-kit info, with real breathing room now that the
        // tree is only 3 tiers deep instead of 4.
        List<SkillNode> tier1 = new ArrayList<>();
        List<SkillNode> tier2 = new ArrayList<>();
        List<SkillNode> tier3 = new ArrayList<>();
        List<SkillNode> buffs = new ArrayList<>();
        List<SkillNode> capstones = new ArrayList<>();

        // A "chain" node's tier-1 root has some later node listing it as a
        // prerequisite; a standalone bonus ability (e.g. Rending Wounds,
        // Serrated Arrows) has no such descendant. Mixing a standalone
        // ability into the tier-1 row alongside real chain roots breaks
        // column alignment for the whole row: each row's slots are spaced
        // independently, so a tier-1 row with 3 items and a tier-2 row with
        // only 2 (because the 3rd tier-1 item has no tier-2 counterpart)
        // land in different columns even though nothing else changed.
        // Standalone abilities go to the buffs row instead, where count
        // mismatches don't cause this problem.
        java.util.Set<String> hasDescendant = new java.util.HashSet<>();
        for (SkillNode node : nodes) {
            hasDescendant.addAll(node.prerequisites());
        }

        for (SkillNode node : nodes) {
            if (node.capstone()) capstones.add(node);
            else if (node.isMultiPoint()) buffs.add(node);
            else if (node.tier() == 1 && node.prerequisites().isEmpty() && !hasDescendant.contains(node.id())) {
                buffs.add(node);
            } else switch (node.tier()) {
                case 1 -> tier1.add(node);
                case 2 -> tier2.add(node);
                default -> tier3.add(node);
            }
        }

        placeRow(gui, data, level, availablePoints, tier1, 0);
        placeRow(gui, data, level, availablePoints, tier2, 9);
        placeRow(gui, data, level, availablePoints, tier3, 18);
        placeRow(gui, data, level, availablePoints, buffs, 36);

        // Normally exactly one capstone shows here (the committed path's
        // ultimate, once filtered down to just that path). Before a path
        // is chosen, all 3 paths' capstones are still in the unfiltered
        // list — spaced across slots 30-32 instead of the single centered
        // slot 31, so none of them get silently dropped.
        if (capstones.size() == 1) {
            gui.setItem(31, nodeItem(data, capstones.get(0), level, availablePoints));
        } else {
            int[] capSlots = {29, 31, 32};
            for (int i = 0; i < capstones.size() && i < capSlots.length; i++) {
                gui.setItem(capSlots[i], nodeItem(data, capstones.get(i), level, availablePoints));
            }
        }

        int totalCost = 0;
        for (SkillNode node : nodes) totalCost += node.cost() * node.maxPoints();
        String pathLine = data.chosenPath == null
                ? "§eNo path chosen yet — investing in any"
                : "§ePath committed: §f" + prettyPath(data.chosenPath);
        String pathLine2 = data.chosenPath == null
                ? "§epath-locked node below commits you to it."
                : "§7Respec to clear your tree and pick again.";
        gui.setItem(27, item(Material.EXPERIENCE_BOTTLE, "§eAvailable Points: " + availablePoints,
                List.of("§7Earned 1 point every " + plugin.classConfig().pointsPerLevelInterval() + " levels.",
                        "§7Investing in everything costs " + totalCost + " points —",
                        "§7more than you can ever fully earn.",
                        " ",
                        pathLine, pathLine2,
                        " ",
                        "§7Rows 1-4: your ability chains, each tier",
                        "§7requiring the one above it.",
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
                    "§7Shift-left-click a Blaze Rod: §bIceball",
                    "§7Or use §f/cast fireball§7, §f/cast lightning");
            case WARRIOR -> List.of(
                    "§7Right-click any sword: §cWhirlwind Strike",
                    "§7Shift+left-click any sword: §6Charge",
                    "§7Or use §f/cast whirlwind§7, §f/cast charge");
            case ARCHER -> List.of(
                    "§7Sneak + left-click while holding a bow: §aPower Shot",
                    "§7(consumes 1 arrow, fires a volley)",
                    "§7Right-click a Tripwire Hook: §2Trap",
                    "§7Or use §f/cast powershot");
        };
        gui.setItem(49, item(Material.NETHER_STAR, "§bYour Base Kit (always available)", baseKitLore, null, null));

        gui.setItem(45, backButton("main"));
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler());
        player.openInventory(gui);
    }

    /** "fire" -> "Fire", "greater_whirlwind" style ids aren't used here —
     *  path keys are always a single lowercase word. */
    private String prettyPath(String path) {
        if (path == null || path.isEmpty()) return "";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /** Places `nodes` evenly across a 9-wide row starting at `rowStart`. */
    private void placeRow(Inventory gui, PlayerClassData data, int level, int availablePoints,
                           List<SkillNode> nodes, int rowStart) {
        int[] slots = evenSlots(rowStart, nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            gui.setItem(slots[i], nodeItem(data, nodes.get(i), level, availablePoints));
        }
    }

    private ItemStack nodeItem(PlayerClassData data, SkillNode node, int level, int availablePoints) {
        int invested = data.investmentIn(node.id());
        boolean maxed = invested >= node.maxPoints();
        boolean hasPrereqs = plugin.skillTreeService().hasPrerequisites(data, node);
        boolean levelOk = level >= node.minLevel();
        boolean canAfford = availablePoints >= node.cost();
        boolean investable = !maxed && hasPrereqs && levelOk && canAfford;

        List<String> lore = new ArrayList<>();
        lore.addAll(node.description());
        if (!node.isShared()) {
            lore.add("§d" + prettyPath(node.path()) + " Path");
        }
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

    /** Mages need a Blaze Rod, Archers need a bow (to trigger Power Shot)
     *  and some arrows (consumed as its cost) — give what's missing for
     *  free on picking or switching to that class, rather than making a
     *  player's core class kit depend on finding one first. Warrior needs
     *  nothing extra (any sword already works). Only gives an item if the
     *  player doesn't already have one, so re-picking a class or
     *  admin-forcing it repeatedly doesn't flood their inventory. */
    private void giveClassWandIfNeeded(Player player, PlayerClass chosen) {
        switch (chosen) {
            case MAGE -> {
                giveIfMissing(player, Material.BLAZE_ROD, 1);
                player.sendMessage("§bYou've been given a blaze rod — right-click to cast Fireball, shift-right-click for Lightning.");
            }
            case ARCHER -> {
                giveIfMissing(player, Material.BOW, 1);
                giveIfMissing(player, Material.ARROW, 16);
                giveIfMissing(player, Material.TRIPWIRE_HOOK, 4);
                player.sendMessage("§bYou've been given a bow, arrows, and tripwire hooks — sneak + left-click while holding the bow to fire a Power Shot (consumes one arrow), or right-click a tripwire hook on the ground to place a trap.");
            }
            case WARRIOR -> {
                // Nothing extra needed — any sword already works.
            }
        }
    }

    private void giveIfMissing(Player player, Material material, int amount) {
        if (player.getInventory().contains(material)) return;
        var leftover = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
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
        giveGuideBook(player, data);
    }

    /** Auto-granted the moment a player picks or changes class — a
     *  cooldown on a system-initiated welcome gift would defeat the point
     *  of it, so this bypasses the normal request cooldown entirely and
     *  doesn't touch lastGuideBookAt (a player who gets this on class-pick
     *  can still manually request one later on the usual cooldown). */
    private void giveWelcomeGuideBook(Player player) {
        giveGuideBook(player, plugin.dataStore().get(player.getUniqueId()));
    }

    private void giveGuideBook(Player player, PlayerClassData data) {
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
            int pointInterval = plugin.classConfig().pointsPerLevelInterval();

            meta.setTitle("Classes Guide");
            meta.setAuthor("Guild Master");

            // Book pages only show ~14 lines before text is cut off with no
            // scrolling — every page here is kept short on purpose, and a
            // topic that needs more room gets a second page instead of one
            // long paragraph. Colors are picked to actually read on a book's
            // light parchment background: no §e (yellow) or §f (white),
            // both wash out almost invisibly there — §1/§2/§4/§8 and plain
            // §7/§6 instead, all of which read clearly on that background.
            List<String> pages = new ArrayList<>(List.of(
                    "§6§lCLASSES GUIDE\n\n§7Warrior, Mage, Archer:\ncontrols, leveling,\npaths, and the tree.\n\n§8Open anytime:\n§1/classmenu",
                    "§6§lPicking a Class\n\n§7Warrior: up close.\nMage: real spells.\nArcher: ranged.\n\n§7Pick from the Class\nmenu.",
                    "§6§lChanging Later\n\n§7Changing class wipes\nyour tree and costs\n§1" + plugin.economy().format(classChangeCost) + "§7,\nonce every §1" + classChangeDays + " days§7.",
                    "§6§lLeveling & XP\n\n§7Comes mostly from PvP\ndamage dealt, plus a\nsmall trickle from PvE.\nA killing blow grants\nbonus XP too.",
                    "§6§lActive Spells\n\n§2Warrior: §7right-click\nany sword.\n§2Mage: §7right-click a\nBlaze Rod.",
                    "§6§lActive Spells (2)\n\n§2Archer: §7sneak +\nleft-click while\nholding a bow.\n\n§7Uses mana, shown on a\nboss bar above your\nhotbar.",
                    "§6§lChoose Your Path\n\n§7Right after picking a\nclass, you'll choose\n1 of 3 paths.\n\n§4Locks you out of the\nother two §7until you\nrespec.",
                    "§6§lShared Skills\n\n§7A few nodes work with\nANY path: Vitality,\nSwiftness, Fortitude,\nMight, proc-chance.\n\n§7Mix these with your\npath's own skills.",
                    "§6§lThe Skill Tree\n\n§7Every " + pointInterval + " levels =\n1 skill point.\n\n§7Chains run 3 tiers:\nlevel 10, 25, then 50.",
                    "§6§lThe Skill Tree (2)\n\n§7Each path ends in its\nown capstone at\nlevel 65.\n\n§7You can't max your\nwhole path — build on\npurpose.",
                    "§6§lRespec\n\n§7Clears your tree AND\npath choice.\n\n§1Cost: " + plugin.economy().format(respecCost) + "\n§1Once every " + respecDays + " days.",
                    "§6§lTips\n\n§7\u2022 Higher tiers replace\nthe one before\n\u2022 Nothing is spammable\n\u2022 Buff points are\nalways-on\n\u2022 Respec resets your\npath too"
            ));

            if (data.chosenClass != null) {
                pages.addAll(classSpecificPages(data.chosenClass));
            }

            meta.setPages(pages.toArray(new String[0]));
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

    /** Appended to the general guide once a player has picked a class —
     *  their exact controls plus a one-line pitch for each of their 3
     *  paths, so nobody has to guess what "Ice" or "Guardian" even means
     *  before committing to it. */
    private List<String> classSpecificPages(PlayerClass cls) {
        return switch (cls) {
            case MAGE -> List.of(
                    "§1§lYOUR CLASS: MAGE\n\n§7Right-click Blaze Rod:\n§4Fireball\n\n§7Shift-right-click:\n§1Lightning",
                    "§1§lMAGE (2)\n\nShift-left-click a\nBlaze Rod: §3Iceball\n\n§7Free Blaze Rod given\nwhen you pick Mage.",
                    "§1§lMAGE PATHS\n\n§4\u2588 Fire: §7big single-\ntarget burst damage.\n\n§3\u2588 Ice: §7control and\nsustain, slows enemies.",
                    "§1§lMAGE PATHS (2)\n\n§9\u2588 Lightning: §7reactive\n— hits back hard when\nyou take damage.",
                    "§1§lMAGE PATH PERKS\n\n§3Ice: §7permanent Water\nBreathing.\n\n§4Fire: §7walk on lava\nunharmed.",
                    "§1§lMAGE PATH PERKS (2)\n\n§9Lightning: §7swap\nhands (F) to blink a\nshort distance, once a\nminute.\n\n§7Granted just for\npicking that path —\nno tree points needed."
            );
            case WARRIOR -> List.of(
                    "§4§lYOUR CLASS: WARRIOR\n\n§7Right-click any sword:\n§4Whirlwind Strike\n§7(AOE)\n\n§7Shift+left-click any\nsword: §6Charge\n\n§7No special item\nneeded.",
                    "§4§lWARRIOR PATHS\n\n§4\u2588 Berserker: §7lifesteal\n— the longer the fight,\nthe stronger you get.\n\n§8\u2588 Guardian: §7pure\nsurvivability.",
                    "§4§lWARRIOR PATHS (2)\n\n§6\u2588 Warlord: §7control\nand area damage, built\nfor fighting groups.",
                    "§4§lWARRIOR PATH PERKS\n\n§4Berserker: §7immune\nto Slow & Weakness.\n\n§8Guardian: §7total\nknockback immunity.",
                    "§4§lWARRIOR PATH PERKS (2)\n\n§6Warlord: §7swap hands\n(F) for Rallying Cry —\nStrength+Speed for you\nand nearby allies,\nonce a minute.\n\n§7Granted just for\npicking that path."
            );
            case ARCHER -> List.of(
                    "§2§lYOUR CLASS: ARCHER\n\n§7Sneak + left-click\nwhile holding a bow:\n§2Power Shot\n\n§7Consumes 1 arrow,\nfires a real volley.",
                    "§2§lARCHER (2)\n\n§7Right-click a\nTripwire Hook on the\nground: §2Trap\n\n§7Free bow, arrows, and\nhooks given when you\npick Archer.",
                    "§2§lARCHER PATHS\n\n§2\u2588 Marksman: §7precision,\nmarked targets take\nfar more damage.\n\n§3\u2588 Skirmisher: §7mobility,\nlifesteal on arrows.",
                    "§2§lARCHER PATHS (2)\n\n§6\u2588 Barrage: §7volume of\nfire — built to fire\nPower Shot as often\nas possible.",
                    "§2§lARCHER PATH PERKS\n\n§2Marksman: §7nearby\nenemies glow through\nwalls and foliage.\n\n§3Skirmisher: §7immune\nto fall damage.",
                    "§2§lARCHER PATH PERKS (2)\n\n§6Barrage: §7swap hands\n(F) for Storm of Arrows\n— fires at every enemy\nnearby at once, once\na minute.\n\n§7Granted just for\npicking that path."
            );
        };
    }

    // ---------------------------------------------------------------- CLICK ROUTING

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isOurs = title.equals(MAIN_TITLE) || title.equals(SELECT_TITLE) || title.equals(CONFIRM_TITLE)
                || title.equals(RESPEC_TITLE) || title.equals(ADMIN_TITLE) || title.startsWith(TREE_TITLE_PREFIX)
                || title.startsWith(PATH_SELECT_TITLE_PREFIX)
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

        try {
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
                    giveWelcomeGuideBook(player);
                    openPathSelectMenu(player);
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
                    giveWelcomeGuideBook(player);
                    openPathSelectMenu(player);
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
            case "choose_path" -> {
                pdata.chosenPath = data;
                player.sendMessage("§a§lPATH CHOSEN §7— §f" + prettyPath(data) + " §7(locked in until you respec)");
                plugin.abilityService().applyPassiveStats(player, pdata);
                openSkillTree(player);
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
                    case WRONG_PATH -> player.sendMessage("§cYou've already committed to a different path — respec to switch.");
                    case SUCCESS -> plugin.abilityService().applyPassiveStats(player, pdata);
                }
                openSkillTree(player);
            }
        }
        } catch (Exception e) {
            player.sendMessage("§cSomething went wrong handling that click. Tell an admin to check console.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Menu click failed — action=" + action + " data=" + data + " player=" + player.getName(), e);
        }
    }
}
