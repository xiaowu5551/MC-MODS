package cn.blockforge.generated.mod46937b35.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 让 LambDynamicLights（Lambd 的动态光源）把玩家背包里的主物品栏也当作光源来算。
 *
 * DLL 计算"活体实体从物品获得的亮度"的入口是静态方法
 * {@code LambDynLights#getLivingEntityLuminanceFromItems(LivingEntity)}，
 * 它只扫描双手持有的物品（外加 Curios/Trinkets 等兼容层）。
 * 本 Mixin 在其每个返回点上取最大值：如果实体是玩家，则遍历其主物品栏 36 格，
 * 用 DLL 自己的 {@code LambDynLights#getLuminanceFromItemStack(ItemStack, boolean)}
 * 查询每个堆叠的亮度——因此凡是 DLL 认得的光源物品（火把、灵魂火把、灯笼、
 * 海晶灯、荧石、模组光源等）放在背包里也能产生移动光源，且完全尊重
 * DLL 的亮度数据、配置开关与客户端渲染管线。
 *
 * 说明：
 *  - 用 {@link Pseudo} 声明目标类，DLL 未安装时整份 Mixin 静默跳过，不会崩溃；
 *  - 对 DLL 的两个方法采用反射调用，避免把 DLL 变成编译期依赖；
 *  - 仅注册在客户端 Mixin 配置中（DLL 本身是纯客户端模组）；
 *  - 注入必须声明 {@code cancellable = true}：{@code setReturnValue} 内部会调用
 *    {@code CallbackInfo#cancel()}，若注入点不可取消，只要背包里扫出比手上更亮的
 *    光源（例如火把放进背包而手上空着）就会当场抛出
 *    {@code CancellationException: The call ... is not cancellable}，
 *    从客户端 LivingEntity#tick 里传播出去导致游戏崩溃；
 *  - 处理器整体再包一层硬兜底：任何未预料的异常都只熄灭本功能，绝不让游戏崩。
 */
@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights")
public class LambDynLightsBackpackLightMixin {

    private static final String DLL_CLASS = "dev.lambdaurora.lambdynlights.LambDynLights";
    private static final String DLL_LUMINANCE_METHOD = "getLuminanceFromItemStack";

    /** 反射缓存：LambDynLights#getLuminanceFromItemStack(ItemStack, boolean)。 */
    private static Method luminanceMethod;
    /** 反射查找失败过则不再重试（功能退化为 DLL 默认的手持光照）。 */
    private static boolean lookupFailed;
    /** 硬兜底：扫描一旦抛出任何异常，本功能整体停用，保证绝不因此崩溃。 */
    private static boolean featureFailed;

    @Inject(method = "getLivingEntityLuminanceFromItems", at = @At("RETURN"), cancellable = true)
    private static void backpacktotem$scanInventory(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (featureFailed) return;
        try {
            int original = cir.getReturnValue();
            if (original >= 15) return;
            if (!(entity instanceof PlayerEntity player)) return;

            Method method = resolveLuminanceMethod();
            if (method == null) return;

            int max = original;
            // 与 DLL 手持逻辑一致：玩家泡在水里时按"浸水"查询亮度（如红石火把水下熄灭）
            boolean submerged = player.isSubmergedInWater();
            for (ItemStack stack : player.getInventory().main) {
                if (stack.isEmpty()) continue;
                int luminance;
                try {
                    luminance = (int) method.invoke(null, stack, submerged);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // 单个物品算不了就放弃本帧扫描，保留 DLL 原结果
                    return;
                }
                if (luminance > max) {
                    max = luminance;
                    if (max >= 15) break;
                }
            }
            if (max > original) {
                cir.setReturnValue(max);
            }
        } catch (Throwable t) {
            // Mixin/反射/DLL 内部任何意外都不允许演变成游戏崩溃：静默停用背包光源功能
            featureFailed = true;
        }
    }

    private static Method resolveLuminanceMethod() {
        if (luminanceMethod != null) return luminanceMethod;
        if (lookupFailed) return null;
        try {
            Class<?> clazz = Class.forName(DLL_CLASS);
            Method method = clazz.getMethod(DLL_LUMINANCE_METHOD, ItemStack.class, boolean.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                lookupFailed = true;
                return null;
            }
            method.setAccessible(true);
            luminanceMethod = method;
            return method;
        } catch (ReflectiveOperationException e) {
            lookupFailed = true;
            return null;
        }
    }
}
