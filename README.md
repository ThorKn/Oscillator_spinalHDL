# SpinalHDL Synthesizer Oscillator Specification

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Timing Architecture](#3-timing-architecture)
4. [Numerical Formats](#4-numerical-formats)
5. [DDS Oscillator Architecture](#5-dds-oscillator-architecture)
6. [Waveform Generation](#6-waveform-generation)
7. [PolyBLEP Anti-Aliasing](#7-polyblep-anti-aliasing)
8. [Synchronization Architecture](#8-synchronization-architecture)
9. [Reset Architecture](#9-reset-architecture)
10. [Interface Architecture](#10-interface-architecture)
11. [DSP Pipeline Behavior](#11-dsp-pipeline-behavior)
12. [Signal Definitions](#12-signal-definitions)
13. [Behavioral Rules](#13-behavioral-rules)
14. [Future Expansion Considerations](#14-future-expansion-considerations)

---

# 1. Project Overview

## 1.1 Goal

The project implements a reusable synthesizer oscillator core in SpinalHDL for FPGA targets.

The oscillator is intended to:

* generate audio-rate waveforms,
* operate synchronously in FPGA hardware,
* provide synthesizer-oriented modulation and synchronization features,
* and serve as a reusable building block for future synthesizer projects.

---

## 1.2 Design Goals

The oscillator architecture shall prioritize:

* deterministic timing,
* FPGA efficiency,
* modularity,
* clean DSP structure,
* future scalability,
* and synthesizer-grade audio quality.

---

## 1.3 HDL Language

The implementation language shall be:

* SpinalHDL

---

# 2. System Architecture

## 2.1 High-Level Signal Flow

```text
24-bit Phase Increment
          │
          ▼
24-bit Phase Accumulator
          │
          ▼
Parallel Waveform Generators
          │
          ▼
Waveform Multiplexer
          │
          ▼
Discontinuity Detection
          │
          ▼
2-Sample PolyBLEP Correction
          │
          ▼
18-bit Internal DSP
          │
          ▼
Amplitude Scaling
          │
          ▼
16-bit Signed Q1.15 Output
```

---

## 2.2 Global Clocking Model

The oscillator shall use:

* a single synchronous clock domain,
* driven by a 50 MHz master clock.

All DSP stages shall:

* operate synchronously to the master clock,
* advance only on `sample_tick`.

---

## 2.3 SpinalHDL Component Hierarchy

The oscillator core is structured into the following SpinalHDL components and logical areas, designed to align with the DSP pipeline stages:

*   **`Oscillator` (Top-Level Component):** The main wrapper, managing the interface and orchestrating the pipeline flow.
*   **`PhaseUnit` (Logical Area):** Handles phase accumulation, modulation, and synchronization logic.
*   **`WaveformGeneratorBank` (Logical Area):** Generates all raw (aliased) waveforms in parallel.
*   **`PolyBlepEngine` (Sub-Component):** Encapsulates the iterative divider, discontinuity detection, and PolyBLEP polynomial calculation.
*   **`DspOutputStage` (Logical Area):** Performs final waveform selection, PolyBLEP correction application, amplitude scaling, and output formatting.

---

# 3. Timing Architecture

## 3.1 FPGA Master Clock

| Property               | Value  |
| ---------------------- | ------ |
| Master clock frequency | 50 MHz |

---

## 3.2 Audio Sample Rate

| Property          | Value    |
| ----------------- | -------- |
| Audio sample rate | 44.1 kHz |

---

## 3.3 Sample Timing Model

The oscillator shall use:

* externally supplied sample timing.

The oscillator core shall not internally generate:

* the audio sample tick.

---

## 3.4 Sample Tick Behavior

| Signal        | Description                        |
| ------------- | ---------------------------------- |
| `sample_tick` | 44.1 kHz single-cycle enable pulse |

All oscillator state updates shall occur only when:

* `sample_tick` is asserted.

---

# 4. Numerical Formats

## 4.1 Phase Domain

| Signal            | Width  | Format   |
| ----------------- | ------ | -------- |
| Phase accumulator | 24 bit | Unsigned |
| Phase increment   | 24 bit | Unsigned |
| Phase modulation  | 24 bit | Signed   |

---

## 4.2 Internal DSP Domain

| Property | Value              |
| -------- | ------------------ |
| Width    | 18 bit             |
| Format   | Signed fixed-point |

Internal DSP signals shall use:

* signed fixed-point arithmetic.

---

Internal DSP signals shall represent a full-scale range from -1.0 (represented as -131072) to +1.0 (represented as +131071).

---

## 4.3 Audio Output Format

| Property           | Value            |
| ------------------ | ---------------- |
| Width              | 16 bit           |
| Format             | Signed Q1.15     |
| Numerical encoding | Two’s complement |

Output range:

```text
-32768 ... +32767
```

Normalized output range:

```text
-1.0 <= output < +1.0
```

---

# 5. DDS Oscillator Architecture

## 5.1 DDS Principle

The oscillator shall use:

* Direct Digital Synthesis (DDS)
* with a phase accumulator.

---

## 5.2 Phase Update Equation

```text
phase <= phase + phase_increment
```

performed on each `sample_tick`.

---

## 5.3 Frequency Equation

```text
f_out = (phase_increment × 44100) / 2^24
```

---

## 5.4 Phase Convention

| Phase Value | Meaning     |
| ----------- | ----------- |
| `0x000000`  | 0°          |
| `0x800000`  | 180°        |
| `0xFFFFFF`  | Almost 360° |

---

# 6. Waveform Generation

## 6.1 Supported Waveforms

| Encoding | Waveform |
| -------- | -------- |
| `000`    | Saw      |
| `001`    | Square   |
| `010`    | PWM      |
| `011`    | Triangle |
| `100`    | Sine     |
| `101`    | Noise    |
| `110`    | Reserved |
| `111`    | Reserved |

---

## 6.2 Waveform Architecture

Waveforms shall be:

* generated in parallel,
* selected by a waveform multiplexer.

---

## 6.3 Saw Waveform

### Generation Method

Direct phase mapping.

### PolyBLEP

Required.

---

## 6.4 Square Waveform

### Generation Method

Threshold comparison using phase value.

### Default Threshold

```text
0x800000
```

### PolyBLEP

Required.

---

## 6.5 PWM Waveform

### Generation Method

Variable-threshold square wave.

```text
pwm = (phase < pulse_width)
```

### Pulse Width Limits

| Minimum | Maximum |
| ------- | ------- |
| 5%      | 95%     |

### PolyBLEP

Required.

---

## 6.6 Triangle Waveform

### Generation Method

Reflected phase ramp.

### PolyBLEP

Not required.

---

## 6.7 Sine Waveform

### Generation Method

Quarter-wave lookup table.

### LUT Configuration

| Property      | Value          |
| ------------- | -------------- |
| LUT type      | Quarter-wave   |
| LUT size      | 512 entries    |
| Interpolation | None initially |

---

## 6.8 Noise Waveform

### Generation Method

LFSR-based pseudo-random generator.

The 23-bit LFSR shall use the primitive polynomial $x^{23} + x^{18} + 1$.

*   **Taps (0-indexed):** Bit 22 and Bit 17.
*   **Feedback Logic:** `feedback = lfsr_reg(22) XOR lfsr_reg(17)`.
*   **Update Rule:** On `sample_tick`, the register shifts left by 1, and the `feedback` bit is inserted at bit 0.

### LFSR Width

| Property | Value  |
| -------- | ------ |
| Width    | 23 bit |

### Output Mapping (18-bit Signed)

1.  Extract the most significant 18 bits: `noise_unsigned = lfsr_reg(22 downto 5)`.
2.  Center the unsigned value around zero: `noise_out = SInt(noise_unsigned) - 131072`.

### Reset Seed

```text
0x000001
```

---

# 7. PolyBLEP Anti-Aliasing

## 7.1 Anti-Aliasing Method

The oscillator shall use:

* classic 2-sample PolyBLEP correction.

---

## 7.2 Arithmetic Model

PolyBLEP shall use:

* fixed-point arithmetic only.

Floating-point arithmetic shall not be used.

---

## 7.3 PolyBLEP Coverage

| Waveform/Event            | PolyBLEP Applied |
| ------------------------- | ---------------- |
| Saw                       | Yes              |
| Square                    | Yes              |
| PWM                       | Yes              |
| Hard sync discontinuities | Yes              |
| Triangle                  | No               |
| Sine                      | No               |
| Noise                     | No               |

---

## 7.4 Discontinuity Detection

### Saw

```text
phase_next < phase_current
```

### Square / PWM

Threshold crossing detection.

### Hard Sync

Sync-triggered phase reset.

---

## 7.5 PolyBLEP Width

```text
dt = phase_increment / 2^24
```

---

## 7.6 DSP Precision

| Property           | Value  |
| ------------------ | ------ |
| Internal precision | 18 bit |

---

## 7.7 Latency Policy

The PolyBLEP implementation shall use:

* fixed deterministic latency.

---

# 8. Synchronization Architecture

## 8.1 Supported Synchronization Features

| Feature                  | Included |
| ------------------------ | -------- |
| Phase reset              | Yes      |
| Configurable reset phase | Yes      |
| Hard sync input          | Yes      |
| Phase modulation         | Yes      |
| Sync output chaining     | Yes      |

---

## 8.2 Phase Reset

### Behavior

```text
phase = phase_reset_value
```

---

## 8.3 Hard Sync

### Behavior

A sync event shall:

* reset oscillator phase.

---

## 8.4 Phase Modulation

### Processing Model

```text
effective_phase =
    phase + phase_modulation
```

### Important Rule

Phase modulation shall:

* affect waveform generation only,
* not modify the stored phase accumulator.

---

## 8.5 Sync Output

### Behavior

`sync_output` shall assert:

* when the phase accumulator wraps around.

### Pulse Width

| Property | Value             |
| -------- | ----------------- |
| Duration | One `sample_tick` |

### Wraparound Detection

```text
if phase_next < phase_current:
    sync_output = 1
```

---

## 8.6 Synchronization Priority

| Priority | Event                  |
| -------- | ---------------------- |
| Highest  | `reset`                |
| Next     | `phase_reset`          |
| Next     | `sync_input`           |
| Lowest   | Normal phase increment |

---

## 8.7 Phase Update Logic

```text
if reset:
    initialize all state
else if phase_reset:
    phase = phase_reset_value
else if sync_input:
    phase = phase_reset_value
else:
    phase += phase_increment

effective_phase =
    phase + phase_modulation
```

---

# 9. Reset Architecture

## 9.1 Reset Model

The oscillator shall use:

* fully synchronous reset behavior.

---

## 9.2 Reset Clock Domain

| Property     | Value               |
| ------------ | ------------------- |
| Clock domain | 50 MHz master clock |

---

## 9.3 Reset Polarity

| Property | Value       |
| -------- | ----------- |
| Polarity | Active-high |

---

## 9.4 Reset Behavior

During reset:

* oscillator state progression shall halt,
* outputs shall remain deterministic.

---

## 9.5 Audio Output During Reset

```text
audio_output = 0
```

---

## 9.6 Sample Tick Behavior During Reset

```text
sample_tick = 0
```

---

## 9.7 Sync Output During Reset

```text
sync_output = 0
```

---

## 9.8 PolyBLEP State Reset

All internal PolyBLEP state:

* shall reset to zero.

---

## 9.9 Reset Recovery Behavior

After reset release:

1. Sample timing resumes
2. Phase progression resumes
3. Waveform generation resumes
4. PolyBLEP processing resumes
5. Audio output becomes valid after DSP latency

---

# 10. Interface Architecture

## 10.1 Interface Philosophy

The oscillator core shall:

* contain no embedded bus interface,
* contain no memory-mapped register logic,
* remain a pure DSP module.

---

## 10.2 System Interface

| Signal  | Direction | Description                   |
| ------- | --------- | ----------------------------- |
| `clk`   | Input     | 50 MHz master clock           |
| `reset` | Input     | Synchronous active-high reset |

---

## 10.3 Timing Interface

| Signal        | Direction | Description            |
| ------------- | --------- | ---------------------- |
| `sample_tick` | Input     | 44.1 kHz sample enable |

---

## 10.4 Control Interface

| Signal             | Width         | Description        |
| ------------------ | ------------- | ------------------ |
| `phase_increment`  | 24 bit        | DDS tuning word    |
| `waveform_select`  | 3 bit         | Waveform selection |
| `pulse_width`      | 24 bit        | PWM threshold      |
| `amplitude`        | 16 bit        | Output amplitude   |
| `phase_modulation` | Signed 24 bit | PM/FM modulation   |
| `enable`           | 1 bit         | Oscillator enable  |

---

## 10.5 Enable Behavior

When:

```text
enable = 0
```

then:

```text
audio_output = 0
```

while:

* internal phase progression continues.

---

## 10.6 Synchronization Interface

| Signal              | Width  | Description                 |
| ------------------- | ------ | --------------------------- |
| `phase_reset`       | 1 bit  | Deterministic phase restart |
| `phase_reset_value` | 24 bit | Reset phase value           |
| `sync_input`        | 1 bit  | Hard sync trigger           |
| `sync_output`       | 1 bit  | Phase wrap pulse            |

---

## 10.7 Audio Interface

| Signal         | Width         | Description        |
| -------------- | ------------- | ------------------ |
| `audio_output` | Signed 16 bit | Q1.15 audio output |
| `audio_valid`  | 1 bit         | Output valid pulse |

---

## 10.8 Audio Valid Timing

```text
audio_valid =
    sample_tick delayed by DSP latency
```

---

# 11. DSP Pipeline Behavior

## 11.1 DSP Processing Flow

```text
sample_tick
      │
      ▼
phase update
      │
      ▼
waveform generation
      │
      ▼
PolyBLEP correction
      │
      ▼
amplitude scaling
      │
      ▼
audio_valid + audio_output
```

---

## 11.2 Processing Policy

All DSP stages shall:

* advance synchronously,
* only on `sample_tick`.

---

## 11.3 Latency Policy

The oscillator shall use:

* fixed deterministic pipeline latency.

All waveforms shall:

* exhibit identical latency.

---

# 12. Signal Definitions

## 12.1 Control Signals

| Signal             | Width  | Format   |
| ------------------ | ------ | -------- |
| `phase_increment`  | 24 bit | Unsigned |
| `phase_modulation` | 24 bit | Signed   |
| `pulse_width`      | 24 bit | Unsigned |
| `amplitude`        | 16 bit | Unsigned |
| `waveform_select`  | 3 bit  | Unsigned |

---

## 12.2 Audio Signals

| Signal         | Width  | Format       |
| -------------- | ------ | ------------ |
| `audio_output` | 16 bit | Signed Q1.15 |
| `audio_valid`  | 1 bit  | Logic        |

---

# 13. Behavioral Rules

## 13.1 Phase Arithmetic

Phase arithmetic shall:

* use natural modular wrapping.

---

## 13.2 DSP Arithmetic

DSP arithmetic shall:

* use signed fixed-point operations.

---

## 13.3 Floating Point

Floating-point arithmetic shall not be used.

---

## 13.4 Clock Domains

The oscillator shall use:

* exactly one clock domain.

---

## 13.5 Control Sampling

All control signals shall:

* be sampled synchronously,
* only on `sample_tick`.

---

# 14. Future Expansion Considerations

The architecture intentionally reserves expansion capability for future additions including:

* wavetable oscillators,
* supersaw generation,
* additional BLEP variants,
* modulation matrices,
* filter integration,
* polyphonic synthesis,
* oscillator banks,
* external bus wrappers,
* oversampling,
* interpolation improvements,
* and advanced synchronization modes.
