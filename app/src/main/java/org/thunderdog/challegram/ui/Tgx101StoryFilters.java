/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/plumagram-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.graphics.Bitmap;

/**
 * TGx101 (user 2026-10-12, mockup «Истории-фильтры» approved — all 18 filters and 8 adjustments): per-pixel filters for
 * story photos. The same maths as the mockup previews; a strength mixes the result with the original; the manual
 * adjustments go on top of any filter. Works on ARGB pixels of any size (a small preview or the full 1080×1920).
 */
public final class Tgx101StoryFilters {
  private Tgx101StoryFilters () { }

  public static final int ORIGINAL = 0, VIVID = 1, WARM = 2, COLD = 3, BW = 4, NOIR = 5, FILM = 6, CINEMA = 7,
    MATTE = 8, VHS = 9, PIXEL = 10, COMIC = 11, DUOTONE = 12, GLITCH = 13, LEAK = 14, SKETCH = 15, NEON = 16, DREAM = 17;
  public static final int COUNT = 18;

  // adjustments: brightness, contrast, saturation, warmth, shadows, highlights (−100…100), vignette, grain (0…100)
  public static final int ADJ_BRIGHTNESS = 0, ADJ_CONTRAST = 1, ADJ_SATURATION = 2, ADJ_WARMTH = 3, ADJ_SHADOWS = 4,
    ADJ_HIGHLIGHTS = 5, ADJ_VIGNETTE = 6, ADJ_GRAIN = 7;
  public static final int ADJ_COUNT = 8;

  public static boolean isIdentity (int filter, float strength, int[] adjust) {
    if (filter != ORIGINAL && strength > 0f) return false;
    for (int a : adjust) if (a != 0) return false;
    return true;
  }

  /** Filters the bitmap in place; {@code duo} — the two colours of the duotone (dark, light), taken from the photo */
  public static void apply (Bitmap bitmap, int filter, float strength, int[] adjust, int[] duo) {
    int w = bitmap.getWidth(), h = bitmap.getHeight();
    int[] px = new int[w * h];
    bitmap.getPixels(px, 0, w, 0, 0, w, h);
    apply(px, w, h, filter, strength, adjust, duo);
    bitmap.setPixels(px, 0, w, 0, 0, w, h);
  }

  public static void apply (int[] px, int w, int h, int filter, float strength, int[] adjust, int[] duo) {
    if (filter != ORIGINAL && strength > 0f) {
      int[] src = px.clone();
      int[] out = px;
      run(filter, src, out, w, h, duo);
      if (strength < 1f) mix(src, out, strength);
    }
    adjust(px, w, h, adjust);
  }

  private static int clamp (float v) {
    return v < 0 ? 0 : v > 255 ? 255 : (int) v;
  }

  private static int rgb (float r, float g, float b) {
    return 0xff000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  private static float rnd (long i) {
    double x = Math.sin(i * 127.1 + 311.7) * 43758.5453;
    return (float) (x - Math.floor(x));
  }

  private static float luma (int c) {
    return .3f * ((c >> 16) & 0xff) + .59f * ((c >> 8) & 0xff) + .11f * (c & 0xff);
  }

  private static void mix (int[] a, int[] b, float f) {
    for (int i = 0; i < a.length; i++) {
      int x = a[i], y = b[i];
      float r = ((x >> 16) & 0xff) + ((((y >> 16) & 0xff) - ((x >> 16) & 0xff)) * f);
      float g = ((x >> 8) & 0xff) + ((((y >> 8) & 0xff) - ((x >> 8) & 0xff)) * f);
      float bl = (x & 0xff) + (((y & 0xff) - (x & 0xff)) * f);
      b[i] = rgb(r, g, bl);
    }
  }

  private static void vignette (int[] px, int w, int h, float k) {
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        float dx = x / (float) w - .5f, dy = y / (float) h - .5f;
        float v = 1f - k * (float) ((dx * dx + dy * dy) / .49);
        int i = y * w + x, c = px[i];
        px[i] = rgb(((c >> 16) & 0xff) * v, ((c >> 8) & 0xff) * v, (c & 0xff) * v);
      }
    }
  }

  private static void run (int filter, int[] s, int[] d, int w, int h, int[] duo) {
    int n = w * h;
    switch (filter) {
      case VIVID:
        for (int i = 0; i < n; i++) {
          int c = s[i]; float r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff, l = .3f * r + .59f * g + .11f * b;
          r = l + (r - l) * 1.45f; g = l + (g - l) * 1.45f; b = l + (b - l) * 1.45f;
          d[i] = rgb((r - 128) * 1.12f + 128, (g - 128) * 1.12f + 128, (b - 128) * 1.12f + 128);
        }
        break;
      case WARM:
        for (int i = 0; i < n; i++) { int c = s[i]; d[i] = rgb(((c >> 16) & 0xff) * 1.12f + 10, ((c >> 8) & 0xff) * 1.03f + 4, (c & 0xff) * .85f); }
        break;
      case COLD:
        for (int i = 0; i < n; i++) { int c = s[i]; d[i] = rgb(((c >> 16) & 0xff) * .86f, ((c >> 8) & 0xff) + 4, (c & 0xff) * 1.15f + 12); }
        break;
      case BW:
        for (int i = 0; i < n; i++) { float l = luma(s[i]) * 1.02f + 6; d[i] = rgb(l, l, l); }
        break;
      case NOIR:
        for (int i = 0; i < n; i++) { float l = (luma(s[i]) - 128) * 1.6f + 118; d[i] = rgb(l, l, l); }
        break;
      case FILM:
        for (int i = 0; i < n; i++) {
          int c = s[i]; float g = (rnd(i) - .5f) * 26f;
          d[i] = rgb(((c >> 16) & 0xff) * .86f + 28 + g, ((c >> 8) & 0xff) * .86f + 22 + g, (c & 0xff) * .8f + 30 + g);
        }
        vignette(d, w, h, .35f);
        break;
      case CINEMA:
        for (int i = 0; i < n; i++) {
          int c = s[i]; float l = luma(c) / 255f, t = 1 - l;
          d[i] = rgb(((c >> 16) & 0xff) + 40 * l - 26 * t, ((c >> 8) & 0xff) + 8 * l + 6 * t, (c & 0xff) - 30 * l + 34 * t);
        }
        vignette(d, w, h, .35f);
        break;
      case MATTE:
        for (int i = 0; i < n; i++) {
          int c = s[i]; float l = luma(c);
          float r = l + (((c >> 16) & 0xff) - l) * .7f, g = l + (((c >> 8) & 0xff) - l) * .7f, b = l + ((c & 0xff) - l) * .7f;
          d[i] = rgb(r * .78f + 46, g * .78f + 46, b * .78f + 46);
        }
        vignette(d, w, h, .35f);
        break;
      case VHS: {
        int shift = Math.max(1, Math.round(w / 90f));
        for (int y = 0; y < h; y++) {
          int off = Math.round((float) Math.sin(y * .07 * 270f / w) * 1.5f * w / 270f + (rnd(y * 270L / w) > .97f ? shift * 2 : 0));
          boolean line = (y * 270 / w) % 3 == 0;
          for (int x = 0; x < w; x++) {
            int xr = Math.min(w - 1, Math.max(0, x + shift + off)), xb = Math.min(w - 1, Math.max(0, x - shift + off));
            float r = (s[y * w + xr] >> 16) & 0xff, g = (s[y * w + x] >> 8) & 0xff, b = s[y * w + xb] & 0xff;
            if (line) { r *= .82f; g *= .82f; b *= .82f; }
            d[y * w + x] = rgb(r * 1.05f + 8, g, b * .95f);
          }
        }
        break;
      }
      case PIXEL: {
        int cell = Math.max(4, Math.round(w / 34f));
        for (int y = 0; y < h; y += cell) {
          for (int x = 0; x < w; x += cell) {
            int c = s[Math.min(h - 1, y + cell / 2) * w + Math.min(w - 1, x + cell / 2)];
            for (int yy = y; yy < y + cell && yy < h; yy++) for (int xx = x; xx < x + cell && xx < w; xx++) d[yy * w + xx] = c;
          }
        }
        break;
      }
      case COMIC: {
        int cell = Math.max(4, Math.round(w / 45f));
        for (int y = 0; y < h; y++) {
          for (int x = 0; x < w; x++) {
            int cx = (x / cell) * cell + cell / 2, cy = (y / cell) * cell + cell / 2;
            int cc = s[Math.min(h - 1, cy) * w + Math.min(w - 1, cx)];
            float rad = (1f - luma(cc) / 255f) * cell * .62f;
            float dd = (float) Math.hypot(x - cx, y - cy);
            int c = s[y * w + x];
            if (dd < rad) d[y * w + x] = rgb(20, 20, 30);
            else d[y * w + x] = rgb(Math.round(((c >> 16) & 0xff) / 85f) * 85 + 30, Math.round(((c >> 8) & 0xff) / 85f) * 85 + 30, Math.round((c & 0xff) / 85f) * 85 + 30);
          }
        }
        break;
      }
      case DUOTONE: {
        int a = duo[0], b = duo[1];
        float ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        for (int i = 0; i < n; i++) { float l = luma(s[i]) / 255f; d[i] = rgb(ar + (br - ar) * l, ag + (bg - ag) * l, ab + (bb - ab) * l); }
        break;
      }
      case GLITCH: {
        int sep = Math.max(2, Math.round(w / 54f));
        int bands = 14;
        for (int y = 0; y < h; y++) {
          int band = y * bands / h;
          int sh = rnd(band) > .6f ? Math.round((rnd(band + 9) - .5f) * w * .12f) : 0;
          for (int x = 0; x < w; x++) {
            int xr = Math.min(w - 1, Math.max(0, x + sh + sep)), xg = Math.min(w - 1, Math.max(0, x + sh)), xb = Math.min(w - 1, Math.max(0, x + sh - sep));
            d[y * w + x] = rgb((s[y * w + xr] >> 16) & 0xff, (s[y * w + xg] >> 8) & 0xff, s[y * w + xb] & 0xff);
          }
        }
        break;
      }
      case LEAK:
        for (int y = 0; y < h; y++) {
          for (int x = 0; x < w; x++) {
            float fx = x / (float) w, fy = y / (float) h;
            float a = Math.max(0f, 1f - (float) Math.hypot(fx - .05f, fy - .15f) / .55f), b = Math.max(0f, 1f - (float) Math.hypot(fx - 1f, fy - .85f) / .5f);
            int c = s[y * w + x];
            d[y * w + x] = rgb(((c >> 16) & 0xff) + 180 * a + 120 * b, ((c >> 8) & 0xff) + 70 * a + 40 * b, (c & 0xff) + 20 * a + 80 * b);
          }
        }
        break;
      case SKETCH:
      case NEON: {
        float k = 270f / Math.max(1, w); // the edge strength was tuned on 270 px wide previews
        int step = Math.max(1, Math.round(w / 270f));
        for (int y = 0; y < h; y++) {
          for (int x = 0; x < w; x++) {
            int i = y * w + x;
            int xl = Math.max(0, x - step), xr = Math.min(w - 1, x + step), yu = Math.max(0, y - step), yd = Math.min(h - 1, y + step);
            float gx = luma(s[y * w + xr]) - luma(s[y * w + xl]), gy = luma(s[yd * w + x]) - luma(s[yu * w + x]);
            float e = (float) Math.hypot(gx, gy);
            int c = s[i];
            if (filter == SKETCH) {
              float v = 245f - Math.min(255f, e * 3f);
              d[i] = rgb(v, v, v - 8);
            } else {
              float en = Math.min(1f, e / 60f);
              d[i] = rgb(((c >> 16) & 0xff) * .18f + 255 * en * .95f, ((c >> 8) & 0xff) * .18f + 80 * en, (c & 0xff) * .25f + 255 * en);
            }
          }
        }
        break;
      }
      case DREAM: {
        int r = Math.max(2, Math.round(w / 60f));
        for (int y = 0; y < h; y++) {
          for (int x = 0; x < w; x++) {
            float sr = 0, sg = 0, sb = 0; int cnt = 0;
            for (int ky = -r; ky <= r; ky += r) for (int kx = -r; kx <= r; kx += r) {
              int c = s[Math.min(h - 1, Math.max(0, y + ky)) * w + Math.min(w - 1, Math.max(0, x + kx))];
              sr += (c >> 16) & 0xff; sg += (c >> 8) & 0xff; sb += c & 0xff; cnt++;
            }
            int c = s[y * w + x];
            d[y * w + x] = rgb(((c >> 16) & 0xff) * .55f + sr / cnt * .6f + 18, ((c >> 8) & 0xff) * .55f + sg / cnt * .55f + 10, (c & 0xff) * .55f + sb / cnt * .62f + 24);
          }
        }
        break;
      }
      default:
        System.arraycopy(s, 0, d, 0, n);
    }
  }

  private static void adjust (int[] px, int w, int h, int[] a) {
    boolean any = false;
    for (int i = 0; i < ADJ_VIGNETTE; i++) any |= a[i] != 0;
    if (any) {
      float bright = a[ADJ_BRIGHTNESS] * .6f, contrast = 1f + a[ADJ_CONTRAST] / 100f * .6f, sat = 1f + a[ADJ_SATURATION] / 100f;
      float warm = a[ADJ_WARMTH] * .3f, shadows = a[ADJ_SHADOWS] / 100f, highs = a[ADJ_HIGHLIGHTS] / 100f;
      for (int i = 0; i < px.length; i++) {
        int c = px[i];
        float r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        r += bright; g += bright; b += bright;
        r = (r - 128) * contrast + 128; g = (g - 128) * contrast + 128; b = (b - 128) * contrast + 128;
        float l = .3f * r + .59f * g + .11f * b;
        r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat;
        r += warm; b -= warm;
        float lum = Math.max(0f, Math.min(1f, l / 255f));
        float sh = shadows * 70f * (1f - lum) * (1f - lum), hi = highs * 70f * lum * lum;
        r += sh + hi; g += sh + hi; b += sh + hi;
        px[i] = rgb(r, g, b);
      }
    }
    if (a[ADJ_VIGNETTE] != 0) vignette(px, w, h, a[ADJ_VIGNETTE] / 100f * .8f);
    if (a[ADJ_GRAIN] != 0) {
      float k = a[ADJ_GRAIN] / 100f * 40f;
      for (int i = 0; i < px.length; i++) {
        int c = px[i]; float g = (rnd(i * 7L + 3) - .5f) * k;
        px[i] = rgb(((c >> 16) & 0xff) + g, ((c >> 8) & 0xff) + g, (c & 0xff) + g);
      }
    }
  }
}
