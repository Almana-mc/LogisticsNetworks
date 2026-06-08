package me.almana.logisticsnetworks.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class LogisticsNodeRenderState extends EntityRenderState {
    public boolean renderVisible;
    public boolean wrenchVisible;
    public boolean highlighted;
    public boolean debugMode;
    public int networkColor = me.almana.logisticsnetworks.data.NetworkColors.DEFAULT;
    public int connections = NodeConnectionMask.NONE;
    public int bridgeConnections = NodeConnectionMask.NONE;
    public float minX = -0.5f;
    public float minY = 0.0f;
    public float minZ = -0.5f;
    public float maxX = 0.5f;
    public float maxY = 1.0f;
    public float maxZ = 0.5f;
    public String debugNodeId = "";
    public String debugChannels = "";

    public void resetBounds() {
        bridgeConnections = NodeConnectionMask.NONE;
        minX = -0.5f;
        minY = 0.0f;
        minZ = -0.5f;
        maxX = 0.5f;
        maxY = 1.0f;
        maxZ = 0.5f;
    }

    public void setBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
}
