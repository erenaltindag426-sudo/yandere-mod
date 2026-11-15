package yandere;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.registry.tag.BlockTags;

/** Olen koylunun cesedini evine (yatagina) birakir ve etrafi kanla boyar. */
public final class Scene {
    private Scene() {}

    public static void leaveCorpse(ServerWorld w, VillagerEntity dead) {
        BlockPos home = dead.getBlockPos();
        boolean homeIsBed = false;
        try {
            GlobalPos gp = dead.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME).orElse(null);
            if (gp != null && gp.dimension() == w.getRegistryKey()) {
                home = gp.pos();
                homeIsBed = w.getBlockState(home).isIn(BlockTags.BEDS);
            }
        } catch (Exception ignored) {}

        VillagerEntity corpse = EntityType.VILLAGER.create(w);
        if (corpse == null) return;
        corpse.setVillagerData(dead.getVillagerData());
        corpse.setAiDisabled(true);
        corpse.setSilent(true);
        corpse.setPersistent();
        corpse.setHealth(1.0f);
        corpse.addCommandTag(YandereEntity.CORPSE_TAG);

        if (homeIsBed) {
            corpse.refreshPositionAndAngles(home.getX() + 0.5, home.getY() + 0.6875, home.getZ() + 0.5, 0, 0);
            corpse.sleep(home);
        } else {
            corpse.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, w.random.nextFloat() * 360f, 0);
        }
        w.spawnEntity(corpse);

        // kan (kirmizi hali) birak
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (w.random.nextInt(100) >= 45) continue;
                BlockPos p = home.add(dx, 0, dz);
                if (w.getBlockState(p).isAir() && w.getBlockState(p.down()).isSolidBlock(w, p.down()))
                    w.setBlockState(p, Blocks.RED_CARPET.getDefaultState());
            }
    }
}
