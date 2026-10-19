package org.thunderdog.challegram.navigation;

import android.graphics.LinearGradient;
import android.graphics.Shader;

import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * TGx101 (approved mockup «Шапка как одно целое», 1; user 2026-10-19 «переход должен быть ещё плавнее… как и в списках
 * чатов»): the header veil's shared look — its density and the fade that continues it under the header's bottom edge,
 * the same in chats, chat lists and the storage.
 */
public final class Tgx101Veil {
  private Tgx101Veil () { }

  /** the veil at the header's bottom edge (and under rows that continue the header) */
  public static final float EDGE_ALPHA = .95f;
  /** how far below the bottom edge the fade runs */
  public static final float FADE_DP = 32f;

  public static int fadeHeight () {
    return Screen.dp(FADE_DP);
  }

  /** the fade from {@link #EDGE_ALPHA} at {@code top} to nothing, eased (smoothstep) so there is no visible line where it starts or ends */
  public static Shader fade (int color, float top) {
    final int steps = 7;
    int[] colors = new int[steps];
    float[] positions = new float[steps];
    for (int i = 0; i < steps; i++) {
      float x = i / (float) (steps - 1);
      float eased = 1f - x * x * (3f - 2f * x); // 1 → 0, flat at both ends
      colors[i] = ColorUtils.alphaColor(EDGE_ALPHA * eased, color);
      positions[i] = x;
    }
    return new LinearGradient(0, top, 0, top + fadeHeight(), colors, positions, Shader.TileMode.CLAMP);
  }
}
