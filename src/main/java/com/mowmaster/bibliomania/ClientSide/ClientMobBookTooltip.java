package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookContents;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;

// Draws a Bestiary's tooltip picture: the mob as its spawn egg with the count on it, then lines for its name,
// babies growing, the breeding cooldown (counting down live) and how many can still eat.
// Registered in BibliomaniaClient. 26.x tooltips "extract" what to draw into a GuiGraphicsExtractor.
public class ClientMobBookTooltip implements ClientTooltipComponent
{
    private static final int ICON = 18;          // the egg's square, like an inventory slot
    private static final int LINE_HEIGHT = 10;
    private static final int GAP = 4;            // between the icon and the text
    private static final int TEXT = 0xFFFFFFFF;  // 0xAARRGGBB
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_GROWING = 0xFF7FFF7F;

    private final MobBookContents raw;

    public ClientMobBookTooltip(MobBookTooltip tooltip)
    {
        this.raw = tooltip.contents();
    }

    // Read every frame with the client's game time, so the cooldown counts down while the tooltip is open
    private static long now()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    private MobBookContents contents()
    {
        return raw.settle(now());
    }

    // The text lines, worked out fresh each time (cheap: a few strings)
    private List<Line> lines()
    {
        MobBookContents contents = contents();
        long now = now();
        List<Line> lines = new ArrayList<>();
        if (contents.isEmpty())
        {
            lines.add(new Line(key("empty").getString(), TEXT_DIM));
            return lines;
        }
        EntityType<?> type = contents.type().get();
        lines.add(new Line(key("count", type.getDescription(), contents.adults()).getString(), TEXT));
        if (contents.growing() > 0) lines.add(new Line(key("growing", contents.growing()).getString(), TEXT_GROWING));
        if (contents.onCooldown(now))
        {
            long seconds = (contents.cooldownLeft(now) + 19) / 20; // rounded up, so it reaches 0:00 when done
            // String.format: %d is a whole number, %02d pads it to two digits (4:05, not 4:5)
            String time = String.format("%d:%02d", seconds / 60, seconds % 60);
            lines.add(new Line(key("cooldown", time).getString(), TEXT_DIM));
        }
        if (contents.adults() > 0) lines.add(new Line(key("feedable", contents.breedable()).getString(), TEXT_DIM));
        return lines;
    }

    private static Component key(String name, Object... args)
    {
        return Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".mob_book." + name, args);
    }

    // A record holds one line's text and colour
    private record Line(String text, int color) {}

    private boolean hasIcon()
    {
        return !contents().isEmpty();
    }

    @Override
    public int getHeight(Font font)
    {
        int textHeight = lines().size() * LINE_HEIGHT;
        return Math.max(hasIcon() ? ICON : 0, textHeight) + 2;
    }

    @Override
    public int getWidth(Font font)
    {
        int width = 0;
        for (Line line : lines()) width = Math.max(width, font.width(line.text()));
        return width + (hasIcon() ? ICON + GAP : 0);
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics)
    {
        MobBookContents contents = contents();
        int textX = x;
        if (!contents.isEmpty())
        {
            // The mob's spawn egg, if it has one (a mod's mob might not): byId gives an Optional holder of the egg item
            ItemStack egg = SpawnEggItem.byId(contents.type().get()).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (!egg.isEmpty())
            {
                graphics.item(egg, x + 1, y + 1);
                graphics.itemDecorations(font, egg, x + 1, y + 1, String.valueOf(contents.adults()));
            }
            textX += ICON + GAP;
        }
        int lineY = y + 1;
        for (Line line : lines())
        {
            graphics.text(font, line.text(), textX, lineY, line.color(), true);
            lineY += LINE_HEIGHT;
        }
    }
}
