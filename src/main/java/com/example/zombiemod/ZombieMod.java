package com.example.zombiemod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class ZombieMod implements ModInitializer {
    private static final String BUFF_TAG = "zm_buffed";
    private static final int NIGHT_START = 13000;
    private static long lastWaveDay = -1;

    private static long currentDay(MinecraftServer server) {
        return server.getOverworld().getTimeOfDay() / 24000L;
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> lastWaveDay = currentDay(server) - 1);

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ZombieEntity zombie)) return;
            if (zombie.getCommandTags().contains(BUFF_TAG)) return;
            zombie.addCommandTag(BUFF_TAG);

            long day = currentDay(world.getServer());
            double health = Math.min(20 + day * 2, 200);
            double speed  = Math.min(0.23 + day * 0.004, 0.40);
            double damage = Math.min(3 + day * 0.5, 20);

            zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
            zombie.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
            zombie.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
            zombie.setHealth(zombie.getMaxHealth());

            float chance = (float) Math.min(0.1 + day * 0.02, 0.9);
            if (world.getRandom().nextFloat() < chance) {
                boolean diamond = day >= 20;
                boolean iron = day >= 7;
                zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(
                    diamond ? Items.DIAMOND_HELMET : iron ? Items.IRON_HELMET : Items.LEATHER_HELMET));
                if (day >= 5 && world.getRandom().nextBoolean()) {
                    zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(
                        diamond ? Items.DIAMOND_CHESTPLATE : iron ? Items.IRON_CHESTPLATE : Items.LEATHER_CHESTPLATE));
                }
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            long time = world.getTimeOfDay();
            long day = time / 24000L;
            if (time % 24000L >= NIGHT_START && day != lastWaveDay) {
                lastWaveDay = day;
                int count = (int) Math.min(3 + day * 2, 40);
                for (ServerPlayerEntity p : world.getPlayers()) {
                    spawnZombies(world, p.getPos(), count);
                    p.sendMessage(Text.literal("Gün " + (day + 1) + ": " + count + " zombi gəlir!"), true);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(CommandManager.literal("horde")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> horde(ctx.getSource(), 10))
                .then(CommandManager.argument("say", IntegerArgumentType.integer(1, 50))
                    .executes(ctx -> horde(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "say")))));

            dispatcher.register(CommandManager.literal("zombieday")
                .executes(ctx -> {
                    long day = currentDay(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("Gün " + (day + 1)
                        + " | zombi canı: " + Math.min(20 + day * 2, 200)), false);
                    return (int) day;
                }));
        });
    }

    private static int horde(ServerCommandSource src, int count) {
        spawnZombies(src.getWorld(), src.getPosition(), count);
        src.sendFeedback(() -> Text.literal("Zombi dəstəsi gəlir: " + count), true);
        return count;
    }

    private static void spawnZombies(ServerWorld world, Vec3d center, int count) {
        for (int i = 0; i < count; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double dist = 15 + world.getRandom().nextDouble() * 10;
            int x = (int) (center.x + Math.cos(angle) * dist);
            int z = (int) (center.z + Math.sin(angle) * dist);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie == null) continue;
            zombie.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.getRandom().nextFloat() * 360f, 0);
            world.spawnEntity(zombie);
        }
    }
}
