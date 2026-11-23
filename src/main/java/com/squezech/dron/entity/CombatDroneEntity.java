package com.squezech.dron.entity;

import java.util.Optional;
import java.util.UUID;

import com.squezech.dron.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small, fast, military FPV strike drone.
 *
 * <p>The pilot rides inside the airframe and steers it with the controller:
 * {@code W/S} thrust, {@code A/D} lateral, {@code Space/Shift} altitude, mouse to
 * aim. Any hard contact with a block, a mob, a player or a fluid detonates the
 * warhead. Beacon beams act as electronic-warfare jammers, flight controller
 * modules boost the motors, and leaving the 280 block command radius
 * self-destructs the airframe.
 *
 * <p>Flight is simulation-only: the position is integrated inside
 * {@link #tick()} instead of the vanilla travel pipeline, so the airframe never
 * clips into blocks on the way.
 */
public class CombatDroneEntity extends Entity {
	public static final double WIDTH = 0.5D;
	public static final double HEIGHT = 0.45D;

	/** Flight time before the battery runs dry (3 minutes). */
	public static final int MAX_FUEL = 20 * 60 * 3;
	/** Radius of the command link, in blocks. */
	public static final double MAX_RANGE = 280.0D;

	public static final float EXPLOSION_POWER = 3.6F;
	public static final float SELFDESTRUCT_POWER = 2.2F;

	/** Hard ceiling of the airframe, blocks per tick (36 blocks/s = 130 km/h). */
	public static final double MAX_SPEED = 1.8D;

	// Tuning shared with the client-side flight model.
	public static final double THRUST = 0.085D;
	public static final double STRAFE = 0.065D;
	public static final double LIFT = 0.095D;
	public static final double DRAG = 0.915D;
	public static final double MAX_FALL = 0.35D;

	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	/** Command link quality, 0..100 percent. */
	private static final EntityDataAccessor<Integer> DATA_SIGNAL =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.INT);
	/** Remaining battery, 0..MAX_FUEL. */
	private static final EntityDataAccessor<Integer> DATA_FUEL =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.INT);
	/** Rotor spin speed, 1 when parked, 2..3 while flying. */
	private static final EntityDataAccessor<Integer> DATA_SPIN =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.INT);
	/** True while the warhead is alive and armed. */
	private static final EntityDataAccessor<Boolean> DATA_ARMED =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.BOOLEAN);
	/** True when the flight controller module is installed. */
	private static final EntityDataAccessor<Boolean> DATA_BOOSTER =
			SynchedEntityData.defineId(CombatDroneEntity.class, EntityDataSerializers.BOOLEAN);

	/** Owner this frame, cached to avoid a UUID lookup on every tick. */
	private Player pilotCache;
	private int pilotCacheTick = -100;
	private int clientSpin;
	private boolean detonated;
	private int jammerGrace;

	/** Latest control frame received from the pilot's client. */
	/** Command station (launch point) the operator returns to. */
	private Vec3 home;
	private byte inForward;
	private byte inStrafe;
	private boolean inUp;
	private boolean inDown;
	/** Latest look direction pushed by the pilot's client. */
	private float inYaw;
	private float inPitch;

	public CombatDroneEntity(EntityType<? extends CombatDroneEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setYRot(this.random.nextFloat() * 360.0F);
		this.getEntityData().set(DATA_ARMED, true);
	}

	@Override
	protected void defineSynchedData() {
		this.entityData.define(DATA_OWNER, Optional.empty());
		this.entityData.define(DATA_SIGNAL, 100);
		this.entityData.define(DATA_FUEL, MAX_FUEL);
		this.entityData.define(DATA_SPIN, 1);
		this.entityData.define(DATA_ARMED, true);
		this.entityData.define(DATA_BOOSTER, false);
	}

	// ------------------------------------------------------------------- state

	public UUID getOwnerUUID() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	public void setOwnerUUID(UUID uuid) {
		this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
	}

	public void setHome(Vec3 home) {
		this.home = home;
	}

	public Vec3 getHome() {
		return this.home;
	}

	/** Resolved at most once per tick, on both sides. */
	public Player getOwner() {
		if (this.pilotCacheTick == this.tickCount && this.pilotCache != null) {
			return this.pilotCache;
		}
		UUID uuid = this.getOwnerUUID();
		this.pilotCache = uuid == null ? null : this.level().getPlayerByUUID(uuid);
		this.pilotCacheTick = this.tickCount;
		return this.pilotCache;
	}

	public Player getPilot() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	public boolean isPiloted() {
		return this.getPilot() != null;
	}

	public int getFuel() {
		return this.entityData.get(DATA_FUEL);
	}

	public int getSignal() {
		return this.entityData.get(DATA_SIGNAL);
	}

	public float getFuelFraction() {
		return Mth.clamp(this.getFuel() / (float) MAX_FUEL, 0.0F, 1.0F);
	}

	public float getSignalFraction() {
		return Mth.clamp(this.getSignal() / 100.0F, 0.0F, 1.0F);
	}

	public boolean isArmed() {
		return this.entityData.get(DATA_ARMED) && !this.detonated;
	}

	public boolean hasBooster() {
		return this.entityData.get(DATA_BOOSTER);
	}

	/** Flight controller module: more thrust, higher top speed, fatter battery. */
	public void installBooster() {
		this.entityData.set(DATA_BOOSTER, true);
	}

	public boolean isOperator(Player player) {
		UUID uuid = this.getOwnerUUID();
		return uuid == null || uuid.equals(player.getUUID());
	}

	/** Distance from the pilot's command station, in blocks. */
	public double getLinkRange() {
		Player owner = this.getOwner();
		return owner == null ? 0.0D : this.position().distanceTo(owner.position());
	}

	public float getSpeed() {
		return (float) this.getDeltaMovement().length();
	}

	/** +1 / -1: alternating rotor direction, purely cosmetic. */
	public int getSpinDirection() {
		return 1;
	}

	/** Accumulated rotor angle, used by the renderer. */
	public float getPropellerAngle(float partialTick) {
		int spin = this.entityData.get(DATA_SPIN);
		return (this.clientSpin + partialTick * spin) * 1.6F;
	}

	// ------------------------------------------------------------- interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (this.level().isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (!this.isArmed()) {
			return InteractionResult.PASS;
		}
		if (this.getPilot() == null && this.isOperator(player)) {
			player.startRiding(this, true);
			this.playLocalSound(ModSounds.DRONE_BEEP, 0.7F, 1.4F);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	// -------------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();

		if (!this.isArmed()) {
			return;
		}

		if (this.level().isClientSide) {
			this.clientSpin += this.entityData.get(DATA_SPIN);
			return;
		}

		if (this.isRemoved()) {
			return;
		}

		// A drone left lying around with nobody at the controls is packed away,
		// so a lost airframe never becomes litter.
		if (this.tickCount == 20 * 20 && !this.isPiloted() && !this.isVehicle()) {
			this.packAway();
			return;
		}

		this.flyTick();
	}

	private void flyTick() {
		Player pilot = this.getPilot();
		boolean booster = this.hasBooster();

		// --- battery -------------------------------------------------------
		if (pilot != null && this.tickCount % 10 == 0) {
			this.entityData.set(DATA_FUEL, Math.max(0, this.getFuel() - 10));
			if (this.getFuel() <= 0) {
				this.selfDestruct(pilot, "dron.message.battery");
				return;
			}
		}

		// --- command link / jamming ----------------------------------------
		int signal = this.computeSignal();
		this.entityData.set(DATA_SIGNAL, signal);
		if (signal <= 0) {
			this.selfDestruct(pilot, "dron.message.jam");
			return;
		}

		// --- controls ------------------------------------------------------
		Vec3 thrust = Vec3.ZERO;
		float yaw = this.getYRot();

		if (pilot != null) {
			yaw = this.inYaw;
			float forward = this.inForward / 100.0F;
			float strafe = this.inStrafe / 100.0F;
			boolean up = this.inUp;
			boolean down = this.inDown;

			// A client that stops talking must not fly the airframe into a wall.
			if (this.tickCount % 20 == 0) {
				this.inForward = 0;
				this.inStrafe = 0;
				this.inUp = false;
				this.inDown = false;
			}

			double rad = Math.toRadians(yaw);
			Vec3 dirForward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
			Vec3 dirRight = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));

			double thrustMul = booster ? 1.28D : 1.0D;
			thrust = dirForward.scale(forward * THRUST * thrustMul)
					.add(dirRight.scale(strafe * STRAFE * thrustMul));

			if (up != down) {
				thrust = thrust.add(0.0D, (up ? LIFT : -LIFT) * (booster ? 1.2D : 1.0D), 0.0D);
			}

			// Rotor wash: a hands-off quad hovers instead of dropping.
			if (forward == 0.0F && strafe == 0.0F && !up && !down) {
				thrust = thrust.add(0.0D, LIFT * 0.55D, 0.0D);
			}

			this.setYRot(yaw);
			this.setXRot(Mth.clamp(this.inPitch * 0.55F, -42.0F, 42.0F));
		}

		// --- integrate -----------------------------------------------------
		Vec3 velocity = this.getDeltaMovement().add(thrust).scale(DRAG);
		if (velocity.y < -MAX_FALL) {
			velocity = new Vec3(velocity.x, -MAX_FALL, velocity.z);
		}

		double maxSpeed = MAX_SPEED * (booster ? 1.12D : 1.0D);
		double speed = velocity.length();
		if (speed > maxSpeed) {
			velocity = velocity.scale(maxSpeed / speed);
		}

		Vec3 next = this.position().add(velocity);
		if (this.contactsWorld(next, velocity)) {
			this.detonate(pilot);
			return;
		}
		if (this.hitsEntity(next)) {
			this.detonate(pilot);
			return;
		}

		this.setDeltaMovement(velocity);
		this.setPos(next.x, next.y, next.z);
		this.updateSpin(speed);

	}

	private void updateSpin(double speed) {
		int spin;
		if (this.isPiloted()) {
			spin = speed > 0.55D ? 3 : 2;
		} else {
			spin = this.getDeltaMovement().lengthSqr() > 0.01D ? 2 : 1;
		}
		this.entityData.set(DATA_SPIN, spin);
	}

	/** Block / fluid contact test for the next position. */
	private boolean contactsWorld(Vec3 next, Vec3 velocity) {
		if (next.y < this.level().getMinBuildHeight() - 6) {
			return true;
		}
		AABB box = this.getBoundingBox().move(next.subtract(this.position())).deflate(0.06D);
		if (!this.level().noCollision(this, box)) {
			return true;
		}
		if (this.isInWater()) {
			return true;
		}

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minX = Mth.floor(box.minX);
		int minY = Mth.floor(box.minY);
		int minZ = Mth.floor(box.minZ);
		int maxX = Mth.floor(box.maxX);
		int maxY = Mth.floor(box.maxY);
		int maxZ = Mth.floor(box.maxZ);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					cursor.set(x, y, z);
					BlockState state = this.level().getBlockState(cursor);
					if (!state.getFluidState().isEmpty()) {
						return true;
					}
				}
			}
		}
		// Forward probe: fast closures must not tunnel through thin walls.
		Vec3 probe = next.add(velocity.normalize().scale(0.35D));
		return !this.level().noCollision(this, box.move(probe.subtract(next)));
	}

	/** Anything solid in the flight path is a valid target. */
	private boolean hitsEntity(Vec3 next) {
		AABB box = this.getBoundingBox().move(next.subtract(this.position())).inflate(0.08D);
		for (Entity other : this.level().getEntities(this, box)) {
			if (other == this || other.isSpectator() || other instanceof CombatDroneEntity) {
				continue;
			}
			if (other instanceof LivingEntity living && living.isDeadOrDying()) {
				continue;
			}
			if (other.canBeCollidedWith()) {
				return true;
			}
		}
		return false;
	}

	/** Server side: refresh the aim of the airframe from the pilot's view. */
	public void updateAim(float yaw, float pitch) {
		this.inYaw = yaw;
		this.inPitch = pitch;
	}

	/** Server side: store the control frame of the pilot's client. */
	public void applyInput(byte forward, byte strafe, boolean up, boolean down, float yaw, float pitch) {
		this.inForward = forward;
		this.inStrafe = strafe;
		this.inUp = up;
		this.inDown = down;
		this.inYaw = yaw;
		this.inPitch = pitch;
	}

	/**
	 * Signal quality: distance falloff plus electronic warfare.
	 *
	 * <ul>
	 *   <li>beacon beams within 22 blocks jam the video link hard;</li>
	 *   <li>every beacon within 110 blocks costs a little bandwidth.</li>
	 * </ul>
	 */
	private int computeSignal() {
		double range = this.getLinkRange();
		double quality = Mth.clamp(1.0D - range / MAX_RANGE, 0.0D, 1.0D);

		if (this.tickCount % 10 == 0 || this.jammerGrace > 0) {
			boolean jammed = false;
			int nearby = 0;
			BlockPos origin = this.blockPosition();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

			for (BlockPos cursor : BlockPos.betweenClosed(
					origin.offset(-24, -12, -24),
					origin.offset(24, 12, 24))) {
				pos.set(cursor);
				if (this.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BEACON)) {
					double distance = Math.sqrt(origin.distSqr(pos));
					nearby++;
					if (distance <= 22.0D) {
						jammed = true;
					}
				}
			}

			if (jammed) {
				this.jammerGrace = 10;
				quality *= 0.35D;
			} else if (nearby > 0) {
				quality *= Math.max(0.55D, 1.0D - nearby * 0.08D);
			} else if (this.jammerGrace > 0) {
				this.jammerGrace--;
				quality *= 0.35D;
			}
		} else if (this.jammerGrace > 0) {
			this.jammerGrace--;
			quality *= 0.35D;
		}

		return (int) Math.round(quality * 100.0D);
	}

	// ------------------------------------------------------------- detonation

	/** Kamikaze impact: full-power warhead. */
	public void detonate(Player pilot) {
		if (this.level().isClientSide || this.detonated) {
			return;
		}
		this.detonated = true;
		this.getEntityData().set(DATA_ARMED, false);

		Level level = this.level();
		if (this.getFirstPassenger() instanceof Player rider) {
			this.returnPilotHome(rider);
		}
		if (pilot != null) {
			pilot.displayClientMessage(Component.translatable("dron.message.impact"), true);
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.DRONE_EXPLODE,
				SoundSource.NEUTRAL, 5.0F, 0.92F + this.random.nextFloat() * 0.12F);
		level.explode(this, this.getX(), this.getY(), this.getZ(), EXPLOSION_POWER,
				Level.ExplosionInteraction.TNT);
		this.spawnBurst();
		this.discard();
	}

	/** Battery dead / link lost: the airframe scuttles itself. */
	public void selfDestruct(Player pilot, String reasonKey) {
		if (this.level().isClientSide || this.detonated) {
			return;
		}
		this.detonated = true;
		this.getEntityData().set(DATA_ARMED, false);

		Level level = this.level();
		if (this.getFirstPassenger() instanceof Player rider) {
			this.returnPilotHome(rider);
		}
		if (pilot != null) {
			pilot.displayClientMessage(Component.translatable(reasonKey), true);
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.DRONE_EXPLODE,
				SoundSource.NEUTRAL, 4.0F, 1.15F);
		level.explode(this, this.getX(), this.getY(), this.getZ(), SELFDESTRUCT_POWER,
				Level.ExplosionInteraction.TNT);
		this.spawnBurst();
		this.discard();
	}

	/** Safe recall: the pilot steps out and the airframe is packed away. */
	public void recall(Player requester) {
		if (this.level().isClientSide || this.detonated) {
			return;
		}
		if (requester != null && !this.isOperator(requester)) {
			return;
		}

		this.detonated = true;
		this.getEntityData().set(DATA_ARMED, false);
		if (this.getFirstPassenger() instanceof Player rider) {
			this.returnPilotHome(rider);
		}

		if (this.level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.DRONE_BEEP,
					SoundSource.NEUTRAL, 0.9F, 1.5F);
			serverLevel.sendParticles(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ(),
					10, 0.25D, 0.25D, 0.25D, 0.02D);
		}
		this.finalizeRecall(requester);
	}

	/** Same as {@link #recall} but silent (used when the airframe was abandoned). */
	public void packAway() {
		this.detonated = true;
		this.getEntityData().set(DATA_ARMED, false);
		if (this.getFirstPassenger() instanceof Player rider) {
			this.returnPilotHome(rider);
		}
		this.finalizeRecall(null);
	}

	private void finalizeRecall(Player requester) {
		if (requester != null) {
			requester.displayClientMessage(Component.translatable("dron.message.recall"), true);
		}
		this.discard();
	}

	/**
	 * The operator is back at the command station: hand the rider over to the
	 * launch point so nobody is dropped out of the sky.
	 */
	private void returnPilotHome(Player rider) {
		if (!(rider instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (this.home == null || serverPlayer.level() != this.level()) {
			return;
		}
		serverPlayer.stopRiding();
		serverPlayer.teleportTo(this.home.x, this.home.y + 0.1D, this.home.z);
		serverPlayer.fallDistance = 0.0F;
	}

	private void spawnBurst() {
		if (this.level() instanceof ServerLevel serverLevel) {
			Vec3 pos = this.position();
			serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 40, 0.9D, 0.9D, 0.9D, 0.09D);
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 26, 1.2D, 1.0D, 1.2D, 0.05D);
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 18, 0.6D, 0.6D, 0.6D, 0.12D);
		}
	}

	// --------------------------------------------------------------- behaviour

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		double y = this.getY() + this.getBbHeight() * 0.62D;
		moveFunction.accept(passenger, this.getX(), y, this.getZ());
	}

	@Override
	public boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty() && !this.detonated;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return this.getPilot();
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

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
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isControlledByLocalInstance() {
		return this.getPilot() != null && this.getPilot().isLocalPlayer();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 192.0D * 192.0D;
	}

	/**
	 * Only real blows detonate the airframe: arrows, melee and other explosions
	 * are shrugged off (the drone is not a damage sponge), but a player hitting
	 * it or another blast is a valid kill.
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide || this.isRemoved() || this.detonated) {
			return false;
		}
		boolean hostile = source.getEntity() instanceof Player
				|| source.getEntity() instanceof Projectile
				|| isExplosionDamage(source);
		if (hostile) {
			this.detonate(this.getPilot());
			return true;
		}
		return false;
	}

	/** Collision damage from the vanilla side must never apply to the airframe. */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return source.typeHolder().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
				|| isExplosionDamage(source) || super.isInvulnerableTo(source);
	}

	private static boolean isExplosionDamage(DamageSource source) {
		return source.typeHolder().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
	}

	@Override
	protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
		return dimensions.height * 0.5F;
	}

	// ---------------------------------------------------------------------- nbt

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		UUID owner = this.getOwnerUUID();
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		tag.putInt("Fuel", this.getFuel());
		if (this.home != null) {
			tag.putDouble("HomeX", this.home.x);
			tag.putDouble("HomeY", this.home.y);
			tag.putDouble("HomeZ", this.home.z);
		}
		tag.putBoolean("Booster", this.hasBooster());
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			this.setOwnerUUID(tag.getUUID("Owner"));
		}
		if (tag.contains("Fuel")) {
			this.entityData.set(DATA_FUEL, tag.getInt("Fuel"));
		}
		if (tag.contains("HomeX")) {
			this.home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
		}
		if (tag.contains("Booster")) {
			this.entityData.set(DATA_BOOSTER, tag.getBoolean("Booster"));
		}
	}

	private void playLocalSound(SoundEvent event, float volume, float pitch) {
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), event, SoundSource.NEUTRAL,
				volume, pitch);
	}
}
