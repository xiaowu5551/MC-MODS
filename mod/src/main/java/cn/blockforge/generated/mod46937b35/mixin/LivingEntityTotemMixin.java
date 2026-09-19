package cn.blockforge.generated.mod46937b35.mixin;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让背包（主物品栏 36 格）里的不死图腾在致命伤害时也能生效。
 *
 * 原版逻辑：{@code LivingEntity#tryUseTotem(DamageSource)} 只在主手/副手找到图腾才生效。
 * 该方法在 1.20.1 中的结构：
 *  - 开头对 BYPASSES_INVULNERABILITY 伤害（/kill、虚空等）直接 return false（提前返回）；
 *  - 末尾 {@code return usedCopy != null;} 为最终返回。
 * 本 Mixin 注入在 TAIL（即最终返回处），因此：
 *  - 致命豁免类伤害天然被绕过，与原版手持图腾行为一致；
 *  - 手上已有图腾时原版已返回 true，直接放行，不重复触发；
 *  - 仅当双手没有图腾、但背包里有图腾时，按原版语义补发一次触发：
 *    消耗图腾、恢复 1 点生命、清除状态效果、附加 再生900/1、吸收100/1、防火800/0，
 *    发送 35 号实体状态包（客户端的图腾动画与音效都走这个状态码），
 *    并正常计入"图腾使用"统计与"图腾保护"进度。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemMixin {

    /** 原版实体状态码 35 = 使用不死图腾（客户端据此播放动画和音效）。 */
    private static final byte TOTEM_STATUS_ID = 35;

    @Inject(method = "tryUseTotem", at = @At("TAIL"), cancellable = true)
    private void backpacktotem$tryInventoryTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        // 原版已经在手上找到图腾并触发，尊重其结果
        if (cir.getReturnValueZ()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity player)) return;

        World world = player.getWorld();
        if (world.isClient) return;

        PlayerInventory inventory = player.getInventory();
        // 只扫描主物品栏（36 格，含快捷栏），与"背包"语义一致
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isOf(Items.TOTEM_OF_UNDYING)) continue;

            // 与原版 tryUseTotem 相同的消耗方式：先复制再扣减
            ItemStack usedCopy = stack.copy();
            stack.decrement(1);
            if (stack.isEmpty()) {
                inventory.setStack(slot, ItemStack.EMPTY);
            }

            // 与原版相同的成功逻辑
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.incrementStat(Stats.USED.getOrCreateStat(Items.TOTEM_OF_UNDYING));
                Criteria.USED_TOTEM.trigger(serverPlayer, usedCopy);
            }
            player.setHealth(1.0f);
            player.clearStatusEffects();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
            world.sendEntityStatus(player, TOTEM_STATUS_ID);

            cir.setReturnValue(true);
            return;
        }
    }
}
