package yandere;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class YandereEntity extends PathAwareEntity {
    public static final String CORPSE_TAG = "yandere_corpse";

    public enum Mood { SWEET, JEALOUS, RAGE, GLITCH }

    private UUID ownerUuid;
    Mood mood = Mood.SWEET;
    int jealousy, moodTicks, kills;
    BlockPos housePos;
    boolean staying;
    final HouseBuilder builder = new HouseBuilder();
    public final YandereChat chat = new YandereChat();

    public YandereEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.add(2, new StalkOwnerGoal(this));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    // ---------- sahiplik ----------
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwner(UUID id) { this.ownerUuid = id; }

    public ServerPlayerEntity getOwner() {
        if (ownerUuid == null || !(getWorld() instanceof ServerWorld sw)) return null;
        return sw.getServer().getPlayerManager().getPlayer(ownerUuid);
    }

    // ---------- bicak ----------
    public void equipKnife() {
        ItemStack k = new ItemStack(Items.IRON_SWORD);
        k.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bıçak").formatted(Formatting.RED));
        equipStack(EquipmentSlot.MAINHAND, k);
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    private void giveKnife(ServerPlayerEntity owner) {
        ItemStack k = new ItemStack(Items.IRON_SWORD);
        k.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Aşkımızın Bıçağı").formatted(Formatting.LIGHT_PURPLE));
        owner.giveItemStack(k);
    }

    // ---------- tick ----------
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerPlayerEntity owner = getOwner();
        if (owner == null) return;

        if (age % 10 == 0) updateJealousy(owner);
        tickMood(owner);
        if (builder.active()) tickBuild(owner);

        if (mood == Mood.SWEET && random.nextInt(300) == 0) swingHand(Hand.MAIN_HAND); // bicagiyla oynuyor
    }

    private List<VillagerEntity> nearbyVillagers(net.minecraft.entity.Entity center, double r) {
        return getWorld().getEntitiesByClass(VillagerEntity.class, center.getBoundingBox().expand(r),
                v -> v.isAlive() && !v.getCommandTags().contains(CORPSE_TAG));
    }

    private void updateJealousy(ServerPlayerEntity owner) {
        if (mood == Mood.RAGE || mood == Mood.GLITCH) return;
        int add = 0;
        for (VillagerEntity v : nearbyVillagers(owner, 8)) if (v.squaredDistanceTo(owner) < 25) add += 1;
        if (owner.currentScreenHandler instanceof MerchantScreenHandler) add += 6; // ticaret = ihanet
        if (add > 0) jealousy = Math.min(150, jealousy + add * 2);
        else jealousy = Math.max(0, jealousy - 1);
    }

    private void tickMood(ServerPlayerEntity owner) {
        switch (mood) {
            case SWEET -> { if (jealousy >= 40) setMood(Mood.JEALOUS, owner); }
            case JEALOUS -> {
                if (jealousy < 20) setMood(Mood.SWEET, owner);
                else if (jealousy >= 100) setMood(Mood.RAGE, owner);
            }
            case RAGE -> {
                moodTicks++;
                if (getTarget() == null || !getTarget().isAlive()) {
                    VillagerEntity v = pickVictim(owner);
                    if (v == null) setMood(Mood.GLITCH, owner);
                    else setTarget(v);
                }
                if (moodTicks > 1200) setMood(Mood.GLITCH, owner);
            }
            case GLITCH -> {
                moodTicks++;
                getNavigation().stop();
                if (moodTicks % 3 == 0) { setYaw(random.nextFloat() * 360f); setHeadYaw(getYaw()); }
                if (moodTicks % 5 == 0 && getWorld() instanceof ServerWorld sw)
                    sw.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                if (moodTicks >= 100) setMood(Mood.SWEET, owner);
            }
        }
    }

    private VillagerEntity pickVictim(ServerPlayerEntity owner) {
        return nearbyVillagers(owner, 24).stream()
                .min(Comparator.comparingDouble((VillagerEntity v) -> v.squaredDistanceTo(owner)))
                .orElse(null);
    }

    void setMood(Mood m, ServerPlayerEntity owner) {
        mood = m;
        moodTicks = 0;
        switch (m) {
            case JEALOUS -> chat.talk(this, owner,
                    "(OLAY) Oyuncu bir köylüyle çok vakit geçiriyor; kıskanmaya başladın. Tatlı ama tehditkâr bir şekilde sitem et.",
                    YandereChat.GENERIC_JEALOUS);
            case RAGE -> {
                equipKnife();
                getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1.5f, 0.5f);
                chat.talk(this, owner,
                        "(OLAY) Kıskançlıktan çıldırdın ve köylüyü öldürmeye gidiyorsun. Ürkütücü, kırık bir cümle kur.",
                        YandereChat.GENERIC_RAGE);
            }
            case GLITCH -> {
                setTarget(null);
                jealousy = 0;
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, false, false));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, false));
                getWorld().playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2f, 0.7f);
                chat.talk(this, owner,
                        "(OLAY) Köylüyü öldürdün ve kontrolünü kaybettin; sistemin bozuluyor. Parçalı, bozuk, korkutucu konuş.",
                        YandereChat.GENERIC_GLITCH);
            }
            case SWEET -> {
                jealousy = 0;
                setTarget(null);
                if (kills > 0)
                    chat.talk(this, owner,
                            "(OLAY) Kendine geldin. Hiçbir şey olmamış gibi masumca davran; belki istemeden küçük bir ipucu sızdır.",
                            YandereChat.CALM_AFTER);
            }
        }
    }

    public void onVillagerKilled(VillagerEntity v) {
        kills++;
        ServerPlayerEntity owner = getOwner();
        if (owner != null && mood == Mood.RAGE) setMood(Mood.GLITCH, owner);
    }

    // ---------- ev ----------
    public boolean startBuild(ServerPlayerEntity owner) {
        if (builder.active() || !(getWorld() instanceof ServerWorld w)) return false;
        BlockPos origin = HouseBuilder.findSite(w, owner);
        if (origin == null) return false;
        builder.plan(w, origin);
        return true;
    }

    private void tickBuild(ServerPlayerEntity owner) {
        BlockPos site = builder.center();
        if (site == null || !(getWorld() instanceof ServerWorld w)) return;
        Vec3d c = Vec3d.ofCenter(site);
        if (squaredDistanceTo(c) > 100) {
            if (age % 20 == 0) getNavigation().startMovingTo(c.x, c.y, c.z, 1.25);
            if (squaredDistanceTo(c) > 2500) refreshPositionAndAngles(c.x, c.y + 1, c.z - 6, 0, 0);
            return;
        }
        getLookControl().lookAt(c.x, c.y + 1, c.z);
        int n = builder.step(w, 4);
        if (n > 0 && age % 4 == 0) swingHand(Hand.MAIN_HAND);
        if (!builder.active()) {
            housePos = site;
            chat.talk(this, owner,
                    "(OLAY) Oyuncuyla senin için evi yeni bitirdin: iki yatak yan yana, mumlar, kırmızı halı. Heyecanlı, flörtöz bir şey söyle.",
                    new String[]{"Evimiz hazır~ Yataklar yan yana, çünkü... şey, yer kalmadı. Tamamen tesadüf. ♥"});
        }
    }

    // ---------- konusma ----------
    public void say(ServerPlayerEntity owner, String text) {
        Formatting color = switch (mood) {
            case SWEET -> Formatting.LIGHT_PURPLE;
            case JEALOUS -> Formatting.GOLD;
            case RAGE -> Formatting.DARK_RED;
            case GLITCH -> Formatting.RED;
        };
        MutableText body = Text.empty();
        for (String w : text.split(" ")) {
            boolean glitch = (mood == Mood.GLITCH && random.nextInt(4) == 0) || (mood == Mood.RAGE && random.nextInt(8) == 0);
            body.append(Text.literal(w + " ").setStyle(Style.EMPTY.withColor(color).withObfuscated(glitch)));
        }
        owner.sendMessage(Text.literal("<" + YandereConfig.get().name + "> ").formatted(Formatting.LIGHT_PURPLE).append(body));
        if (mood == Mood.SWEET)
            getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1f, 1.4f);
    }

    public void doAction(String a, ServerPlayerEntity owner) {
        switch (a.trim().toUpperCase(Locale.ROOT)) {
            case "BUILD_HOUSE" -> {
                if (!startBuild(owner))
                    owner.sendMessage(Text.literal("(" + YandereConfig.get().name + " uygun bir yer bulamadı)").formatted(Formatting.GRAY));
            }
            case "GIVE_KNIFE" -> giveKnife(owner);
            case "STAY" -> staying = true;
            case "FOLLOW" -> staying = false;
            case "CALM" -> { jealousy = 0; if (mood == Mood.JEALOUS) mood = Mood.SWEET; }
            case "JEALOUS" -> jealousy = Math.max(jealousy, 60);
            default -> {}
        }
    }

    public String stateLine(ServerPlayerEntity o) {
        long t = getWorld().getTimeOfDay() % 24000;
        return String.format(Locale.ROOT,
                "[Durum: ruh_hali=%s, kiskanclik=%d/100, yakin_koylu=%d, zaman=%s, oyuncu_cani=%.0f/20, evimiz_var=%s, oldurdugum_koylu=%d]",
                mood, Math.min(jealousy, 100), nearbyVillagers(o, 16).size(),
                (t >= 13000 && t < 23000) ? "gece" : "gunduz", o.getHealth(), housePos != null, kills);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp && sp.getUuid().equals(ownerUuid)) {
            chat.talk(this, sp, "(OLAY) Oyuncu sana dokundu.", YandereChat.GENERIC_SWEET);
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    // ---------- takip (oyuncu bakarsa donar) ----------
    boolean isWatchedBy(PlayerEntity p) {
        Vec3d look = p.getRotationVec(1f).normalize();
        Vec3d to = getEyePos().subtract(p.getEyePos()).normalize();
        return look.dotProduct(to) > 0.92 && p.canSee(this);
    }

    static class StalkOwnerGoal extends Goal {
        private final YandereEntity y;
        private int cooldown;

        StalkOwnerGoal(YandereEntity y) {
            this.y = y;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            ServerPlayerEntity o = y.getOwner();
            return o != null && !y.staying && !y.builder.active()
                    && (y.mood == Mood.SWEET || y.mood == Mood.JEALOUS)
                    && y.squaredDistanceTo(o) > 9;
        }

        @Override
        public boolean shouldContinue() { return canStart(); }

        @Override
        public void tick() {
            ServerPlayerEntity o = y.getOwner();
            if (o == null) return;
            double d = y.squaredDistanceTo(o);
            if (d > 576) { // cok uzaksa oyuncunun arkasina isinlan
                Vec3d back = o.getPos().subtract(o.getRotationVec(1f).multiply(1, 0, 1).normalize().multiply(5));
                y.refreshPositionAndAngles(back.x, o.getY(), back.z, 0, 0);
                y.getNavigation().stop();
                return;
            }
            if (d > 25 && y.isWatchedBy(o)) { // bakinca donup kalir
                y.getNavigation().stop();
                return;
            }
            if (--cooldown <= 0) {
                cooldown = 10;
                y.getNavigation().startMovingTo(o, 1.15);
            }
        }
    }

    // ---------- kayit ----------
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("Kills", kills);
        nbt.putBoolean("Staying", staying);
        if (housePos != null) nbt.putLong("House", housePos.asLong());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        kills = nbt.getInt("Kills");
        staying = nbt.getBoolean("Staying");
        if (nbt.contains("House")) housePos = BlockPos.fromLong(nbt.getLong("House"));
    }
}
