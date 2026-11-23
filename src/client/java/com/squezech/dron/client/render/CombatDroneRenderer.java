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

/**
 * Renders the airframe: yaw / pitch from the entity, bank into turns, and a
 * subtle rotor vibration that scales with speed.
 */
public class CombatDroneRenderer extends EntityRenderer<CombatDroneEntity> {
	private static final float MODEL_SCALE = 0.62F;

	private final DroneModel model;

	public CombatDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new DroneModel(context.bakeLayer(DroneModel.LAYER));
		this.shadowRadius = 0.28F;
		this.shadowStrength = 0.55F;
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

		poseStack.translate(0.0D, 0.36D, 0.0D);

		// Idle rotor vibration, stronger with speed.
		float jitter = 0.012F * Math.min(1.0F, speed * 6.0F);
		if (jitter > 0.0F) {
			long time = drone.level().getGameTime();
			poseStack.translate(Math.sin(time * 1.9D) * jitter, Math.cos(time * 2.3D) * jitter,
					Math.sin(time * 2.7D) * jitter);
		}

		poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw + 180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(bodyPitch));

		// Bank into the drift, like a racing quad.
		double side = delta.x * Math.cos(Math.toRadians(bodyYaw)) - delta.z * Math.sin(Math.toRadians(bodyYaw));
		float bank = Mth.clamp(speed * 34.0F, -30.0F, 30.0F);
		if (Math.abs(side) > 0.001D) {
			bank *= Math.signum(-side);
		}
		poseStack.mulPose(Axis.ZP.rotationDegrees(bank));

		poseStack.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
		this.model.setupAnim(drone, 0.0F, 0.0F, 0.0F, 0.0F, bodyPitch);
		this.model.renderToBuffer(poseStack, buffer.getBuffer(RenderTypeRef.entity(DroneModel.TEXTURE)),
				packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

		poseStack.popPose();

		super.render(drone, yaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	protected boolean shouldShowName(CombatDroneEntity entity) {
		return false;
	}

	/** Tiny indirection so the render type choice lives in one place. */
	private static final class RenderTypeRef {
		private RenderTypeRef() {
		}

		static net.minecraft.client.renderer.RenderType entity(ResourceLocation texture) {
			return net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);
		}
	}
}
