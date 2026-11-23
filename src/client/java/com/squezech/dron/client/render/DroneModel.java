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
 * Procedural strike quad: carbon body, gimballed camera, warhead pod, four
 * arms with counter-rotating props, antenna and landing skids.
 *
 * <p>The mesh is built in 16-pixel-per-block scale and shrunk by the renderer,
 * so the airframe reads as a 0.5 block toy without a second model layer.
 */
public class DroneModel extends EntityModel<CombatDroneEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(DronMod.id("combat_drone"), "main");
	public static final ResourceLocation TEXTURE = DronMod.id("textures/entity/combat_drone.png");

	private final ModelPart body;
	private final ModelPart camera;
	private final ModelPart warhead;
	private final ModelPart led;
	private final ModelPart propFrontLeft;
	private final ModelPart propFrontRight;
	private final ModelPart propBackLeft;
	private final ModelPart propBackRight;

	public DroneModel(ModelPart root) {
		super(RenderType::entityCutoutNoCull);
		this.body = root.getChild("body");
		this.camera = this.body.getChild("camera");
		this.warhead = this.body.getChild("warhead");
		this.led = this.body.getChild("led");
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
						// main carbon shell
						.texOffs(0, 0).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 3.5F, 9.0F)
						// top plate / heat sink
						.texOffs(32, 0).addBox(-2.5F, -2.75F, -3.5F, 5.0F, 1.0F, 6.0F)
						// tail boom
						.texOffs(0, 13).addBox(-1.0F, -1.0F, 4.0F, 2.0F, 1.5F, 4.0F),
				PartPose.ZERO);

		// Fixed FPV camera under the nose.
		body.addOrReplaceChild("camera",
				CubeListBuilder.create()
						.texOffs(0, 20).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
						.texOffs(14, 20).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offset(0.0F, 0.6F, -4.2F));

		// Warhead pod slung under the belly, with a pointed nose.
		body.addOrReplaceChild("warhead",
				CubeListBuilder.create()
						.texOffs(0, 26).addBox(-1.5F, 0.0F, -3.5F, 3.0F, 2.0F, 7.0F)
						.texOffs(24, 26).addBox(-1.0F, 0.0F, -4.5F, 2.0F, 2.0F, 1.0F)
						.texOffs(32, 26).addBox(-1.5F, 0.0F, 3.5F, 3.0F, 2.0F, 1.0F),
				PartPose.offset(0.0F, 1.5F, -0.5F));

		// Navigation LED on the tail.
		body.addOrReplaceChild("led",
				CubeListBuilder.create().texOffs(40, 26).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F),
				PartPose.offset(0.0F, -0.8F, 8.0F));

		// Four arms + rotors, tipped with spinning two-blade props.
		addRotor(body, "rotor_front_left", -4.0F, -4.0F, -1);
		addRotor(body, "rotor_front_right", 4.0F, -4.0F, 1);
		addRotor(body, "rotor_back_left", -4.0F, 4.0F, -1);
		addRotor(body, "rotor_back_right", 4.0F, 4.0F, 1);

		// Landing skids.
		body.addOrReplaceChild("skids",
				CubeListBuilder.create()
						.texOffs(0, 36).addBox(-4.5F, 0.0F, -3.5F, 9.0F, 1.0F, 1.0F)
						.texOffs(0, 40).addBox(-4.5F, 0.0F, 2.5F, 9.0F, 1.0F, 1.0F)
						.texOffs(28, 36).addBox(-4.0F, -1.0F, -3.0F, 1.0F, 1.0F, 1.0F)
						.texOffs(32, 36).addBox(3.0F, -1.0F, -3.0F, 1.0F, 1.0F, 1.0F),
				PartPose.offset(0.0F, 2.6F, 0.0F));

		return LayerDefinition.create(mesh, 64, 64);
	}

	private static void addRotor(PartDefinition body, String name, float x, float z, int side) {
		// The arm always runs from the hull out to the motor, on both sides.
		float armX = side > 0 ? 3.0F : -7.0F;
		PartDefinition rotor = body.addOrReplaceChild(name,
				CubeListBuilder.create()
						// arm
						.texOffs(42, 0).addBox(armX, -0.4F, -0.4F, 4, 1, 1)
						// motor bell
						.texOffs(52, 0).addBox(side * 7.0F - 0.75F, -1.0F, -0.75F, 1.5F, 1.5F, 1.5F),
				PartPose.offset(x, -1.2F, z));

		// Two thin blades plus a small cap: reads as a spinning prop at this size.
		rotor.addOrReplaceChild("prop",
				CubeListBuilder.create()
						.texOffs(44, 8).addBox(-4.6F, -0.1F, -0.35F, 4.3F, 0.12F, 0.7F)
						.texOffs(44, 8).addBox(0.3F, -0.1F, -0.35F, 4.3F, 0.12F, 0.7F)
						.texOffs(44, 10).addBox(-0.35F, -0.1F, -4.6F, 0.7F, 0.12F, 4.3F)
						.texOffs(44, 10).addBox(-0.35F, -0.1F, 0.3F, 0.7F, 0.12F, 4.3F)
						.texOffs(56, 12).addBox(-0.5F, -0.35F, -0.5F, 1, 0.5F, 1),
				PartPose.offset(side * 7.0F, -1.35F, 0.0F));
	}

	@Override
	public void setupAnim(CombatDroneEntity drone, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		float spin = drone.getPropellerAngle(0.0F);
		int direction = drone.getSpinDirection();
		this.propFrontLeft.yRot = spin * direction;
		this.propFrontRight.yRot = -spin * direction;
		this.propBackLeft.yRot = -spin * direction;
		this.propBackRight.yRot = spin * direction;

		// Gimbal: camera follows the pilot's aim slightly.
		this.camera.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F + 0.10F;
		this.camera.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5F;

		// Bright nav LED while armed.
		this.led.visible = true;
	}

	/** Used by the renderer to fade the warhead pod when the fuse is armed. */
	public ModelPart warhead() {
		return this.warhead;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		this.body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
