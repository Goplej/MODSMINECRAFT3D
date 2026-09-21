package com.example.upgradermod.logic.providers;

import com.example.upgradermod.ModConfig;
import com.example.upgradermod.logic.ValueProvider;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Провайдер ценности по характеристикам предмета — «высшая математика»:
 * ценность = урон × вес + скорость атаки × вес + прочность × вес
 *          + броня × вес + твёрдость × вес + скорость добычи × вес
 *          + бонус за зачарования.
 * Работает для оружия, брони и инструментов; предметы без боевых
 * характеристик (материалы) возвращают 0 и оцениваются следующими
 * провайдерами (values.json, теги, аналоги, редкость).
 * ProjectE и EMC не используются вообще.
 * Приоритет: 600 (после overrides.json и values.json, перед тегами).
 *
 * @author Popipok
 */
public class StatsValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public double getValue(ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return 0.0;
            }

            double value = 0.0;
            boolean hasStats = false;

            // Урон и скорость атаки в основной руке.
            double damage = 0.0;
            Double attackSpeed = null; // итоговая скорость = 4.0 (рука) + модификаторы
            Multimap<Attribute, AttributeModifier> mainhand = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
            for (Map.Entry<Attribute, AttributeModifier> entry : mainhand.entries()) {
                Attribute attr = entry.getKey();
                AttributeModifier mod = entry.getValue();
                if (mod.getOperation() != AttributeModifier.Operation.ADDITION) {
                    continue;
                }
                if (attr == Attributes.ATTACK_DAMAGE) {
                    damage += mod.getAmount();
                    hasStats = true;
                } else if (attr == Attributes.ATTACK_SPEED) {
                    attackSpeed = (attackSpeed == null ? 4.0 : attackSpeed) + mod.getAmount();
                }
            }
            if (damage > 0.0) {
                value += damage * ModConfig.getStatsDamageWeight();
            }
            if (attackSpeed != null && attackSpeed > 0.0) {
                hasStats = true;
                value += attackSpeed * ModConfig.getStatsAttackSpeedWeight();
            }

            // Броня и твёрдость по броневым слотам.
            double armor = 0.0;
            double toughness = 0.0;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                    continue;
                }
                for (Map.Entry<Attribute, AttributeModifier> entry : stack.getAttributeModifiers(slot).entries()) {
                    AttributeModifier mod = entry.getValue();
                    if (mod.getOperation() != AttributeModifier.Operation.ADDITION) {
                        continue;
                    }
                    if (entry.getKey() == Attributes.ARMOR) {
                        armor += mod.getAmount();
                        hasStats = true;
                    } else if (entry.getKey() == Attributes.ARMOR_TOUGHNESS) {
                        toughness += mod.getAmount();
                    }
                }
            }
            if (armor > 0.0) {
                value += armor * ModConfig.getStatsArmorWeight();
                value += toughness * ModConfig.getStatsToughnessWeight();
            }

            // Скорость добычи: как быстро предмет ломает камень (инструменты).
            float miningSpeed = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
            if (miningSpeed > 1.5F) {
                hasStats = true;
                value += miningSpeed * ModConfig.getStatsMiningSpeedWeight();
            }

            if (!hasStats) {
                // Материалы и прочие предметы без характеристик — следующим провайдерам.
                return 0.0;
            }

            // Прочность — часть ценности боевого предмета.
            if (stack.isDamageableItem()) {
                value += stack.getMaxDamage() * ModConfig.getStatsDurabilityWeight();
            }

            // Бонус за зачарования: вес зависит от редкости (секция weights в конфиге).
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                value += (double) enchantWeight(entry.getKey()) * entry.getValue();
            }

            return value;
        } catch (Throwable t) {
            LOGGER.debug("StatsValueProvider error: {}", t.getMessage());
            return 0.0;
        }
    }

    /** Вес зачарования по редкости (настройки enchantWeight* в конфиге). */
    private int enchantWeight(Enchantment enchantment) {
        return switch (enchantment.getRarity()) {
            case COMMON -> ModConfig.getEnchantWeightCommon();
            case UNCOMMON -> (ModConfig.getEnchantWeightCommon() + ModConfig.getEnchantWeightRare()) / 2;
            case RARE -> ModConfig.getEnchantWeightRare();
            case VERY_RARE -> ModConfig.getEnchantWeightLegendary();
        };
    }

    @Override
    public int getPriority() {
        return 600;
    }

    @Override
    public String getName() {
        return "StatsValueProvider";
    }
}
