package com.eternalreturn.mobs.ai;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.entity.WebEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;

/**
 * Spiders (and cave spiders) fling a ball of web at their target from 4 to 16 blocks away when they can
 * see it, every mobs.spiderWebCooldownSeconds or so. A hit leaves the target stuck in a cobweb
 * (WebEntity). The throw takes no time, so the spider keeps chasing.
 */
public class SpiderWebGoal extends Goal {
	private final SpiderEntity spider;
	private int cooldown;

	public SpiderWebGoal(SpiderEntity spider) {
		this.spider = spider;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		if (this.cooldown > 0) {
			this.cooldown--;
			return false;
		}
		LivingEntity target = this.spider.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		double distance = this.spider.squaredDistanceTo(target);
		return distance > 16.0 && distance < 256.0 && this.spider.getVisibilityCache().canSee(target) && EternalReturnConfig.get().mobs.spiderWebCooldownSeconds > 0;
	}

	@Override
	public void start() {
		LivingEntity target = this.spider.getTarget();
		if (target == null) {
			return;
		}
		WebEntity web = new WebEntity(this.spider.getWorld(), this.spider);
		double dx = target.getX() - this.spider.getX();
		double dy = target.getBodyY(0.33) - web.getY();
		double dz = target.getZ() - this.spider.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		web.setVelocity(dx, dy + flat * 0.2, dz, 1.1F, 6.0F);
		this.spider.getWorld().spawnEntity(web);
		this.spider.playSound(SoundEvents.ENTITY_SPIDER_AMBIENT, 1.0F, 1.6F);
		int base = Math.max(1, EternalReturnConfig.get().mobs.spiderWebCooldownSeconds) * 20;
		this.cooldown = base + this.spider.getRandom().nextInt(base / 2 + 1);
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}
}
