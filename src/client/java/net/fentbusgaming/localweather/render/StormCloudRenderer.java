package net.fentbusgaming.localweather.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Renders blocky, Minecraft-style storm clouds over weather zones.
 * Each zone is divided into a grid of cloud cells. A hash function determines
 * which cells are filled, producing patchy cloud coverage. Filled cells are
 * drawn as 3D boxes (top face + 4 sides) with shading, like vanilla clouds.
 */
@Environment(EnvType.CLIENT)
public class StormCloudRenderer {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;

    /** Size of each cloud "pixel" in blocks — vanilla uses 12, we use 16 for a chunkier storm look. */
    private static final int CELL_SIZE = 16;
    /** Vertical thickness of cloud boxes in blocks. */
    private static final float CLOUD_THICKNESS = 6.0f;
    /** Base height of the cloud layer bottom. */
    private static final float CLOUD_BASE = 191.0f;
    /** How far away clouds are visible (in blocks). */
    private static final float MAX_DIST = ZONE_SIZE * 4.5f;
    /** Clouds drift speed (blocks per tick). */
    private static final float DRIFT_SPEED = 0.4f;

    /** Number of cells per zone side. */
    private static final int CELLS_PER_ZONE = ZONE_SIZE / CELL_SIZE; // 16

    /** Cloud coverage threshold — lower = more cloud cells filled. */
    private static final float COVERAGE_RAIN = 0.55f;
    private static final float COVERAGE_THUNDER = 0.70f;
    private static final float COVERAGE_SNOW = 0.50f;

    /** Cloud cell opacity by weather type. */
    private static final float ALPHA_RAIN = 0.65f;
    private static final float ALPHA_THUNDER = 0.82f;
    private static final float ALPHA_SNOW = 0.55f;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(StormCloudRenderer::render);
    }

    /**
     * Deterministic hash to decide if a cloud cell is filled.
     * Uses bit mixing for good spatial distribution without noise textures.
     */
    private static int cellHash(int cx, int cz, int layer) {
        int h = cx * 374761393 + cz * 668265263 + layer * 2147483647;
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        return h;
    }

    /** Returns 0.0–1.0 from the hash for coverage threshold comparison. */
    private static float cellNoise(int cx, int cz, int layer) {
        return (cellHash(cx, cz, layer) & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private static void render(WorldRenderContext context) {
        Map<Long, ClientWeatherHandler.ZoneState> zones = ClientWeatherHandler.getZoneStates();
        if (zones.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();

        // Cloud drift offset based on game time and wind direction
        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        long worldTime = client.world != null ? client.world.getTime() : 0;
        float driftMag = (worldTime + tickDelta) * DRIFT_SPEED;
        float driftX = (float) (driftMag * ClientWeatherHandler.getWindDirX());
        float driftZ = (float) (driftMag * ClientWeatherHandler.getWindDirZ());

        boolean anyVisible = false;
        for (ClientWeatherHandler.ZoneState s : zones.values()) {
            if (s.weather == WeatherZone.WeatherType.CLEAR || s.transitionProgress < 0.05f) continue;
            double dx = (s.zoneX + 0.5) * ZONE_SIZE - cam.x;
            double dz = (s.zoneZ + 0.5) * ZONE_SIZE - cam.z;
            if (dx * dx + dz * dz < MAX_DIST * MAX_DIST) {
                anyVisible = true;
                break;
            }
        }
        if (!anyVisible) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrices();
        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumer buffer = consumers.getBuffer(RenderLayers.debugQuads());

        for (ClientWeatherHandler.ZoneState s : zones.values()) {
            if (s.weather == WeatherZone.WeatherType.CLEAR || s.transitionProgress < 0.05f) continue;

            double zoneCX = (s.zoneX + 0.5) * ZONE_SIZE;
            double zoneCZ = (s.zoneZ + 0.5) * ZONE_SIZE;
            double zDistX = zoneCX - cam.x;
            double zDistZ = zoneCZ - cam.z;
            double zoneDist = Math.sqrt(zDistX * zDistX + zDistZ * zDistZ);
            if (zoneDist > MAX_DIST + ZONE_SIZE) continue;

            // Weather type settings
            int ri, gi, bi;
            float coverage, baseAlpha;
            switch (s.weather) {
                case THUNDER -> {
                    ri = 0x2A; gi = 0x2A; bi = 0x32;
                    coverage = COVERAGE_THUNDER;
                    baseAlpha = ALPHA_THUNDER;
                }
                case SNOW -> {
                    ri = 0xC2; gi = 0xC7; bi = 0xCF;
                    coverage = COVERAGE_SNOW;
                    baseAlpha = ALPHA_SNOW;
                }
                default -> {
                    ri = 0x6A; gi = 0x6F; bi = 0x78;
                    coverage = COVERAGE_RAIN;
                    baseAlpha = ALPHA_RAIN;
                }
            }

            // Side face shading (darker like vanilla MC cloud sides)
            int sideR = (int) (ri * 0.7f);
            int sideG = (int) (gi * 0.7f);
            int sideB = (int) (bi * 0.7f);
            // Bottom face even darker
            int botR = (int) (ri * 0.55f);
            int botG = (int) (gi * 0.55f);
            int botB = (int) (bi * 0.55f);

            float zoneWorldX = s.zoneX * ZONE_SIZE;
            float zoneWorldZ = s.zoneZ * ZONE_SIZE;

            for (int cx = 0; cx < CELLS_PER_ZONE; cx++) {
                for (int cz = 0; cz < CELLS_PER_ZONE; cz++) {
                    // World-space cell coords (with drift)
                    int worldCellX = s.zoneX * CELLS_PER_ZONE + cx;
                    int worldCellZ = s.zoneZ * CELLS_PER_ZONE + cz;

                    // Check if this cell should be filled
                    if (cellNoise(worldCellX, worldCellZ, 0) > coverage) continue;

                    // Cell world position (with wind-direction drift)
                    float cellWX = zoneWorldX + cx * CELL_SIZE + driftX;
                    float cellWZ = zoneWorldZ + cz * CELL_SIZE + driftZ;

                    // Distance fade per cell
                    double cdx = cellWX + CELL_SIZE * 0.5f - cam.x;
                    double cdz = cellWZ + CELL_SIZE * 0.5f - cam.z;
                    double cellDist = Math.sqrt(cdx * cdx + cdz * cdz);
                    if (cellDist > MAX_DIST) continue;

                    float distFade = cellDist < MAX_DIST * 0.6f ? 1f :
                            Math.max(0f, 1f - (float) ((cellDist - MAX_DIST * 0.6f) / (MAX_DIST * 0.4f)));

                    float alpha = baseAlpha * s.transitionProgress * distFade;
                    if (alpha < 0.01f) continue;
                    int ai = (int) (alpha * 255);
                    int sideAi = (int) (alpha * 0.85f * 255);
                    int botAi = (int) (alpha * 0.75f * 255);

                    // Camera-relative coords
                    float x1 = (float) (cellWX - cam.x);
                    float z1 = (float) (cellWZ - cam.z);
                    float x2 = x1 + CELL_SIZE;
                    float z2 = z1 + CELL_SIZE;
                    float yBot = (float) (CLOUD_BASE - cam.y);
                    float yTop = yBot + CLOUD_THICKNESS;

                    // Check neighbors for which side faces to draw
                    boolean drawNorth = cellNoise(worldCellX, worldCellZ - 1, 0) > coverage;
                    boolean drawSouth = cellNoise(worldCellX, worldCellZ + 1, 0) > coverage;
                    boolean drawWest  = cellNoise(worldCellX - 1, worldCellZ, 0) > coverage;
                    boolean drawEast  = cellNoise(worldCellX + 1, worldCellZ, 0) > coverage;

                    // --- Top face (brightest) ---
                    buffer.vertex(mat, x1, yTop, z1).color(ri, gi, bi, ai);
                    buffer.vertex(mat, x1, yTop, z2).color(ri, gi, bi, ai);
                    buffer.vertex(mat, x2, yTop, z2).color(ri, gi, bi, ai);
                    buffer.vertex(mat, x2, yTop, z1).color(ri, gi, bi, ai);

                    // --- Bottom face (darkest) ---
                    buffer.vertex(mat, x2, yBot, z1).color(botR, botG, botB, botAi);
                    buffer.vertex(mat, x2, yBot, z2).color(botR, botG, botB, botAi);
                    buffer.vertex(mat, x1, yBot, z2).color(botR, botG, botB, botAi);
                    buffer.vertex(mat, x1, yBot, z1).color(botR, botG, botB, botAi);

                    // --- North face (z1 side) ---
                    if (drawNorth) {
                        buffer.vertex(mat, x1, yBot, z1).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x1, yTop, z1).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x2, yTop, z1).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x2, yBot, z1).color(sideR, sideG, sideB, sideAi);
                    }

                    // --- South face (z2 side) ---
                    if (drawSouth) {
                        buffer.vertex(mat, x2, yBot, z2).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x2, yTop, z2).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x1, yTop, z2).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x1, yBot, z2).color(sideR, sideG, sideB, sideAi);
                    }

                    // --- West face (x1 side) ---
                    if (drawWest) {
                        buffer.vertex(mat, x1, yBot, z2).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x1, yTop, z2).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x1, yTop, z1).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x1, yBot, z1).color(sideR, sideG, sideB, sideAi);
                    }

                    // --- East face (x2 side) ---
                    if (drawEast) {
                        buffer.vertex(mat, x2, yBot, z1).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x2, yTop, z1).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x2, yTop, z2).color(sideR, sideG, sideB, sideAi);
                        buffer.vertex(mat, x2, yBot, z2).color(sideR, sideG, sideB, sideAi);
                    }
                }
            }
        }

        matrices.pop();
    }
}
