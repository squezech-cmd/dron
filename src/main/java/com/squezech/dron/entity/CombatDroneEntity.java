package com.squezech.dron.entity;

import java.util.Optional;
import java.util.UUID;

import com.squezech.dron.mixin.LivingEntityAccessor;
import com.squezech.dron.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small, fast, military FPV kamikaze drone.
 *
 * <p>The pilot sits inside the airframe and steers it with the controller item:
 * WASD for horizontal thrust, jump / sneak for altitude. Any hard contact with a
 * block, an entity or a fluid detonates the warhead.
 */
public class CombatDroneEntity extends Entity {
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> DATA_SIGNAL =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SPIN =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FUEL =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.INT);

	public static final int MAX_FUEL = 20 * 60 * 5;
	public static final int MAX_SIGNAL_BLOCKS = 260;
	public static final float EXPLOSION_POWER = 4.0F;
	public static final double MAX_SPEED = 1.5D;

	private static final double ACCEL = 0.085D;
	private static final double DRAG = 0.90D;
	private static final double LIFT = 1.05D;

	private int clientSpin;
	private boolean detonated;
	private Vec3 home = Vec3.ZERO;

	public CombatDroneEntity(EntityType<? extends CombatDroneEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setYRot(this.random.nextFloat() * 360.0F);
	}

	@Override
	protected void defineSynchedData() {
		this.entityData.define(DATA_OWNER, Optional.empty());
		this.entityData.define(DATA_SIGNAL, MAX_SIGNAL_BLOCKS);
		this.entityData.define(DATA_SPIN, 1);
		this.entityData.define(DATA_FUEL, MAX_FUEL);
	}

	// ------------------------------------------------------------------ state

	public UUID getOwnerUUID() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	public void setOwnerUUID(UUID uuid) {
		this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
	}

	public Player getOwner() {
		UUID uuid = this.getOwnerUUID();
		return uuid == null ? null : this.level().getPlayerByUUID(uuid);
	}

	public int getSignal() {
		return this.entityData.get(DATA_SIGNAL);
	}

	public int getFuel() {
		return this.entityData.get(DATA_FUEL);
	}

	public boolean isOperator(Player player) {
		UUID uuid = this.getOwnerUUID();
		return uuid == null || uuid.equals(player.getUUID());
	}

	public boolean isArmed() {
		return !this.detonated && !this.isRemoved();
	}

	public void setHome(Vec3 home) {
		this.home = home;
	}

	// ------------------------------------------------------------------ input

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (this.level().isClientSide) {
			return InteractionResult.SUCCESS;
		}

		if (this.getFirstPassenger() == null && this.isOperator(player)) {
			player.startRiding(this, true);
			return InteractionResult.CONSUME;
		}

		return InteractionResult.PASS;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide) {
			this.clientSpin += this.entityData.get(DATA_SPIN);
			return;
		}

		Player pilot = this.getFirstPassenger() instanceof Player p ? p : null;

		if (this.tickCount % 10 == 0) {
			if (pilot != null) {
				this.entityData.set(DATA_FUEL, Math.max(0, this.getFuel() - 10));
			}

			double range = this.position().distanceTo(this.home);
			int signal = (int) Math.round(Math.max(0.0D, 1.0D - range / MAX_SIGNAL_BLOCKS) * MAX_SIGNAL_BLOCKS);
			this.entityData.set(DATA_SIGNAL, signal);

			if (this.getFuel() <= 0) {
				this.detonate(pilot, true);
				return;
			}

			if (signal <= 0) {
				this.detonate(pilot, true);
				return;
			}
		}

		this.entityData.set(DATA_SPIN, pilot != null ? 2 : 1);

		// Sparse positional buzz: every client hears the drone from where it is.
		if (this.tickCount % 7 == 0) {
			float volume = pilot != null ? 0.22F : 0.34F;
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.DRONE_LOOP,
					SoundSource.NEUTRAL, volume, 0.88F + this.random.nextFloat() * 0.12F);
		}

		this.fly(pilot);
	}

	/** Accumulated rotor angle, used by the renderer. */
	public float getPropellerAngle(float partialTick) {
		return (this.clientSpin + partialTick * this.entityData.get(DATA_SPIN)) * 1.35F;
	}

	// ---------------------------------------------------------------- physics

	private void fly(Player pilot) {
		Vec3 velocity = this.getDeltaMovement();

		if (pilot != null) {
			float yaw = pilot.getYRot();
			double rad = Math.toRadians(yaw);
			Vec3 forward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
			Vec3 right = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));

			Vec3 thrust = forward.scale(pilot.zza * ACCEL).add(right.scale(pilot.xxa * ACCEL * 0.85D));

			boolean up = ((LivingEntityAccessor) pilot).dron$isJumping();
			boolean down = pilot.isShiftKeyDown();

			if (up) {
				thrust = thrust.add(0.0D, ACCEL * LIFT, 0.0D);
			}
			if (down) {
				thrust = thrust.add(0.0D, -ACCEL * LIFT, 0.0D);
			}

			// Rotor wash keeps a hovering drone in the air.
			if (pilot.zza == 0.0F && pilot.xxa == 0.0F && !up && !down) {
				thrust = thrust.add(0.0D, ACCEL * 0.42D, 0.0D);
			}

			velocity = velocity.add(thrust);

			this.setYRot(yaw);
			this.setXRot(pilot.getXRot() * 0.45F);
		}

		velocity = velocity.scale(DRAG);

		double speed = velocity.length();
		if (speed > MAX_SPEED) {
			velocity = velocity.normalize().scale(MAX_SPEED);
		}

		Vec3 next = this.position().add(velocity);
		AABB moved = this.getBoundingBox().move(velocity);

		boolean blocked = !this.level().noCollision(this, moved);
		boolean wet = this.level().getBlockStates(moved.deflate(0.12D))
				.anyMatch(state -> !state.getFluidState().isEmpty());

		if (blocked || wet) {
			this.setDeltaMovement(velocity.scale(0.2D));
			this.detonate(pilot, false);
			return;
		}

		this.setDeltaMovement(velocity);
		this.setPos(next.x, next.y, next.z);

		if (pilot == null && speed > 0.08D) {
			float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
			this.setYRot(yaw);
		}
	}

	// -------------------------------------------------------------- detonation

	public void detonate(Player source, boolean selfDestruct) {
		if (this.level().isClientSide || this.detonated || this.isRemoved()) {
			return;
		}
		this.detonated = true;

		Level level = this.level();

		if (this.getFirstPassenger() instanceof Player pilot) {
			pilot.stopRiding();
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.DRONE_EXPLODE,
				SoundSource.NEUTRAL, 5.0F, 0.95F + this.random.nextFloat() * 0.1F);

		level.explode(this, this.getX(), this.getY(), this.getZ(),
				selfDestruct ? 2.6F : EXPLOSION_POWER, Level.ExplosionInteraction.TNT);

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
					this.getX(), this.getY(), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
			serverLevel.sendParticles(ParticleTypes.FLAME,
					this.getX(), this.getY(), this.getZ(), 34, 0.8D, 0.8D, 0.8D, 0.07D);
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
					this.getX(), this.getY(), this.getZ(), 22, 1.1D, 0.9D, 1.1D, 0.04D);
		}

		if (source != null && source.isAlive()) {
			source.displayClientMessage(Component.translatable("dron.message.lost"), true);
		}

		this.discard();
	}

	/** Safe recall: the pilot steps out and the airframe is packed away. */
	public void recall(Player requester) {
		if (this.level().isClientSide || this.detonated || this.isRemoved()) {
			return;
		}
		if (requester != null && !this.isOperator(requester)) {
			return;
		}

		this.detonated = true;
		if (this.getFirstPassenger() instanceof Player pilot) {
			pilot.stopRiding();
		}

		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.DRONE_BEEP,
				SoundSource.PLAYERS, 0.8F, 1.0F);

		if (requester != null) {
			requester.displayClientMessage(Component.translatable("dron.message.recall"), true);
		}

		this.discard();
	}

	// --------------------------------------------------------------- behaviour

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide || this.isRemoved()) {
			return false;
		}
		this.detonate(source.getEntity() instanceof Player p ? p : null, false);
		return true;
	}

	@Override
	public boolean isControlledByLocalInstance() {
		Entity passenger = this.getFirstPassenger();
		return passenger != null && passenger.isControlledByLocalInstance();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 16384.0D;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
		return 0.28F;
	}

	// -------------------------------------------------------------------- nbt

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		UUID owner = this.getOwnerUUID();
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		tag.putInt("Fuel", this.getFuel());
		tag.putInt("Signal", this.getSignal());
		tag.putDouble("HomeX", this.home.x);
		tag.putDouble("HomeY", this.home.y);
		tag.putDouble("HomeZ", this.home.z);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			this.setOwnerUUID(tag.getUUID("Owner"));
		}
		if (tag.contains("Fuel")) {
			this.entityData.set(DATA_FUEL, tag.getInt("Fuel"));
		}
		if (tag.contains("Signal")) {
			this.entityData.set(DATA_SIGNAL, tag.getInt("Signal"));
		}
		if (tag.contains("HomeX")) {
			this.home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
		}
	}
}
