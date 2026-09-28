package net.zhaiji.cirno.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.entity.CirnoEntity;

public class InitEntity {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Cirno.MOD_ID);

    public static final RegistryObject<EntityType<CirnoEntity>> CIRNO = ENTITIES.register(
            "cirno",
            () -> EntityType.Builder.<CirnoEntity>of(CirnoEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSummon()
                    .build("cirno")
    );
}
