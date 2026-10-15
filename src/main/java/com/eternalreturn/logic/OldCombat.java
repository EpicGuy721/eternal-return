package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Older combat rules:
 * <ul>
 * <li>pickaxes, axes and shovels fall below swords as they did before 1.9: an axe 1 less than a sword of its
 * material, a pickaxe 2 less, a shovel 3 less (pickaxe 1, axe 2, shovel 0, plus the material's bonus: wood and gold
 * 0, stone 1, iron 2, diamond 3, netherite 4; plus the hand's 1). Swords and attack speeds are unchanged;</li>
 * <li>a sword only sweeps with Sweeping Edge on it (PlayerEntitySweepMixin);</li>
 * <li>snowballs and eggs knock back what they hit, players too, half as hard as a punch (survival.thrownKnockback)
 * (ThrownKnockbackMixin for players, ThrownHitKnockbackMixin for the rest);</li>
 * <li>a firework rocket no longer boosts a gliding elytra.</li>
 * </ul>
 */
public final class OldCombat {
	private OldCombat() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (Item item : Registries.ITEM) {
				double base = item instanceof PickaxeItem ? 1.0 : item instanceof AxeItem ? 2.0 : item instanceof ShovelItem ? 0.0 : -1.0;
				if (base < 0.0 || !(item instanceof ToolItem tool)) {
					continue;
				}
				AttributeModifiersComponent modifiers = item.getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
				if (modifiers == null) {
					continue;
				}
				double damage = base + tool.getMaterial().getAttackDamage();
				List<AttributeModifiersComponent.Entry> entries = new ArrayList<>();
				for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
					if (entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID)) {
						entries.add(new AttributeModifiersComponent.Entry(entry.attribute(),
								new EntityAttributeModifier(entry.modifier().id(), damage, entry.modifier().operation()), entry.slot()));
					} else {
						entries.add(entry);
					}
				}
				context.modify(item, builder -> builder.add(DataComponentTypes.ATTRIBUTE_MODIFIERS,
						new AttributeModifiersComponent(entries, modifiers.showInTooltip())));
			}
		});
	}

	/**
	 * How hard a hit from this pushes, for a hit whose knockback has the given strength: a snowball's or egg's is
	 * survival.thrownKnockback, anything else's as it was.
	 */
	public static double knockbackStrength(@Nullable Entity source, double strength) {
		return (source instanceof SnowballEntity || source instanceof EggEntity) && EternalReturnConfig.get().survival.snowballsKnockBack
				? EternalReturnConfig.get().survival.thrownKnockback : strength;
	}

	/**
	 * A snowball or egg hit a player: vanilla pushes a mob it hits but not a player (a hit doing no damage stops short
	 * for players), so the same push here, along the throw. Not in creative, nor right after another hit, as with mobs.
	 */
	public static void knockback(ProjectileEntity projectile, Entity hit) {
		if (!EternalReturnConfig.get().survival.snowballsKnockBack || !(hit instanceof PlayerEntity player) || projectile.getWorld().isClient
				|| player.getAbilities().invulnerable || player.isSpectator() || player.timeUntilRegen > 10) {
			return;
		}
		Vec3d along = projectile.getVelocity();
		if (along.horizontalLengthSquared() < 1.0E-6) {
			return;
		}
		player.takeKnockback(knockbackStrength(projectile, 0.4), -along.x, -along.z);
		player.velocityModified = true;
	}
}
