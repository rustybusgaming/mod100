package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public abstract class SkyColorMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void localweather$darkenStormSky(ClientWorld world, float tickDelta,
                                              Camera camera, SkyRenderState state,
                                              CallbackInfo ci) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.01f) return;

        int color = state.skyColor;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Overcast sky: grey. Thunder sky: very dark grey.
        float stormR = 0.55f - thunder * 0.25f;
        float stormG = 0.57f - thunder * 0.27f;
        float stormB = 0.60f - thunder * 0.25f;

        r = r + (stormR - r) * factor;
        g = g + (stormG - g) * factor;
        b = b + (stormB - b) * factor;

        state.skyColor = (0xFF << 24)
                | (Math.clamp((int) (r * 255), 0, 255) << 16)
                | (Math.clamp((int) (g * 255), 0, 255) << 8)
                | Math.clamp((int) (b * 255), 0, 255);
    }
}
