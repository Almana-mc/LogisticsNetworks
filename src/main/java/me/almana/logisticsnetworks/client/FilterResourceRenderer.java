package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FilterResourceRenderer {

    public static boolean renderFluid(GuiGraphics graphics, FluidStack stack, int x, int y) {
        ItemStack bucket = new ItemStack(stack.getFluid().getBucket());
        if (!bucket.isEmpty()) {
            graphics.renderItem(bucket, x, y);
            return true;
        }

        Identifier fallback = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, fallback));
        graphics.blit(x, y, 0, 16, 16, sprite);
        return true;
    }

    public static boolean renderChemical(GuiGraphics graphics, String chemicalId, int x, int y) {
        Identifier texture = MekanismCompat.getChemicalIcon(chemicalId);
        if (texture == null) {
            return false;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
        int color = MekanismCompat.getChemicalTint(chemicalId);

        graphics.blit(x, y, 0, 16, 16, sprite);
        renderTintOverlay(graphics, x, y, color);
        return true;
    }

    private static void renderTintOverlay(GuiGraphics graphics, int x, int y, int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 96;
        } else {
            alpha = Math.min(alpha, 128);
        }
        graphics.fill(x, y, x + 16, y + 16, (alpha << 24) | (color & 0x00FFFFFF));
    }

    private FilterResourceRenderer() {
    }
}
