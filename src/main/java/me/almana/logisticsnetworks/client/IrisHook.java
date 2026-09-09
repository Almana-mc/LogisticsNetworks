package me.almana.logisticsnetworks.client;

import net.irisshaders.iris.api.v0.IrisApi;

final class IrisHook {

    private IrisHook() {
    }

    static boolean renderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
