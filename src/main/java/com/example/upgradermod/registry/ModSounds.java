package com.example.upgradermod.registry;

import com.example.upgradermod.UpgraderMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Звуки рулетки апгрейдера.
 *
 * <p>События зарегистрированы модом, а наборы звуков описаны в assets/upgradermod/sounds.json.
 * Сейчас они используют стандартные звуковые файлы Minecraft, поэтому мод не требует
 * дополнительных бинарных ресурсов.</p>
 *
 * @author Popipok
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, UpgraderMod.MOD_ID);

    /** Короткий звук начала прокрутки. */
    public static final RegistryObject<SoundEvent> SPIN_START = register("spin_start");

    /** Звук успешного апгрейда. */
    public static final RegistryObject<SoundEvent> SPIN_SUCCESS = register("spin_success");

    /** Звук неудачного апгрейда. */
    public static final RegistryObject<SoundEvent> SPIN_FAILURE = register("spin_failure");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(UpgraderMod.MOD_ID, name)));
    }

    /**
     * Регистрирует звуковые события на mod event bus.
     *
     * @param bus шина событий мода
     */
    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
