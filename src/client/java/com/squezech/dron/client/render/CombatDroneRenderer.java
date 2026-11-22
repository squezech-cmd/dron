package com.squezech.dron.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class CombatDroneRenderer extends EntityRenderer<CombatDroneEntity> {
	private final DroneModel model;

	public CombatDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new DroneModel(context.bakeLayer(DroneModel.LAYER));
		this.shadowRadius = 0.3F;
		this.shadowStrength = 0.6F;
	}

	@Override
	public ResourceLocation getTextureLocation(CombatDroneEntity entity) {
		return DroneModel.TEXTURE;
	}

	@Override
	public void render(CombatDroneEntity drone, float yaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();

		float bodyYaw = Mth.rotLerp(partialTick, drone.yRotO, drone.getYRot());
		float bodyPitch = Mth.lerp(partialTick, drone.xRotO, drone.getXRot());

		Vec3 delta = new Vec3(drone.getX() - drone.xo, 0.0D, drone.getZ() - drone.zo);
		float speed = (float) delta.length();

		poseStack.translate(0.0D, 0.42D, 0.0D);
		poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw + 180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(bodyPitch));

		// Bank into the turn / drift, like a racing quad.
		float bank = Mth.clamp(speed * 26.0F, -26.0F, 26.0F);
		double side = delta.x * Math.cos(Math.toRadians(bodyYaw)) - delta.z * Math.sin(Math.toRadians(bodyYaw));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) Math.copySign(bank, side == 0.0D ? 1.0D : -side)));

		poseStack.scale(-0.9F, -0.9F, 0.9F);
		this.model.setupAnim(drone, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		this.model.renderToBuffer(poseStack, buffer.getBuffer(this.model.renderType(DroneModel.TEXTURE)),
				packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

		poseStack.popPose();

		super.render(drone, yaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	protected boolean shouldShowName(CombatDroneEntity entity) {
		return false;
	}
}
