package com.mowmaster.bibliomania.Blocks.BreakfastBook;

import com.mowmaster.bibliomania.Blocks.Book.BookItem;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.ArrayList;
import java.util.List;

// The Breakfast Club as an item: a normal book in inventories (BookItem: bundle-style clicks, scroll or the page keys
// to pick a slot), and it can be eaten. Right-click in the air (a right-click on a block places it, like any book)
// eats or drinks one item from the selected slot, taking as long as that item does, and gives exactly what the item
// gives: the item itself is "eaten" through vanilla's own code (ItemStack#finishUsingItem), so its hunger,
// saturation and effects (golden apples, potions, suspicious stew...) all apply.
// What's left (a bowl, a glass bottle) stays in the slot if the slot is now empty and the item didn't stack
// (stews, potions); otherwise it goes into the inventory (honey bottles, or a slot that still holds more).
public class BreakfastBookItem extends BookItem
{
    public BreakfastBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    // One of the selected slot's item (empty if the slot is empty or holds something inedible, like a left bowl)
    private ItemStack selectedFood(ItemStack book)
    {
        PagedContents contents = contents(book);
        ResourceStack<ItemResource> stack = contents.stack(contents.selectedPage());
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack food = stack.resource().toStack(1);
        return BreakfastBookBlock.isEdible(food) ? food : ItemStack.EMPTY;
    }

    // ---- Eating: the same steps as eating the item itself ----

    // Right-click: start eating, if there's something to eat and the player may eat it (not full, unless it's always
    // edible like a golden apple). startUsingItem makes the game call the methods below over the next ticks.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack food = selectedFood(player.getItemInHand(hand));
        if (food.isEmpty()) return InteractionResult.PASS;
        FoodProperties properties = food.get(DataComponents.FOOD);
        if (properties != null && !player.canEat(properties.canAlwaysEat())) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // How long it takes (32 ticks for most food, 16 for dried kelp...) and the arm pose (eat or drink): the food's own
    @Override
    public int getUseDuration(ItemStack book, LivingEntity entity)
    {
        ItemStack food = selectedFood(book);
        return food.isEmpty() ? 0 : food.getUseDuration(entity);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack book)
    {
        ItemStack food = selectedFood(book);
        return food.isEmpty() ? ItemUseAnimation.NONE : food.getUseAnimation();
    }

    // While eating: the munching/gulping sound every 4 ticks. (The game plays these from the held item's
    // minecraft:consumable component, which the book doesn't have, so the food's sound is played here.)
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack book, int remainingUseDuration)
    {
        if (remainingUseDuration % 4 != 0 || level.isClientSide()) return;
        Consumable consumable = selectedFood(book).get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        level.playSound(null, entity.blockPosition(), consumable.sound().value(), entity.getSoundSource(),
                0.5f, 0.9f + level.getRandom().nextFloat() * 0.2f);
    }

    // Done: eat one from the selected slot (server). Creative players eat without using it up, like with food.
    @Override
    public ItemStack finishUsingItem(ItemStack book, Level level, LivingEntity entity)
    {
        if (!(level instanceof ServerLevel) || !(entity instanceof Player player)) return book;
        ItemStack food = selectedFood(book);
        if (food.isEmpty()) return book;
        boolean stackable = food.getMaxStackSize() > 1;

        // Vanilla's eating: nutrition, effects, sounds, stats; returns what's left of the one item (a bowl, a glass
        // bottle, or nothing). For creative players it returns the item itself, as nothing was used up.
        ItemStack left = food.copy().finishUsingItem(level, player);
        if (player.hasInfiniteMaterials()) return book;

        ItemStack remainder = left.isEmpty() || ItemStack.isSameItemSameComponents(left, food) ? ItemStack.EMPTY : left;
        boolean slotNowEmpty = takeOneFromSelected(book, stackable ? ItemStack.EMPTY : remainder);
        if (!remainder.isEmpty() && (stackable || !slotNowEmpty)) StorageInteractions.giveToPlayer(player, remainder);
        return book;
    }

    // Takes one from the selected slot. If that empties it, `leaveBehind` (a bowl, a bottle; may be empty) goes in
    // its place. Returns whether the slot was emptied. The page list is copied, changed and stored as new contents
    // (data components never change in place).
    private boolean takeOneFromSelected(ItemStack book, ItemStack leaveBehind)
    {
        PagedContents contents = contents(book);
        int page = contents.selectedPage();
        ResourceStack<ItemResource> current = contents.stack(page);
        List<ResourceStack<ItemResource>> stacks = new ArrayList<>(contents.stacks());
        while (stacks.size() <= page) stacks.add(new ResourceStack<>(ItemResource.EMPTY, 0));

        boolean emptied = current.amount() <= 1;
        if (!emptied) stacks.set(page, new ResourceStack<>(current.resource(), current.amount() - 1));
        else if (!leaveBehind.isEmpty()) stacks.set(page, new ResourceStack<>(ItemResource.of(leaveBehind), leaveBehind.getCount()));
        else stacks.set(page, new ResourceStack<>(ItemResource.EMPTY, 0));
        setContents(book, new PagedContents(contents.pages(), contents.page(), stacks));
        return emptied;
    }
}
