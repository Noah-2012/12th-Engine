package net.twelfthengine.gui;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.window.Window;

/**
 * Plays a full-screen intro sequence at engine startup.
 *
 * <p>While the intro is active the engine loop should skip world updates, ticks, and normal
 * rendering – this class takes over the entire frame output.
 *
 * <p>Integration in the main loop (before any other per-frame logic):
 *
 * <pre>{@code
 * IntroScreen intro = new IntroScreen(window, renderer, textRenderer);
 *
 * while (!window.shouldClose()) {
 *     // ... compute deltaTime ...
 *
 *     if (intro.isPlaying()) {
 *         intro.update(deltaTime);
 *         intro.render();
 *         window.update();
 *         continue;          // skip ticks, world update, normal rendering
 *     }
 *
 *     // --- normal engine loop below ---
 * }
 * }</pre>
 *
 * <p>Each "card" (a full-screen text slide) follows this timing envelope:
 *
 * <ol>
 *   <li><b>Fade-in</b> – black → text over {@link #FADE_DURATION} seconds
 *   <li><b>Hold</b> – fully visible for {@link #HOLD_DURATION} seconds
 *   <li><b>Fade-out</b> – text → black over {@link #FADE_DURATION} seconds
 * </ol>
 *
 * <p>After all cards have played {@link #isPlaying()} returns {@code false} and the engine resumes
 * normally.
 */
public class IntroScreen {

  // ── Timing (seconds) ─────────────────────────────────────────────────────
  /** Duration of each fade-in and fade-out phase. */
  public static final float FADE_DURATION = 1.2f;

  /** Duration the card is held at full opacity. */
  public static final float HOLD_DURATION = 1.8f;

  /** Total time per card = fade-in + hold + fade-out. */
  private static final float CARD_DURATION = FADE_DURATION * 2f + HOLD_DURATION;

  /**
   * STBEasyFont renders characters on a 13 px baseline (before any scaling). Used to vertically
   * centre the text on screen.
   */
  private static final float STB_CHAR_HEIGHT = 13f;

  // ── Intro cards ───────────────────────────────────────────────────────────
  /**
   * Full-screen text cards shown in order during the intro. Edit or extend this array to customise
   * what is displayed.
   */
  private static final String[] CARDS = {
    "A 12th Engine Production", "12th Engine  v1.1.2", "Patch 2: Better Physics",
  };

  // ── Font scale ────────────────────────────────────────────────────────────
  private static final float TITLE_SCALE = 4.0f;

  // ── State ─────────────────────────────────────────────────────────────────
  private boolean playing = true;
  private int cardIndex = 0;

  /** Elapsed time within the current card, in seconds. */
  private float cardTime = 0f;

  // ── Dependencies ──────────────────────────────────────────────────────────
  private final Window window;
  private final Renderer2D renderer;
  private final TextRenderer textRenderer;

  // ── Constructor ───────────────────────────────────────────────────────────

  /**
   * Creates an {@code IntroScreen}.
   *
   * @param window the main window (provides width / height)
   * @param renderer the 2-D renderer (used for the black background rect)
   * @param textRenderer the text renderer (used for the card label)
   */
  public IntroScreen(Window window, Renderer2D renderer, TextRenderer textRenderer) {
    this.window = window;
    this.renderer = renderer;
    this.textRenderer = textRenderer;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Returns {@code true} while the intro is still running. When this becomes {@code false} the main
   * loop may continue normally.
   */
  public boolean isPlaying() {
    return playing;
  }

  /**
   * Advances the intro timer by one frame. Must be called <em>before</em> {@link #render()} every
   * frame.
   *
   * @param deltaTime seconds elapsed since the last frame
   */
  public void update(float deltaTime) {
    if (!playing) return;

    cardTime += deltaTime;

    if (cardTime >= CARD_DURATION) {
      cardTime -= CARD_DURATION;
      cardIndex++;

      if (cardIndex >= CARDS.length) {
        playing = false;
        cardIndex = 0;
        cardTime = 0f;
      }
    }
  }

  /**
   * Draws the current intro frame: a black full-screen background with the active card text centred
   * and faded appropriately. Must be called <em>after</em> {@link #update(float)} every frame.
   */
  public void render() {
    if (!playing) return;

    renderer.begin2D();

    int sw = window.getWidth();
    int sh = window.getHeight();

    // Black background covers the entire window
    renderer.setColor(0f, 0f, 0f, 1f);
    renderer.drawRect(0, 0, sw, sh);

    float alpha = computeAlpha(cardTime);
    if (alpha <= 0f) return;

    String text = CARDS[cardIndex];

    // Centre the text using STBEasyFont's known base glyph dimensions
    float textWidth = textRenderer.getTextWidth(text, TITLE_SCALE);
    float textHeight = STB_CHAR_HEIGHT * TITLE_SCALE;

    float tx = (sw - textWidth) / 2f;
    float ty = (sh - textHeight) / 2f;

    textRenderer.drawText2D(text, tx, ty, TITLE_SCALE, 1f, 1f, 1f, alpha);

    renderer.end2D();
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  /**
   * Computes the opacity (alpha) for a given position within a card's timeline.
   *
   * <ul>
   *   <li>0 … FADE_DURATION → linear ramp 0 → 1 (fade-in)
   *   <li>FADE_DURATION … +HOLD_DURATION → constant 1 (hold)
   *   <li>+HOLD_DURATION … +FADE_DURATION → linear ramp 1 → 0 (fade-out)
   * </ul>
   *
   * @param t elapsed time within the card, in seconds
   * @return alpha in [0, 1]
   */
  private static float computeAlpha(float t) {
    if (t < FADE_DURATION) {
      return t / FADE_DURATION;
    } else if (t < FADE_DURATION + HOLD_DURATION) {
      return 1f;
    } else {
      float elapsed = t - FADE_DURATION - HOLD_DURATION;
      return Math.max(0f, 1f - (elapsed / FADE_DURATION));
    }
  }
}
