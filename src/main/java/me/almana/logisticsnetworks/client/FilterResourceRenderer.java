package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public final class FilterResourceRenderer {

    public static boolean renderFluid(GuiGraphics graphics, FluidStack stack, int x, int y) {
        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation texture = clientFluid.getStillTexture(stack);
        if (texture == null) {
            return false;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(texture);
        int color = clientFluid.getTintColor(stack);
        renderSprite(graphics, sprite, color, x, y);
        return true;
    }

    public static boolean renderChemical(GuiGraphics graphics, String chemicalId, int x, int y) {
        ResourceLocation texture = MekanismCompat.getChemicalIcon(chemicalId);
        if (texture == null) {
            return false;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(texture);
        renderSprite(graphics, sprite, MekanismCompat.getChemicalTint(chemicalId), x, y);
        return true;
    }

    private static void renderSprite(GuiGraphics graphics, TextureAtlasSprite sprite, int color, int x, int y) {
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, green, blue, 1.0f);
        graphics.blit(x, y, 0, 16, 16, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private FilterResourceRenderer() {
    }
}
