package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Older combat rules:
 * <ul>
 * <li>pickaxes, axes and shovels hit as hard as they did before 1.9 (pickaxe 2, axe 3, shovel 1, plus the material's
 * bonus: wood and gold 0, stone 1, iron 2, diamond 3, netherite 4; plus the hand's 1). Attack speed is unchanged;</li>
 * <li>a sword only sweeps with Sweeping Edge on it (PlayerEntitySweepMixin);</li>
 * <li>snowballs and eggs knock back what they hit;</li>
 * <li>a firework rocket no longer boosts a gliding elytra.</li>
 * </ul>
 */
public final class OldCombat {
	private OldCombat() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (Item item : Registries.ITEM) {
				double base = item instanceof PickaxeItem ? 2.0 : item instanceof AxeItem ? 3.0 : item instanceof ShovelItem ? 1.0 : -1.0;
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

	/** A snowball or egg hit something: a push away from where it came from. */
	public static void knockback(ProjectileEntity projectile, Entity hit) {
		if (!EternalReturnConfig.get().survival.snowballsKnockBack || !(hit instanceof LivingEntity living) || projectile.getWorld().isClient) {
			return;
		}
		Vec3d push = projectile.getVelocity().multiply(1.0, 0.0, 1.0);
		if (push.lengthSquared() < 1.0E-6) {
			return;
		}
		push = push.normalize().multiply(0.4 * (1.0 - living.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)));
		living.addVelocity(push.x, 0.1, push.z);
		living.velocityModified = true;
	}
}
