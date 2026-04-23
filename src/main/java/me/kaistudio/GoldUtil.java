package me.kaistudio;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;

public class GoldUtil {

    public static int countNuggets(Inventory inv) {
        int nuggets = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (item == null) continue;
            switch (item.getType()) {
                case GOLD_BLOCK  -> nuggets += item.getAmount() * 81;
                case GOLD_INGOT  -> nuggets += item.getAmount() * 9;
                case GOLD_NUGGET -> nuggets += item.getAmount();
            }
        }
        return nuggets;
    }

    public static String format(int nuggets) {
        if (nuggets % 9 == 0) return (nuggets / 9) + " gold";
        return String.format("%.2f gold", nuggets / 9.0);
    }

    // Remove nuggets worth of gold from inventory, giving back exact change
    public static boolean removeGold(Player player, int nuggets) {
        PlayerInventory inv = player.getInventory();
        int total = countNuggets(inv);
        if (total < nuggets) return false;
        removeAllGold(inv);
        addGold(player, total - nuggets);
        return true;
    }

    public static void addGold(Player player, int nuggets) {
        int blocks = nuggets / 81;
        int remainder = nuggets % 81;
        int ingots = remainder / 9;
        int remainingNuggets = remainder % 9;

        if (blocks > 0)          giveOrDrop(player, Material.GOLD_BLOCK, blocks);
        if (ingots > 0)          giveOrDrop(player, Material.GOLD_INGOT, ingots);
        if (remainingNuggets > 0) giveOrDrop(player, Material.GOLD_NUGGET, remainingNuggets);
    }

    private static void giveOrDrop(Player player, Material mat, int amount) {
        while (amount > 0) {
            int stackSize = Math.min(amount, mat.getMaxStackSize());
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(mat, stackSize));
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            amount -= stackSize;
        }
    }

    private static void removeAllGold(PlayerInventory inv) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null) continue;
            Material type = contents[i].getType();
            if (type == Material.GOLD_BLOCK || type == Material.GOLD_INGOT || type == Material.GOLD_NUGGET) {
                contents[i] = null;
            }
        }
        inv.setStorageContents(contents);
    }
}
