package com.squezech.dron.item;

import java.util.List;

import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.registry.ModEntities;
import com.squezech.dron.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Remote controller of the FPV combat drone.
 *
 * <p>Right click with the controller: launches a fresh drone in front of the
 * pilot, boards it and hands over the controls. While piloting, WASD thrusts,
 * Space / Shift changes altitude and R (or the left mouse button) does the rest.
 */
public class DroneRemoteItem extends Item {
	public DroneRemoteItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getVehicle() instanceof CombatDroneEntity) {
			return InteractionResultHolder.pass(stack);
		}

		if (level.isClientSide) {
			return InteractionResultHolder.success(stack);
		}

		Vec3 look = player.getLookAngle();
		double x = player.getX() + look.x * 1.8D;
		double y = player.getY() + 1.1D;
		double z = player.getZ() + look.z * 1.8D;

		CombatDroneEntity drone = ModEntities.COMBAT_DRONE.create(level);
		if (drone == null) {
			return InteractionResultHolder.fail(stack);
		}

		drone.moveTo(x, y, z, player.getYRot(), 0.0F);
		drone.setOwnerUUID(player.getUUID());
		drone.setHome(player.position());

		level.addFreshEntity(drone);
		level.playSound(null, x, y, z, ModSounds.DRONE_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.9F);

		player.startRiding(drone, true);
		player.getCooldowns().addCooldown(this, 40);

		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.dron.drone_remote.desc").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.dron.drone_remote.controls")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}
}
