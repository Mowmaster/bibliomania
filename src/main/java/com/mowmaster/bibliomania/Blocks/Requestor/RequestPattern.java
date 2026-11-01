package com.mowmaster.bibliomania.Blocks.Requestor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// What a Patterned Storage Requestor (9 slots) or Storage Dispenser (1 slot) is set to: per slot an item (kept as one,
// with its components) and how many to ask for (1 to MAX_COUNT; always 1 on the dispenser). These are settings, not
// items: nothing is held here, and breaking the block drops none of them.
// The count is kept apart from the item because an ItemStack can't be saved holding more than its item stacks to
// (64 buckets).
public final class RequestPattern
{
    public static final int MAX_COUNT = 64;
    private static final String TAG_PATTERN = "pattern";
    private static final String TAG_EXACT = "exact";

    private final ItemStack[] items;
    private final int[] counts;
    private final int maxCount;
    private final Runnable onChanged;
    // How stored items are matched: false (fuzzy, the default) ignores damage; true (exact) doesn't
    private boolean exact;

    // onChanged: the block entity saving itself and telling clients
    public RequestPattern(int size, int maxCount, Runnable onChanged)
    {
        this.items = new ItemStack[size];
        this.counts = new int[size];
        this.maxCount = maxCount;
        this.onChanged = onChanged;
        Arrays.fill(items, ItemStack.EMPTY);
    }

    public int size()
    {
        return items.length;
    }

    // The slot's item (count 1), or EMPTY
    public ItemStack item(int slot)
    {
        return slot >= 0 && slot < items.length ? items[slot] : ItemStack.EMPTY;
    }

    public int count(int slot)
    {
        return slot >= 0 && slot < items.length && !items[slot].isEmpty() ? counts[slot] : 0;
    }

    public boolean isEmpty()
    {
        for (ItemStack item : items) if (!item.isEmpty()) return false;
        return true;
    }

    // Sets a slot to what's held: its item, and its stack size as the count
    public void set(int slot, ItemStack held)
    {
        if (slot < 0 || slot >= items.length) return;
        items[slot] = held.copyWithCount(1);
        counts[slot] = Mth.clamp(held.getCount(), 1, maxCount);
        onChanged.run();
    }

    public void clear(int slot)
    {
        if (slot < 0 || slot >= items.length || items[slot].isEmpty()) return;
        items[slot] = ItemStack.EMPTY;
        counts[slot] = 0;
        onChanged.run();
    }

    // More or fewer (never below 1 or above the most). Returns whether it changed.
    public boolean adjust(int slot, int by)
    {
        if (count(slot) == 0) return false;
        int changed = Mth.clamp(counts[slot] + by, 1, maxCount);
        if (changed == counts[slot]) return false;
        counts[slot] = changed;
        onChanged.run();
        return true;
    }

    // ---- Matching (the Filter Tool switches it) ----

    public boolean exact()
    {
        return exact;
    }

    // Switches between fuzzy and exact; returns the new setting
    public boolean toggleExact()
    {
        exact = !exact;
        onChanged.run();
        return exact;
    }

    // ---- Saving ----

    // One set slot as saved: [{slot: 0, item: {id: "minecraft:stone"}, count: 32}, ...]
    private record Saved(int slot, ItemStack item, int count)
    {
        // RecordCodecBuilder: a codec built field by field, then put together with the record's constructor
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(Saved::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(Saved::item),
                Codec.INT.fieldOf("count").forGetter(Saved::count)
        ).apply(instance, Saved::new));
    }

    public void save(ValueOutput output)
    {
        List<Saved> saved = new ArrayList<>();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (!items[slot].isEmpty()) saved.add(new Saved(slot, items[slot], counts[slot]));
        }
        output.store(TAG_PATTERN, Saved.CODEC.listOf(), saved);
        output.putBoolean(TAG_EXACT, exact);
    }

    public void load(ValueInput input)
    {
        Arrays.fill(items, ItemStack.EMPTY);
        Arrays.fill(counts, 0);
        exact = input.getBooleanOr(TAG_EXACT, false);
        for (Saved saved : input.read(TAG_PATTERN, Saved.CODEC.listOf()).orElse(List.of()))
        {
            if (saved.slot() < 0 || saved.slot() >= items.length || saved.item().isEmpty()) continue;
            items[saved.slot()] = saved.item().copyWithCount(1);
            counts[saved.slot()] = Mth.clamp(saved.count(), 1, maxCount);
        }
    }
}
