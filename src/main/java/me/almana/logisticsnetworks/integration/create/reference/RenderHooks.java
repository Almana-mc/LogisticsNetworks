// Enable when 26.1.2 is supported.
/*
Source owners: LogisticsNodeRenderer, NodeHighlightQueue, NodeHighlightRenderer

private static final Map<NodeAttachmentKey, LogisticsNodeEntity> nodesByAttachment = new HashMap<>();

NodeRenderContext context = CreateCompat.getRenderContext(entity, partialTick);
if (context == null) {
    return;
}

Vec3 offset = context.position().subtract(entity.getPosition(partialTick));
poseStack.translate(offset.x, offset.y, offset.z);
poseStack.pushPose();
poseStack.mulPose(context.rotation());

if (isHighlighted) {
    NodeHighlightRenderer.queue(context, 0.15F, 0.45F, 1.0F, 0.35F, true);
} else if (isHoldingWrench) {
    NodeHighlightRenderer.queue(context, 0.0F, 1.0F, 0.0F, 0.35F, false);
    renderWrenchDebug(entity, context.rotation(), poseStack, buffer, light);
}

AABB shapeBounds = shapeBounds(context.shapeLevel(), context.shapePos());
NodeAttachmentKey attachmentKey = context.attachmentKey();
LogisticsNodeEntity neighbor = nodesByAttachment.get(relative(attachmentKey, direction));

private static NodeAttachmentKey relative(NodeAttachmentKey key, Direction direction) {
    return new NodeAttachmentKey(key.contraptionId(), key.position().relative(direction));
}

for (Entity entity : mc.level.entitiesForRendering()) {
    if (entity instanceof LogisticsNodeEntity node && node.isActive()) {
        NodeRenderContext context = CreateCompat.getRenderContext(node, partialTick);
        if (context != null) {
            nodesByAttachment.put(context.attachmentKey(), node);
        }
    }
}

record HighlightRequest(Vec3 position, Quaternionf rotation, float red, float green, float blue, float alpha,
        boolean xray) {
}

static void queue(NodeRenderContext context, float red, float green, float blue, float alpha, boolean xray) {
    NodeHighlightQueue.add(context.position(), context.rotation(), red, green, blue, alpha, xray);
}

Vec3 offset = request.position().subtract(cameraPosition);
poseStack.translate(offset.x, offset.y, offset.z);
poseStack.mulPose(request.rotation());
renderBox(poseStack.last().pose(), bufferSource, request);
*/
