package yandere;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class YandereMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("yandere");

    public static final EntityType<YandereEntity> YANDERE = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of("yandere", "yuki"),
            EntityType.Builder.create(YandereEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f).build("yuki"));

    @Override
    public void onInitialize() {
        YandereConfig.get();
        FabricDefaultAttributeRegistry.register(YANDERE, YandereEntity.createAttributes());

        // Oyuncu chat'e yazinca Yuki cevap verir
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                find(sender).ifPresent(y -> y.chat.talk(y, sender, message.getSignedContent(), null)));

        // Yuki bir koylu oldurunce: ceset sahnesi
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof VillagerEntity v
                    && source.getAttacker() instanceof YandereEntity y
                    && v.getWorld() instanceof ServerWorld sw
                    && !v.getCommandTags().contains(YandereEntity.CORPSE_TAG)) {
                y.onVillagerKilled(v);
                Scene.leaveCorpse(sw, v);
            }
        });

        // Cesetlerden kan damlasin
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 30 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerWorld w = p.getServerWorld();
                for (VillagerEntity v : w.getEntitiesByClass(VillagerEntity.class, p.getBoundingBox().expand(24),
                        x -> x.getCommandTags().contains(YandereEntity.CORPSE_TAG))) {
                    w.spawnParticles(new DustParticleEffect(new Vector3f(0.55f, 0f, 0f), 1.3f),
                            v.getX(), v.getY() + 0.6, v.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
                CommandManager.literal("yandere")
                        .then(CommandManager.literal("spawn").executes(ctx -> {
                            spawn(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("ev").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            find(p).ifPresent(y -> y.doAction("BUILD_HOUSE", p));
                            return 1;
                        }))
                        .then(CommandManager.literal("kiskan").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            find(p).ifPresent(y -> y.jealousy = 150); // test icin
                            return 1;
                        }))
                        .then(CommandManager.literal("gitsin").executes(ctx -> {
                            find(ctx.getSource().getPlayerOrThrow()).ifPresent(YandereEntity::discard);
                            return 1;
                        }))));
    }

    static Optional<YandereEntity> find(ServerPlayerEntity p) {
        return p.getServerWorld()
                .getEntitiesByClass(YandereEntity.class, p.getBoundingBox().expand(96),
                        e -> p.getUuid().equals(e.getOwnerUuid()))
                .stream().findFirst();
    }

    static void spawn(ServerPlayerEntity p) {
        if (find(p).isPresent()) {
            p.sendMessage(Text.literal("Yuki zaten yanında~").formatted(Formatting.LIGHT_PURPLE));
            return;
        }
        ServerWorld w = p.getServerWorld();
        YandereEntity y = YANDERE.create(w);
        if (y == null) return;
        y.refreshPositionAndAngles(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0);
        y.setOwner(p.getUuid());
        y.setPersistent();
        y.equipKnife();
        y.setCustomName(Text.literal(YandereConfig.get().name));
        y.setCustomNameVisible(true);
        w.spawnEntity(y);
        y.chat.talk(y, p, "(OLAY) Oyuncuyla ilk kez karşılaştın. Kendini tanıt, ona aşık olduğunu tatlı ve hafif ürkütücü şekilde belli et.",
                new String[]{"Sonunda buldum seni~ Artık hiç yalnız kalmayacaksın. ♥ (Bıçağım mı? Sadece... aksesuar.)"});
    }
}
