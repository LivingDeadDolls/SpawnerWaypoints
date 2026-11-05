package spawnerwaypoints.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import spawnerwaypoints.display.SpawnerFrames;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    /**
     * Right before the level renderer turns the frame's gizmos into geometry: add the spawners' outlines into the
     * renderer's own per-frame collection (opened here too, since not every frame is drawn from inside the game loop's,
     * a screenshot for one).
     */
    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void spawnerwaypoints$frameGizmos(CallbackInfo ci) {
        try (Gizmos.TemporaryCollection ignored = ((LevelRenderer) (Object) this).collectPerFrameRenderThreadGizmos()) {
            SpawnerFrames.frame();
        }
    }
}
