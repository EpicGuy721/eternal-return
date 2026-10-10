package com.eternalreturn.duck;

import com.eternalreturn.villager.VillagerNeeds;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** A villager's needs (VillagerNeeds): when each was last met, on the day clock, and its walk so far. */
public interface NeedyVillager {
	/** When the need was last met, or Long.MIN_VALUE before the villager's first tick with this mod. */
	long eternalreturn$lastMet(VillagerNeeds.Need need);

	void eternalreturn$setLastMet(VillagerNeeds.Need need, long time);

	/** Where the current walk started, or null when standing still. */
	@Nullable
	Vec3d eternalreturn$walkStart();

	void eternalreturn$setWalkStart(@Nullable Vec3d start);

	int eternalreturn$stillTicks();

	void eternalreturn$setStillTicks(int ticks);
}
