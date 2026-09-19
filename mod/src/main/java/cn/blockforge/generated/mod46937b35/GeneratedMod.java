package cn.blockforge.generated.mod46937b35;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 背包火把与不死图腾 模组入口。
 *
 * 功能一：背包里的火把等光源物品不需要拿在手上也可以照明
 *        （兼容 LambDynamicLights，复用其移动光源管线，由
 *        {@code mixin.LambDynLightsBackpackLightMixin} 实现）。
 * 功能二：背包里的不死图腾不需要拿在手上也可以触发保命
 *        （由 {@code mixin.LivingEntityTotemMixin} 实现，
 *        效果与原版手持图腾完全一致）。
 */
public final class GeneratedMod implements ModInitializer {
    public static final String MOD_ID = "mod_46937b35";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] 已加载：背包火把照明（需 LambDynamicLights）与背包不死图腾。", MOD_ID);
    }
}
