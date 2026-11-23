package com.squezech.dron.item;

import java.util.List;

import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.registry.ModEntities;
import com.squezech.dron.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Handheld controller of the strike drone.
 *
 * <p>Right click launches a fresh airframe in front of the operator and boards it.
 * While piloting, left click (attack) arms the impact fuse manually and {@code R}
 * recalls the drone.
 */
public class DroneRemoteItem extends Item {
	/** Minimal service ceiling of the airframe, in blocks above the launch point. */
	private static final double MAX_ALTITUDE = 180.0D;

	public DroneRemoteItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getVehicle() instanceof CombatDroneEntity drone) {
			// Already at the controls: leave the airframe (R does a safe recall).
			if (!level.isClientSide) {
				player.stopRiding();
			}
			return InteractionResultHolder.success(stack);
		}

		if (level.isClientSide) {
			return InteractionResultHolder.success(stack);
		}

		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResultHolder.pass(stack);
		}

		Vec3 position = launchPosition(player);
		if (position == null) {
			player.displayClientMessage(Component.translatable("dron.message.nofly").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}

		CombatDroneEntity drone = ModEntities.COMBAT_DRONE.create(serverLevel);
		if (drone == null) {
			return InteractionResultHolder.fail(stack);
		}

		drone.moveTo(position.x, position.y, position.z, player.getYRot(), 0.0F);
		drone.setOwnerUUID(player.getUUID());
		drone.setHome(player.position());
		serverLevel.addFreshEntity(drone);

		serverLevel.playSound(null, position.x, position.y, position.z, ModSounds.DRONE_LAUNCH,
				SoundSource.PLAYERS, 1.0F, 0.92F);

		player.startRiding(drone, true);
		player.getCooldowns().addCooldown(this, 30);

		return InteractionResultHolder.success(stack);
	}

	/** Free spot in front of the operator, or {@code null} if it is walled in. */
	@Nullable
	private static Vec3 launchPosition(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() < 1.0E-4D) {
			flat = new Vec3(0.0D, 0.0D, 1.0D);
		}
		flat = flat.normalize().scale(1.6D);
		Vec3 spot = player.position().add(flat).add(0.0D, 0.9D, 0.0D);

		if (!player.level().noCollision(player, player.getBoundingBox().move(spot.subtract(player.position())))) {
			Vec3 up = player.position().add(0.0D, 1.4D, 0.0D);
			if (player.level().noCollision(player, player.getBoundingBox().move(up.subtract(player.position())))) {
				return up;
			}
			return null;
		}
		return spot;
	}

	/** Soft safety rail: the operator cannot climb past the service ceiling. */
	public static double clampAltitude(Entity drone) {
		return drone.getY();
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.dron.drone_remote.desc").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.dron.drone_remote.controls")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.dron.drone_remote.reload")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}
}
