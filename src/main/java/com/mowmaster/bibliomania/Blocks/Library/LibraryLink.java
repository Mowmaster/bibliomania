package com.mowmaster.bibliomania.Blocks.Library;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

// A library member's own library data, kept by its block entity (which forwards LibraryMember's methods here): which
// library it's in and whether its settings are locked. Saved under "library" and "locked", and sent to clients with
// the block entity's other data (the Reading Goggles show it).
public final class LibraryLink
{
    private static final String TAG_LIBRARY = "library";
    private static final String TAG_LOCKED = "locked";

    private @Nullable BlockPos library;
    private boolean locked;
    private final Runnable onChanged;

    // onChanged: the block entity saving itself and telling clients
    public LibraryLink(Runnable onChanged)
    {
        this.onChanged = onChanged;
    }

    public @Nullable BlockPos library()
    {
        return library;
    }

    // Only changed (and saved) when it's really different: controllers call this every scan
    public void setLibrary(@Nullable BlockPos controller)
    {
        if (controller == null ? library == null : controller.equals(library)) return;
        library = controller == null ? null : controller.immutable();
        onChanged.run();
    }

    public boolean locked()
    {
        return locked;
    }

    public void setLocked(boolean newLocked)
    {
        if (locked == newLocked) return;
        locked = newLocked;
        onChanged.run();
    }

    public void save(ValueOutput output)
    {
        // BlockPos.CODEC: a position saved as [x, y, z]
        if (library != null) output.store(TAG_LIBRARY, BlockPos.CODEC, library);
        output.putBoolean(TAG_LOCKED, locked);
    }

    public void load(ValueInput input)
    {
        library = input.read(TAG_LIBRARY, BlockPos.CODEC).orElse(null);
        locked = input.getBooleanOr(TAG_LOCKED, false);
    }
}
