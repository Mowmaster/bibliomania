package com.mowmaster.bibliomania.ClientSide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mowmaster.bibliomania.Blocks.Book.BookBlock;
import com.mowmaster.bibliomania.Blocks.StorageBook.StorageBookBlockEntity;
import com.mowmaster.mowlib.client.MowLibClientParticles;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

// Draws a glass sphere above a placed storage book, filled as far as the book is full:
// - fluids: the fluid's own texture and colour; experience: glowing experience green; energy: a shimmering red;
//   any other type: water's texture in the type's colour
// - when something goes in, a column of it rises from a puddle on the book into the sphere; when something comes
//   out, it pours down from the sphere and spreads over the book
// All the shapes are built from quads in submit(), with the vertex data written straight into the buffer
// (submitCustomGeometry: the game calls our lambda with a VertexConsumer when it draws translucent things).
public class StorageBookRenderer implements BlockEntityRenderer<StorageBookBlockEntity, StorageBookRenderer.State>
{
    // Sphere radius and its gap above the book's pages, in blocks
    private static final float RADIUS = 0.28f;
    private static final float GAP = 0.06f;
    // How finely the sphere is cut: SEGMENTS around, BANDS from bottom to top
    private static final int SEGMENTS = 20;
    private static final int BANDS = 14;
    // How long the flow animation runs after something goes in or out (ticks)
    private static final int FLOW_TICKS = 20;
    // Full brightness (glowing), as a packed light value: block light 15 and sky light 15
    private static final int FULL_BRIGHT = 0xF000F0;

    public StorageBookRenderer(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    // ---- Extract: copy what to draw out of the block entity ----

    @Override
    public void extractRenderState(StorageBookBlockEntity book, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(book, state, partialTick, cameraPos, crumblingOverlay);
        Level level = book.getLevel();
        if (level == null) return;
        float time = level.getGameTime() + partialTick;

        state.fill = book.fullness();
        state.height = (book.getBlockState().getValue(BookBlock.THICKNESS) + 2) / 16f;
        state.glass = blockSprite(Identifier.withDefaultNamespace("block/glass"));
        style(book.type(), book.displayResource(), time, state);

        long age = level.getGameTime() - book.flowTick();
        state.flowing = book.flowTick() > 0 && age >= 0 && age < FLOW_TICKS;
        state.flowIn = book.flowIn();
        state.flowProgress = Math.min(1f, (age + partialTick) / FLOW_TICKS);
    }

    // The sprite (texture) and colour for the type's contents
    private static void style(TransportType<?> type, @Nullable Resource resource, float time, State state)
    {
        TextureAtlasSprite water = fluidSprite(FluidResource.of(Fluids.WATER));
        state.sprite = water;
        state.light = state.lightCoords;
        if (type == TransportTypes.FLUIDS.get() && resource instanceof FluidResource fluid && !fluid.isEmpty())
        {
            state.sprite = fluidSprite(fluid);
            // MowLib knows NeoForge's fluid tints (-1 = no tint, e.g. lava has its colour in the texture)
            int tint = MowLibClientParticles.fluidColor(fluid);
            state.color = 0xE0000000 | (tint == -1 ? 0xFFFFFF : tint);
            // Fluids that give off light (lava) glow
            if (fluid.getFluidType().getLightLevel() > 0) state.light = FULL_BRIGHT;
        }
        else if (type == TransportTypes.EXPERIENCE.get())
        {
            // Pulses between the two greens of an experience orb
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.15f);
            state.color = 0xE0000000 | lerpColor(0x80FF20, 0xD4FF4A, pulse);
            state.light = FULL_BRIGHT;
        }
        else if (type == TransportTypes.ENERGY.get())
        {
            // A shimmering red: the brightness ripples quickly
            float shimmer = 0.75f + 0.25f * (float) Math.sin(time * 0.6f) * (float) Math.sin(time * 0.23f);
            state.color = 0xE0000000 | lerpColor(0x600000, 0xFF3030, shimmer);
            state.light = FULL_BRIGHT;
        }
        else
        {
            state.color = 0xE0000000 | type.getRgb();
        }
    }

    // A fluid's "still" texture, from its fluid model (26.1+: fluid textures are FluidModels in the FluidStateModelSet)
    private static TextureAtlasSprite fluidSprite(FluidResource fluid)
    {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(fluid.getFluid().defaultFluidState()).stillMaterial().sprite();
    }

    // A texture from the block atlas, e.g. "minecraft:block/glass"
    private static TextureAtlasSprite blockSprite(Identifier id)
    {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
    }

    private static int lerpColor(int from, int to, float t)
    {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ---- Submit: build the shapes ----

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.sprite == null || state.glass == null) return;
        poseStack.pushPose();
        // The sphere's centre: middle of the block, GAP above the pages
        poseStack.translate(0.5, state.height + GAP + RADIUS, 0.5);
        // The pages' height, relative to the centre
        float bookY = -(GAP + RADIUS);

        // Everything is on the block atlas, drawn see-through. One lambda draws it all, contents first, glass last.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(state.sprite.atlasLocation()), (pose, buffer) -> {
            if (state.fill > 0.001f) fluidBody(buffer, pose, state);
            if (state.flowing) flow(buffer, pose, state, bookY);
            sphere(buffer, pose, state.glass, RADIUS, -RADIUS, RADIUS, 0x60FFFFFF, state.lightCoords, false);
        });
        poseStack.popPose();
    }

    // The contents: the sphere from the bottom up to the fill height (a little inside the glass), with a flat top
    private static void fluidBody(VertexConsumer buffer, PoseStack.Pose pose, State state)
    {
        float inner = RADIUS * 0.96f;
        float top = -inner + 2f * inner * state.fill;
        sphere(buffer, pose, state.sprite, inner, -inner, top, state.color, state.light, true);
        if (state.fill < 0.999f) disc(buffer, pose, state.sprite, top, ringRadius(inner, top), state.color, state.light);
    }

    // The flow animation. In: a puddle on the book (drying up as it goes) and a column rising into the sphere.
    // Out: a column pouring down from the sphere, then a puddle spreading over the book.
    private static void flow(VertexConsumer buffer, PoseStack.Pose pose, State state, float bookY)
    {
        float t = state.flowProgress;
        float puddleY = bookY + 0.01f;
        float sphereBottom = -RADIUS * 0.96f;
        float columnRadius = 0.035f;
        if (state.flowIn)
        {
            float puddle = 0.2f * (1f - t);
            if (puddle > 0.01f) disc(buffer, pose, state.sprite, puddleY, puddle, state.color, state.light);
            // The top of the column climbs to the sphere in the first half, then the bottom follows
            float columnTop = bookY + (sphereBottom - bookY) * Math.min(1f, t * 2f);
            float columnBottom = bookY + (sphereBottom - bookY) * Math.max(0f, t * 2f - 1f);
            column(buffer, pose, state.sprite, columnBottom, columnTop, columnRadius, state.color, state.light);
        }
        else
        {
            // The bottom of the column falls to the book in the first half, then the top follows it down
            float columnBottom = sphereBottom + (bookY - sphereBottom) * Math.min(1f, t * 2f);
            float columnTop = sphereBottom + (bookY - sphereBottom) * Math.max(0f, t * 2f - 1f);
            column(buffer, pose, state.sprite, columnBottom, columnTop, columnRadius, state.color, state.light);
            float puddle = 0.25f * Math.max(0f, t * 2f - 0.6f) / 1.4f;
            if (puddle > 0.01f) disc(buffer, pose, state.sprite, puddleY, Math.min(0.25f, puddle), state.color, state.light);
        }
    }

    // The width of a sphere of `radius` at height y (from its centre)
    private static float ringRadius(float radius, float y)
    {
        return (float) Math.sqrt(Math.max(0f, radius * radius - y * y));
    }

    // Part of a sphere, from height `bottom` to `top` (relative to its centre), as rings of quads.
    // Each quad shows a slice of the sprite, so the texture wraps around it.
    private static void sphere(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, float radius,
                               float bottom, float top, int color, int light, boolean scaleBands)
    {
        // A part-filled sphere gets fewer bands, so they stay about the same height
        int bands = scaleBands ? Math.max(2, Math.round(BANDS * (top - bottom) / (2f * radius))) : BANDS;
        for (int band = 0; band < bands; band++)
        {
            float y0 = bottom + (top - bottom) * band / bands;
            float y1 = bottom + (top - bottom) * (band + 1) / bands;
            float r0 = ringRadius(radius, y0);
            float r1 = ringRadius(radius, y1);
            float v0 = lerp(sprite.getV0(), sprite.getV1(), (float) band / bands);
            float v1 = lerp(sprite.getV0(), sprite.getV1(), (float) (band + 1) / bands);
            for (int segment = 0; segment < SEGMENTS; segment++)
            {
                double a0 = Math.PI * 2 * segment / SEGMENTS;
                double a1 = Math.PI * 2 * (segment + 1) / SEGMENTS;
                // Four segments to one width of the texture
                float u0 = lerp(sprite.getU0(), sprite.getU1(), (segment % 4) / 4f);
                float u1 = lerp(sprite.getU0(), sprite.getU1(), (segment % 4 + 1) / 4f);
                float x00 = (float) Math.cos(a0) * r0, z00 = (float) Math.sin(a0) * r0;
                float x01 = (float) Math.cos(a1) * r0, z01 = (float) Math.sin(a1) * r0;
                float x10 = (float) Math.cos(a0) * r1, z10 = (float) Math.sin(a0) * r1;
                float x11 = (float) Math.cos(a1) * r1, z11 = (float) Math.sin(a1) * r1;
                vertex(buffer, pose, x00, y0, z00, u0, v0, color, light, x00 / radius, y0 / radius, z00 / radius);
                vertex(buffer, pose, x01, y0, z01, u1, v0, color, light, x01 / radius, y0 / radius, z01 / radius);
                vertex(buffer, pose, x11, y1, z11, u1, v1, color, light, x11 / radius, y1 / radius, z11 / radius);
                vertex(buffer, pose, x10, y1, z10, u0, v1, color, light, x10 / radius, y1 / radius, z10 / radius);
            }
        }
    }

    // A flat circle at height y, facing up (the surface of the contents, a puddle). Each quad is a slice of the
    // circle (its first two corners are both the centre), textured by its x/z position.
    private static void disc(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, float y, float radius,
                             int color, int light)
    {
        for (int segment = 0; segment < SEGMENTS; segment++)
        {
            double a0 = Math.PI * 2 * segment / SEGMENTS;
            double a1 = Math.PI * 2 * (segment + 1) / SEGMENTS;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float uc = lerp(sprite.getU0(), sprite.getU1(), 0.5f), vc = lerp(sprite.getV0(), sprite.getV1(), 0.5f);
            vertex(buffer, pose, 0, y, 0, uc, vc, color, light, 0, 1, 0);
            vertex(buffer, pose, 0, y, 0, uc, vc, color, light, 0, 1, 0);
            vertex(buffer, pose, x1, y, z1, discU(sprite, x1, radius), discV(sprite, z1, radius), color, light, 0, 1, 0);
            vertex(buffer, pose, x0, y, z0, discU(sprite, x0, radius), discV(sprite, z0, radius), color, light, 0, 1, 0);
        }
    }

    private static float discU(TextureAtlasSprite sprite, float x, float radius)
    {
        return lerp(sprite.getU0(), sprite.getU1(), 0.5f + 0.5f * x / radius);
    }

    private static float discV(TextureAtlasSprite sprite, float z, float radius)
    {
        return lerp(sprite.getV0(), sprite.getV1(), 0.5f + 0.5f * z / radius);
    }

    // A thin upright tube from `bottom` to `top` (the flowing stream)
    private static void column(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, float bottom, float top,
                               float radius, int color, int light)
    {
        if (top - bottom < 0.005f) return;
        int sides = 8;
        float v1 = lerp(sprite.getV0(), sprite.getV1(), Math.min(1f, (top - bottom) * 4f));
        for (int side = 0; side < sides; side++)
        {
            double a0 = Math.PI * 2 * side / sides;
            double a1 = Math.PI * 2 * (side + 1) / sides;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float u0 = lerp(sprite.getU0(), sprite.getU1(), (float) side / sides);
            float u1 = lerp(sprite.getU0(), sprite.getU1(), (float) (side + 1) / sides);
            vertex(buffer, pose, x0, bottom, z0, u0, sprite.getV0(), color, light, x0 / radius, 0, z0 / radius);
            vertex(buffer, pose, x1, bottom, z1, u1, sprite.getV0(), color, light, x1 / radius, 0, z1 / radius);
            vertex(buffer, pose, x1, top, z1, u1, v1, color, light, x1 / radius, 0, z1 / radius);
            vertex(buffer, pose, x0, top, z0, u0, v1, color, light, x0 / radius, 0, z0 / radius);
        }
    }

    // One corner of a quad: position, colour (0xAARRGGBB), texture position, no damage overlay, light, and the
    // direction the surface faces (for shading). Entity render types need all of these, in this order.
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int color, int light, float nx, float ny, float nz)
    {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static float lerp(float from, float to, float t)
    {
        return from + (to - from) * t;
    }

    // The sphere is above the block: keep drawing it when only it is on screen (NeoForge culls by this box)
    @Override
    public AABB getRenderBoundingBox(StorageBookBlockEntity book)
    {
        BlockPos pos = book.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
    }

    public static class State extends BlockEntityRenderState
    {
        float fill;
        float height;
        @Nullable TextureAtlasSprite sprite;
        @Nullable TextureAtlasSprite glass;
        int color;
        int light;
        boolean flowing;
        boolean flowIn;
        float flowProgress;
    }
}
