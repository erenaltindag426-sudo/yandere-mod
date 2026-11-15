package yandere;

import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;

/** 7x7 bir ev planlar ve her tick birkac blok koyar. */
public class HouseBuilder {
    private record P(BlockPos pos, BlockState state) {}

    private final ArrayDeque<P> queue = new ArrayDeque<>();
    private BlockPos center;

    public boolean active() { return !queue.isEmpty(); }
    public BlockPos center() { return center; }

    /** Oyuncunun cevresinde duz, sulak olmayan bir yer ara. Bulamazsa null. */
    public static BlockPos findSite(ServerWorld w, ServerPlayerEntity o) {
        for (int i = 0; i < 6; i++) {
            double yaw = Math.toRadians(o.getYaw() + i * 60);
            int cx = o.getBlockX() + (int) Math.round(-Math.sin(yaw) * 10);
            int cz = o.getBlockZ() + (int) Math.round(Math.cos(yaw) * 10);
            int x0 = cx - 3, z0 = cz - 3;
            int[][] pts = {{x0, z0}, {x0 + 6, z0}, {x0, z0 + 6}, {x0 + 6, z0 + 6}, {cx, cz}};
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            boolean ok = true;
            for (int[] p : pts) {
                int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]);
                if (!w.getBlockState(new BlockPos(p[0], top - 1, p[1])).getFluidState().isEmpty()) ok = false;
                min = Math.min(min, top);
                max = Math.max(max, top);
            }
            if (ok && max - min <= 3) return new BlockPos(x0, max, z0);
        }
        return null;
    }

    /** origin = evin kuzeybati kosesi, y = zemin ustu (ilk bos blok). */
    public void plan(ServerWorld w, BlockPos origin) {
        queue.clear();
        int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
        center = new BlockPos(x0 + 3, y0, z0 + 3);

        BlockState air = Blocks.AIR.getDefaultState();
        BlockState planks = Blocks.OAK_PLANKS.getDefaultState();
        BlockState log = Blocks.OAK_LOG.getDefaultState();
        BlockState floor = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();

        // 1) alani temizle
        for (int y = y0; y <= y0 + 5; y++)
            for (int x = 0; x < 7; x++)
                for (int z = 0; z < 7; z++) {
                    BlockPos p = new BlockPos(x0 + x, y, z0 + z);
                    if (!w.getBlockState(p).isAir()) queue.add(new P(p, air));
                }

        // 2) temel + zemin
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++) {
                for (int yy = y0 - 2; yy >= y0 - 8; yy--) {
                    BlockPos p = new BlockPos(x0 + x, yy, z0 + z);
                    BlockState s = w.getBlockState(p);
                    if (s.isAir() || s.isReplaceable() || !s.getFluidState().isEmpty()) queue.add(new P(p, cobble));
                    else break;
                }
                queue.add(new P(new BlockPos(x0 + x, y0 - 1, z0 + z), floor));
            }

        // 3) duvarlar
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 7; x++)
                for (int z = 0; z < 7; z++) {
                    boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                    if (!edge) continue;
                    BlockPos p = new BlockPos(x0 + x, y0 + y, z0 + z);
                    boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                    BlockState s = corner ? log : planks;
                    if (y == 1 && ((x == 0 && z == 3) || (x == 6 && z == 3) || (x == 3 && z == 0))) s = Blocks.GLASS.getDefaultState();
                    if (x == 3 && z == 6 && y <= 1) {
                        s = Blocks.SPRUCE_DOOR.getDefaultState()
                                .with(DoorBlock.FACING, Direction.SOUTH)
                                .with(DoorBlock.HALF, y == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                    }
                    queue.add(new P(p, s));
                }

        // 4) cati
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++)
                queue.add(new P(new BlockPos(x0 + x, y0 + 4, z0 + z), planks));

        // 5) ic dekor: iki yatak yan yana, halı, mum, sandik, masa, mesale
        BlockState bed = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH);
        for (int bx = 1; bx <= 2; bx++) {
            queue.add(new P(new BlockPos(x0 + bx, y0, z0 + 2), bed.with(BedBlock.PART, BedPart.FOOT)));
            queue.add(new P(new BlockPos(x0 + bx, y0, z0 + 1), bed.with(BedBlock.PART, BedPart.HEAD)));
        }
        for (int x = 2; x <= 4; x++)
            for (int z = 3; z <= 4; z++)
                queue.add(new P(new BlockPos(x0 + x, y0, z0 + z), Blocks.RED_CARPET.getDefaultState()));
        queue.add(new P(new BlockPos(x0 + 4, y0, z0 + 5),
                Blocks.RED_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true)));
        queue.add(new P(new BlockPos(x0 + 5, y0, z0 + 2), Blocks.CRAFTING_TABLE.getDefaultState()));
        queue.add(new P(new BlockPos(x0 + 5, y0, z0 + 3), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST)));
        for (int[] t : new int[][]{{5, 1}, {5, 5}, {1, 5}})
            queue.add(new P(new BlockPos(x0 + t[0], y0, z0 + t[1]), Blocks.TORCH.getDefaultState()));
    }

    /** En fazla n blok koyar, koyulan sayiyi dondurur. */
    public int step(ServerWorld w, int n) {
        int placed = 0;
        while (placed < n && !queue.isEmpty()) {
            P p = queue.poll();
            w.setBlockState(p.pos(), p.state(), Block.NOTIFY_LISTENERS);
            if (placed == 0 && !p.state().isAir())
                w.playSound(null, p.pos(), p.state().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.8f, 1f);
            placed++;
        }
        return placed;
    }
}
