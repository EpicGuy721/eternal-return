package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.climbing.SurfaceWalker;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a surface walker's move control is sent is where it heads on walls and ceilings too (Climber.steerTo); on the
 * floor following a route over the surfaces, the route's next step is where its move control takes it.
 */
@Mixin(MoveControl.class)
public abstract class ClimberSteerMixin {
	@Shadow
	@Final
	protected MobEntity entity;

	@Shadow
	public abstract void moveTo(double x, double y, double z, double speed);

	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$followRoute(CallbackInfo ci) {
		if (this.entity instanceof SurfaceWalker walker) {
			Vec3d waypoint = walker.eternalreturn$climber().floorWaypoint();
			if (waypoint != null) {
				this.moveTo(waypoint.x, waypoint.y, waypoint.z, walker.eternalreturn$climber().speed());
			}
		}
	}

	@Inject(method = "moveTo", at = @At("HEAD"))
	private void eternalreturn$steer(double x, double y, double z, double speed, CallbackInfo ci) {
		if (this.entity instanceof SurfaceWalker walker) {
			walker.eternalreturn$climber().steerTo(x, y, z, speed);
		}
	}
}
