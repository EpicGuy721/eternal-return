package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/**
 * Small farming rules:
 * <ul>
 * <li>shears on a vine stop it growing (a "cut" property on vines, false by default, so vines are
 * vanilla's until cut);</li>
 * <li>pink petals can be placed on still water, like lily pads;</li>
 * <li>pigs have litters: 2 or 3 piglets, or 2 to 5 if either parent was fed a golden carrot (golden
 * carrots are now pig food);</li>
 * <li>a baby animal fed a poisonous potato may be poisoned for a few seconds (half the time), and one
 * poisoned that way never grows up.</li>
 * </ul>
 */
public final class Farming {
	public static final BooleanProperty CUT = BooleanProperty.of("cut");

	/** What an animal keeps (AnimalEntityMixin): fed a golden carrot (pigs), stunted (babies). */
	public interface Holder {
		boolean eternalreturn$goldenFed();

		void eternalreturn$setGoldenFed(boolean fed);

		boolean eternalreturn$stunted();

		void eternalreturn$setStunted(boolean stunted);
	}

	private Farming() {
	}

	/** Still water with no water above holds pink petals, as it holds lily pads (petals above it are fine). */
	public static boolean waterHoldsPetals(WorldView world, BlockPos waterPos, BlockState water) {
		return world.getFluidState(waterPos).getFluid() == Fluids.WATER && world.getFluidState(waterPos.up()).isEmpty();
	}

	/** After two pigs breed (one piglet already born): the rest of the litter. */
	public static void litter(ServerWorld world, AnimalEntity mother, AnimalEntity father) {
		if (!(mother instanceof PigEntity) || !EternalReturnConfig.get().survival.pigLitters) {
			return;
		}
		Holder a = (Holder) mother;
		Holder b = (Holder) father;
		boolean golden = a.eternalreturn$goldenFed() || b.eternalreturn$goldenFed();
		a.eternalreturn$setGoldenFed(false);
		b.eternalreturn$setGoldenFed(false);
		int size = golden ? 2 + world.random.nextInt(4) : 2 + world.random.nextInt(2);
		for (int i = 1; i < size; i++) {
			PassiveEntity piglet = mother.createChild(world, father);
			if (piglet == null) {
				return;
			}
			piglet.setBaby(true);
			piglet.refreshPositionAndAngles(mother.getX() + (world.random.nextDouble() - 0.5) * 0.6, mother.getY(), mother.getZ() + (world.random.nextDouble() - 0.5) * 0.6, 0.0F, 0.0F);
			world.spawnEntityAndPassengers(piglet);
		}
	}

	/** A baby animal eats a poisonous potato: half the time poisoned for 4 seconds, and then never grows up. */
	public static void poisonousPotato(AnimalEntity baby) {
		if (baby.getRandom().nextDouble() < EternalReturnConfig.get().survival.poisonousPotatoStuntChance) {
			baby.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 0));
			((Holder) baby).eternalreturn$setStunted(true);
		}
	}
}
