package net.zhaiji.cirno.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.entity.CirnoEntity;

public class InitEntity {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Cirno.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CirnoEntity>> CIRNO = ENTITIES.register(
            "cirno",
            () -> EntityType.Builder.<CirnoEntity>of(CirnoEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSummon()
                    .build("cirno")
    );
}
