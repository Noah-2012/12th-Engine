# 🎬 Post-Processing Pipeline Setup (Renderer3D)

This guide shows how to configure the full cinematic post-processing stack inside `onSetupRenderer()`.

The pipeline renders the 3D scene into an off-screen framebuffer and then applies a chain of effects.
Each effect reads the output of the previous one — **order matters**.

---

## ✨ Final Visual Stack

**Pipeline order**

```
Scene → SSAO → Depth of Field → Bloom → Color Grading → Chromatic Aberration → Vignette → Screen
```

This order produces a natural, film-like result.

---

# 🧠 Setup Code

```java
postProcess = new PostProcessPipeline(config.width(), config.height());
renderer3D.setActiveFbo(postProcess.getFboAId());
```

---

# 🎯 Effect Order & Configuration

---

## 1️⃣ SSAO — Ambient Occlusion (Foundation Lighting)

**Purpose**
Adds soft shadowing in crevices and contact areas.
This must run **first** so later effects operate on correct lighting.

```java
ssaoEffect = new SSAOEffect()
        .radius(0.4f)
        .intensity(1.0f)
        .bias(0.02f)
        .samples(16)
        .blurRadius(2)
        .near(0.1f)
        .far(1000f);

postProcess.addEffect(ssaoEffect);
```

### Key tuning tips

| Parameter | Meaning                | Suggested Range |
        | --------- | ---------------------- | --------------- |
        | radius    | Occlusion distance     | 0.3 – 0.6       |
        | intensity | Darkness strength      | 0.8 – 1.3       |
        | samples   | Quality vs performance | 8 – 32          |

---

## 2️⃣ Depth of Field — Camera Focus

**Purpose**
Simulates camera lens focus. Objects outside the focus range become blurred.

```java
dofEffect = new DepthOfFieldEffect()
        .focalDistance(15f)
        .focalRange(8f)
        .maxBlur(6f)
        .samples(12)
        .near(0.1f)
        .far(1000f);

postProcess.addEffect(dofEffect);
```

### Mental model

```
Camera ----[ sharp zone ]----
        15 ± 8 units
```

---

## 3️⃣ Bloom — Light Glow

**Purpose**
Makes bright objects glow (lights, explosions, emissive materials).

```java
bloomEffect = new BloomEffect()
        .threshold(0.75f)
        .intensity(1.0f)
        .blurRadius(4);

postProcess.addEffect(bloomEffect);
```

### Common styles

| Style          | Threshold | Intensity |
        | -------------- | --------- | --------- |
        | Subtle realism | 0.8       | 0.6       |
        | Cinematic      | 0.75      | 1.0       |
        | Dreamy         | 0.6       | 1.4       |

---

## 4️⃣ Color Grading — Final Look & Mood

**Purpose**
Applies cinematic color correction to the entire frame.

### Option A — Cinematic Preset

```java
colorGradingEffect = new ColorGradingEffect()
        .presetCinematic();

postProcess.addEffect(colorGradingEffect);
```

### Option B — Custom Grading

```java
colorGradingEffect = new ColorGradingEffect()
        .brightness(0f)
        .contrast(1.05f)
        .saturation(0.9f)
        .gamma(1.0f)
        .shadows(0.9f, 0.93f, 1.02f)
        .midtones(1.0f, 0.98f, 0.94f)
        .highlights(1.02f, 1.0f, 0.96f);

postProcess.addEffect(colorGradingEffect);
```

---
## 5️⃣ Chromatic Aberration — Lens Imperfection

**Purpose**
Adds subtle RGB separation near screen edges for realism.

```java
chromaticEffect = new ChromaticAberrationEffect()
        .strength(0.004f)
        .falloff(1.0f);

postProcess.addEffect(chromaticEffect);
```

| Strength | Result               |
| -------- | -------------------- |
| 0.002    | Barely visible       |
| 0.004    | Cinematic sweet spot |
| 0.01+    | Stylized / glitch    |

---

## 6️⃣ Vignette — Final Framing

**Purpose**
Darkens edges to focus viewer attention.
        **Always last** — it frames the final composed image.

```java
vignetteEffect = new VignetteEffect()
        .radius(0.70f)
        .softness(0.40f)
        .strength(0.75f);

postProcess.addEffect(vignetteEffect);
```

---

# 🗂️ Expected Shader Layout

```
/shaders/postprocess/
│
├── ssao.frag
├── dof.frag
├── bloom.frag
├── colorgrading.frag
├── chromatic.frag
├── vignette.frag
└── fullscreen.vert
```

---

# 🎥 Result

You now have a **complete cinematic rendering pipeline** featuring:

* Physically grounded lighting (SSAO)
* Real camera focus (DoF)
* Filmic glow (Bloom)
* Cinematic color grading
* Lens realism (Chromatic Aberration)
* Final framing (Vignette)
