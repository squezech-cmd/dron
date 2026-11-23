package com.squezech.dron.probe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Compile-only smoke test: every API call used by the rewritten mod must compile
 * against the Minecraft 1.20.1 client classpath.
 */
public final class ApiSmoke {
    private static final EntityDataAccessor<Integer> DATA =
            SynchedEntityData.defineId(SmokeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLAG =
            SynchedEntityData.defineId(SmokeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FLOAT =
            SynchedEntityData.defineId(SmokeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> OWNER =
            SynchedEntityData.defineId(SmokeEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public static void common() {
        EntityType<SmokeEntity> type = EntityType.Builder
                .<SmokeEntity>of(SmokeEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.45F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("smoke");
        SoundEvent ev = SoundEvent.createVariableRangeEvent(new ResourceLocation("dron", "smoke"));

        Level level = null;
        if (level != null) {
            level.explode(null, 0, 0, 0, 4.0F, Level.ExplosionInteraction.TNT);
            level.playSound(null, new BlockPos(0, 0, 0), ev, SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.getEntities((Entity) null, new AABB(0, 0, 0, 1, 1, 1), e -> true);
            BlockHitResult hit = level.clip(new ClipContext(Vec3.ZERO, Vec3.ZERO, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY, null));
            BlockState state = level.getBlockState(BlockPos.ZERO);
            level.noCollision(null, new AABB(0, 0, 0, 1, 1, 1));
        }

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(1);
        buf.writeVarInt(2);
        buf.writeFloat(0.5F);
        buf.writeBoolean(true);
        buf.writeUUID(java.util.UUID.randomUUID());
        ClientPlayNetworking.send(new ResourceLocation("dron", "input"), buf);
        ServerPlayNetworking.registerGlobalReceiver(new ResourceLocation("dron", "input"),
                (server, player, handler, b, sender) -> server.execute(() -> b.readByte()));

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, 0, 0, 0, 1, 0, 0, 0, 0);
        }
    }

    public static void client() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        Input input = player.input;
        float fwd = input.forwardImpulse;
        boolean jump = input.jumping;
        boolean shift = input.shiftKeyDown;

        KeyMapping km = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.dron.x", GLFW.GLFW_KEY_R, "key.categories.dron"));
        boolean down = km.isDown();
        while (km.consumeClick()) {
            break;
        }
        mc.options.keyUp.isDown();
        mc.options.keyJump.isDown();
        mc.options.keyShift.isDown();
        mc.options.keyAttack.isDown();
        mc.options.hideGui = true;

        Camera camera = mc.gameRenderer.getMainCamera();
        camera.setRotation(0.0F, 0.0F);
        camera.setPosition(0.0D, 0.0D, 0.0D);
        Vec3 pos = camera.getPosition();
        Entity cam = mc.cameraEntity;
        Entity look = mc.crosshairPickEntity;
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Vec3 loc = hit.getLocation();
        }
        if (mc.level != null && mc.level.getEntity(1) instanceof Entity e) {
            e.getId();
        }
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        GuiGraphics g = null;
        if (g != null) {
            g.fill(0, 0, 10, 10, 0xFF00FF00);
            g.fillGradient(0, 0, 10, 10, 0xFF000000, 0x00000000);
            g.drawString(font, "x", 0, 0, 0xFFFFFFFF, false);
            g.drawCenteredString(font, "x", 0, 0, 0xFFFFFFFF);
            g.enableScissor(0, 0, 10, 10);
            g.disableScissor();
            g.pose().pushPose();
            g.pose().popPose();
            g.blit(new ResourceLocation("dron", "textures/gui/x.png"), 0, 0, 0, 0, 10, 10, 10, 10);
            g.renderOutline(0, 0, 10, 10, 0xFF00FF00);
            g.hLine(0, 10, 0, 0xFF00FF00);
            g.vLine(0, 0, 10, 0xFF00FF00);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.input.forwardImpulse > 0.1F) {
                ClientPlayNetworking.send(new ResourceLocation("dron", "in"), PacketByteBufs.create());
            }
        });
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            int w = graphics.guiWidth();
            graphics.fill(0, 0, w, 1, 0xFF00FF00);
        });

        EntityModelLayerRegistry.registerModelLayer(new ModelLayerLocation(new ResourceLocation("dron", "m"), "main"),
                ApiSmoke::layer);
        EntityRendererRegistry.register(null, ApiSmoke::renderer);

        mc.getSoundManager().play(new Buzz(null));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(null, 1.0F, 1.0F));
    }

    private static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -5.0F, 8.0F, 4.0F, 10.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("child", CubeListBuilder.create().texOffs(0, 0).addBox(0, 0, 0, 1, 1, 1),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static EntityRenderer<SmokeEntity> renderer(EntityRendererProvider.Context ctx) {
        return new EntityRenderer<>(ctx) {
            @Override
            public ResourceLocation getTextureLocation(SmokeEntity entity) {
                return new ResourceLocation("dron", "textures/entity/x.png");
            }

            @Override
            public void render(SmokeEntity entity, float yaw, float partialTick, PoseStack poseStack,
                    MultiBufferSource buffer, int packedLight) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.translate(0.0D, 0.0D, 0.0D);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.popPose();
                super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
            }
        };
    }

    static final class Buzz extends AbstractTickableSoundInstance {
        Buzz(SoundEvent event) {
            super(event, SoundSource.NEUTRAL, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 1.0F;
            this.pitch = 1.0F;
        }

        @Override
        public void tick() {
            this.volume = 0.5F;
            this.pitch = 1.2F;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        @Override
        public boolean canPlaySound(SoundManager manager) {
            return !this.isStopped();
        }
    }

    static final class SmokeEntity extends Entity {
        SmokeEntity(EntityType<? extends SmokeEntity> type, Level level) {
            super(type, level);
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(DATA, 0);
            this.entityData.define(FLAG, false);
            this.entityData.define(FLOAT, 0.0F);
            this.entityData.define(OWNER, java.util.Optional.empty());
        }

        @Override
        public void tick() {
            super.tick();
            this.entityData.set(DATA, this.entityData.get(DATA) + 1);
            this.setDeltaMovement(Vec3.ZERO);
            this.setYRot(0.0F);
            this.setXRot(0.0F);
            this.setPos(this.getX(), this.getY(), this.getZ());
            if (this.isRemoved()) {
                this.discard();
            }
            if (this.level().isClientSide) {
                return;
            }
            this.positionRider(this);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return true;
        }

        @Override
        public boolean isControlledByLocalInstance() {
            return this.getFirstPassenger() instanceof Player p && p.isLocalPlayer();
        }

        @Override
        public InteractionResult interact(Player player, InteractionHand hand) {
            if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("x"), true);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public boolean isPickable() {
            return true;
        }

        @Override
        public boolean canBeCollidedWith() {
            return false;
        }

        @Override
        public boolean isAttackable() {
            return true;
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
            tag.putInt("x", 1);
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        public boolean isNoGravity() {
            return true;
        }

        @Override
        public boolean shouldRenderAtSqrDistance(double d) {
            return d < 4096.0D;
        }
    }
}
