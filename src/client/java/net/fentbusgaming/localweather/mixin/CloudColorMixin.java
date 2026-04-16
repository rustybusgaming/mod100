package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Darkens vanilla cloud color during storms. Automatically skipped when
 * Better Clouds is installed, since it replaces the vanilla cloud renderer
 * and reads our rain/thunder gradients directly from the world.
 */
@Environment(EnvType.CLIENT)
@Mixin(CloudRenderer.class)
public abstract class CloudColorMixin {

    @Unique
    private static final boolean BETTER_CLOUDS_LOADED =
            FabricLoader.getInstance().isModLoaded("betterclouds");

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int localweather$darkenStormClouds(int cloudColor) {
        if (BETTER_CLOUDS_LOADED) return cloudColor;

        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.01f) return cloudColor;

        float r = ((cloudColor >> 16) & 0xFF) / 255.0f;
        float g = ((cloudColor >> 8) & 0xFF) / 255.0f;
        float b = (cloudColor & 0xFF) / 255.0f;

        // Rain clouds: dark grey. Thunder clouds: very dark.
        float stormR = 0.35f - thunder * 0.15f;
        float stormG = 0.37f - thunder * 0.17f;
        float stormB = 0.40f - thunder * 0.15f;

        r = r + (stormR - r) * factor;
        g = g + (stormG - g) * factor;
        b = b + (stormB - b) * factor;

        return (cloudColor & 0xFF000000)
                | (Math.clamp((int) (r * 255), 0, 255) << 16)
                | (Math.clamp((int) (g * 255), 0, 255) << 8)
                | Math.clamp((int) (b * 255), 0, 255);
    }
}
