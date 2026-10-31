package com.mowmaster.bibliomania.Blocks.BreakfastBook;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.Book.BookItem;
import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.mowlib.paging.PagedContents;
import com.mowmaster.mowlib.storage.StorageInteractions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// The Breakfast Club as an item: a normal book in inventories (BookItem: bundle-style clicks, scroll or the page keys
// to pick a slot), and it can be eaten. Right-click in the air (a right-click on a block places it, like any book)
// eats or drinks one item from the selected slot, taking as long as that item does.
// - Food, potions, milk...: the item itself is "eaten" through vanilla's own code (ItemStack#finishUsingItem), so
//   its hunger, saturation and effects (golden apples, potions, milk clearing effects...) all apply. What's left (a
//   bowl, a bottle, a bucket) stays in the slot if the slot is now empty and the item didn't stack (stews, potions,
//   milk); otherwise it goes into the inventory (honey bottles, or a slot that still holds more).
// - Cakes: one slice per bite, like eating a placed cake. The slot remembers the slices left ("uses left", in the
//   bibliomania:uses_left component, one number per slot) and can't be emptied until that cake is finished.
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

    // ---- Uses left (cakes) ----

    // The list, one entry per slot (an empty list if the book has none yet). getOrDefault: the component, or the
    // default if the item doesn't have it.
    private static List<Integer> usesLeft(ItemStack book)
    {
        return book.getOrDefault(DataComponentRegistry.USES_LEFT.get(), List.of());
    }

    // Uses left of the cake being eaten in `page`, 0 if none has been started
    public static int usesLeft(ItemStack book, int page)
    {
        List<Integer> list = usesLeft(book);
        return page < list.size() ? list.get(page) : 0;
    }

    private static void setUsesLeft(ItemStack book, int page, int uses)
    {
        List<Integer> list = new ArrayList<>(usesLeft(book));
        while (list.size() <= page) list.add(0);
        list.set(page, uses);
        book.set(DataComponentRegistry.USES_LEFT.get(), list);
    }

    // ---- Eating: the same steps as eating the item itself ----

    // Right-click: start eating, if there's something to eat and the player may eat it (not full, unless it's always
    // edible like a golden apple; drinks always). startUsingItem makes the game call the methods below over the next ticks.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack food = selectedFood(player.getItemInHand(hand));
        if (food.isEmpty()) return InteractionResult.PASS;
        FoodProperties properties = food.get(DataComponents.FOOD);
        boolean cake = BreakfastBookBlock.usesOf(food) > 0;
        if ((properties != null || cake) && !player.canEat(properties != null && properties.canAlwaysEat()))
        {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // How long it takes and the arm pose (eat or drink): the item's own; a cake slice like food (32 ticks, eating)
    @Override
    public int getUseDuration(ItemStack book, LivingEntity entity)
    {
        ItemStack food = selectedFood(book);
        if (food.isEmpty()) return 0;
        return BreakfastBookBlock.usesOf(food) > 0 ? 32 : food.getUseDuration(entity);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack book)
    {
        ItemStack food = selectedFood(book);
        if (food.isEmpty()) return ItemUseAnimation.NONE;
        return BreakfastBookBlock.usesOf(food) > 0 ? ItemUseAnimation.EAT : food.getUseAnimation();
    }

    // While eating: the munching/gulping sound every 4 ticks. (The game plays these from the held item's
    // minecraft:consumable component, which the book doesn't have, so the food's sound is played here.)
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack book, int remainingUseDuration)
    {
        if (remainingUseDuration % 4 != 0 || level.isClientSide()) return;
        ItemStack food = selectedFood(book);
        Consumable consumable = food.get(DataComponents.CONSUMABLE);
        if (consumable == null && BreakfastBookBlock.usesOf(food) == 0) return;
        level.playSound(null, entity.blockPosition(), consumable != null ? consumable.sound().value() : SoundEvents.GENERIC_EAT.value(),
                entity.getSoundSource(), 0.5f, 0.9f + level.getRandom().nextFloat() * 0.2f);
    }

    // Done: eat one from the selected slot (server). Creative players eat without using it up, like with food.
    @Override
    public ItemStack finishUsingItem(ItemStack book, Level level, LivingEntity entity)
    {
        if (!(level instanceof ServerLevel) || !(entity instanceof Player player)) return book;
        ItemStack food = selectedFood(book);
        if (food.isEmpty()) return book;
        int uses = BreakfastBookBlock.usesOf(food);
        if (uses > 0)
        {
            eatSlice(book, player, uses);
            return book;
        }
        boolean stackable = food.getMaxStackSize() > 1;

        // Vanilla's eating: nutrition, effects, sounds, stats; returns what's left of the one item (a bowl, a bottle,
        // a bucket, or nothing). For creative players it returns the item itself, as nothing was used up.
        ItemStack left = food.copy().finishUsingItem(level, player);
        if (player.hasInfiniteMaterials()) return book;

        ItemStack remainder = left.isEmpty() || ItemStack.isSameItemSameComponents(left, food) ? ItemStack.EMPTY : left;
        boolean slotNowEmpty = takeOneFromSelected(book, stackable ? ItemStack.EMPTY : remainder);
        if (!remainder.isEmpty() && (stackable || !slotNowEmpty)) StorageInteractions.giveToPlayer(player, remainder);
        return book;
    }

    // One slice of the selected slot's cake: what vanilla's CakeBlock gives (hunger, saturation, the stat), then one
    // fewer use left; the last slice uses up the cake and the slot's count resets for the next one
    private void eatSlice(ItemStack book, Player player, int uses)
    {
        player.getFoodData().eat(BreakfastBookBlock.SLICE_NUTRITION, BreakfastBookBlock.SLICE_SATURATION);
        player.awardStat(Stats.EAT_CAKE_SLICE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), player.getSoundSource(), 1f, 1f);
        if (player.hasInfiniteMaterials()) return;

        int page = contents(book).selectedPage();
        int left = usesLeft(book, page);
        if (left <= 0) left = uses; // a new cake
        left--;
        if (left <= 0)
        {
            takeOneFromSelected(book, ItemStack.EMPTY);
            setUsesLeft(book, page, 0);
        }
        else
        {
            setUsesLeft(book, page, left);
        }
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

    // ---- Inventory: no taking out a half-eaten cake ----
    // MowLib's bundle-style clicks take the selected slot out on a right-click (empty cursor on the book, or the book
    // onto an empty slot); while that slot's cake is half eaten, those clicks are refused here first.

    private static boolean takeOutBlocked(ItemStack book, ClickAction action, boolean emptyTarget, Player player)
    {
        if (action != ClickAction.SECONDARY || !emptyTarget) return false;
        if (!(book.getItem() instanceof BreakfastBookItem item) || usesLeft(book, item.contents(book).selectedPage()) <= 0) return false;
        player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
        return true;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (takeOutBlocked(self, clickAction, slot.getItem().isEmpty(), player)) return true;
        return super.overrideStackedOnOther(self, slot, clickAction, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (takeOutBlocked(self, clickAction, other.isEmpty(), player)) return true;
        return super.overrideOtherStackedOnMe(self, other, slot, clickAction, player, carriedItem);
    }

    // ---- Tooltip: "Slot 2, Cake: 5/7 uses left" for every slot holding a cake ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        PagedContents contents = contents(stack);
        for (int page = 0; page < contents.pages(); page++)
        {
            ResourceStack<ItemResource> onPage = contents.stack(page);
            if (onPage.isEmpty()) continue;
            ItemStack item = onPage.resource().toStack(1);
            int uses = BreakfastBookBlock.usesOf(item);
            if (uses == 0) continue;
            int left = usesLeft(stack, page);
            tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".breakfast_book.uses",
                    page + 1, item.getHoverName(), left > 0 ? left : uses, uses).withStyle(ChatFormatting.GRAY));
        }
    }
}
