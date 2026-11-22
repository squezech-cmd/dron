package com.squezech.dron.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.squezech.dron.DronMod;
import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Procedural quadcopter: carbon body, camera turret, warhead pod, four arms with
 * spinning props and landing skids. UVs match the painted 64x64 skin.
 */
public class DroneModel extends EntityModel<CombatDroneEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(DronMod.id("combat_drone"), "main");
	public static final ResourceLocation TEXTURE = DronMod.id("textures/entity/combat_drone.png");

	private final ModelPart body;
	private final ModelPart camera;
	private final ModelPart warhead;
	private final ModelPart propFrontLeft;
	private final ModelPart propFrontRight;
	private final ModelPart propBackLeft;
	private final ModelPart propBackRight;

	public DroneModel(ModelPart root) {
		super(RenderType::entityCutoutNoCull);
		this.body = root.getChild("body");
		this.camera = this.body.getChild("camera");
		this.warhead = this.body.getChild("warhead");
		this.propFrontLeft = this.body.getChild("rotor_front_left").getChild("prop");
		this.propFrontRight = this.body.getChild("rotor_front_right").getChild("prop");
		this.propBackLeft = this.body.getChild("rotor_back_left").getChild("prop");
		this.propBackRight = this.body.getChild("rotor_back_right").getChild("prop");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create()
						.texOffs(0, 0).addBox(-4.0F, -2.0F, -5.0F, 8.0F, 4.0F, 10.0F)
						.texOffs(36, 0).addBox(-1.5F, 2.0F, -4.0F, 3.0F, 1.0F, 8.0F),
				PartPose.ZERO);

		body.addOrReplaceChild("warhead",
				CubeListBuilder.create()
						.texOffs(0, 14).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 7.0F)
						.texOffs(20, 14).addBox(-1.0F, 0.0F, 5.5F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(0.0F, 2.5F, 0.0F));

		body.addOrReplaceChild("camera",
				CubeListBuilder.create()
						.texOffs(28, 14).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
						.texOffs(40, 14).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offset(0.0F, -2.0F, -3.5F));

		addRotor(body, "rotor_front_left", -4.5F, -3.5F, -1);
		addRotor(body, "rotor_front_right", 4.5F, -3.5F, 1);
		addRotor(body, "rotor_back_left", -4.5F, 3.5F, -1);
		addRotor(body, "rotor_back_right", 4.5F, 3.5F, 1);

		body.addOrReplaceChild("skids",
				CubeListBuilder.create()
						.texOffs(0, 20).addBox(-5.0F, 0.0F, -4.0F, 10.0F, 1.0F, 1.0F)
						.texOffs(0, 24).addBox(-5.0F, 0.0F, 3.0F, 10.0F, 1.0F, 1.0F),
				PartPose.offset(0.0F, 3.0F, 0.0F));

		return LayerDefinition.create(mesh, 64, 64);
	}

	private static void addRotor(PartDefinition body, String name, float x, float z, int side) {
		PartDefinition rotor = body.addOrReplaceChild(name,
				CubeListBuilder.create()
						.texOffs(46, 0).addBox(side * 4.0F, -0.5F, -0.5F, 4, 1, 1)
						.texOffs(56, 10).addBox(side * 8.0F, -1.0F, -1.0F, 2, 2, 2),
				PartPose.offset(x, -1.0F, z));

		rotor.addOrReplaceChild("prop",
				CubeListBuilder.create()
						.texOffs(48, 20).addBox(-4.0F, -0.5F, -0.5F, 1, 0, 1)
						.texOffs(48, 20).addBox(3.0F, -0.5F, -0.5F, 1, 0, 1)
						.texOffs(48, 22).addBox(-0.5F, -0.5F, -4.0F, 1, 0, 1)
						.texOffs(48, 22).addBox(-0.5F, -0.5F, 3.0F, 1, 0, 1),
				PartPose.offset(side * 8.0F, -1.6F, 0.0F));
	}

	@Override
	public void setupAnim(CombatDroneEntity drone, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		float spin = drone.getPropellerAngle(0.0F);
		this.propFrontLeft.yRot = spin;
		this.propFrontRight.yRot = -spin;
		this.propBackLeft.yRot = -spin;
		this.propBackRight.yRot = spin;

		// Camera turret tracks slightly downwards, like a strike FPV camera.
		this.camera.xRot = headPitch * Mth.DEG_TO_RAD + 0.12F;
		this.camera.yRot = netHeadYaw * Mth.DEG_TO_RAD;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		this.body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	/** Exposed so the renderer can flash the warhead red when armed. */
	public void setArmed(boolean armed) {
		this.warhead.visible = true;
	}
}
