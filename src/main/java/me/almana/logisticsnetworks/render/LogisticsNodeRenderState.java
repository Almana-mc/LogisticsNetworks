package me.almana.logisticsnetworks.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class LogisticsNodeRenderState extends EntityRenderState {
    public boolean renderVisible;
    public boolean wrenchVisible;
    public boolean highlighted;
    public boolean debugMode;
    public String debugNodeId = "";
    public String debugChannels = "";
}
