package synthchip.oscillator

import spinal.core._
import spinal.lib._

/**
 * DspOutputStage: Final pipeline stage for mixing, scaling, and output formatting.
 * Aligned with Specification Sections 2.3, 4.2.3, and 10.7.
 */
class DspOutputStage extends Component {
  val io = new Bundle {
    val sampleTick     = in Bool()
    val waveformSelect = in UInt(3 bits)
    val waveforms      = in(WaveformBundle())
    val correction     = in SInt(18 bits)
    val amplitude      = in UInt(16 bits)
    val enable         = in Bool()
    
    val audioOutput    = out SInt(16 bits)
    val audioValid     = out Bool()
  }

  // Helper to pipeline signals to maintain Stage 5 alignment
  // Naive waveforms generated in Stage 2 must be delayed to match PolyBLEP calculation (finished in Stage 5)
  def delay[T <: Data](sig: T, cycles: Int): T = Delay(sig, cycles, io.sampleTick)

  // --- Stage 5: Final Mix and Output ---
  
  // Pipeline alignment (33 cycles from Stage 2 to Stage 5)
  val delayedWaves     = delay(io.waveforms, 33)
  val delayedSelect    = delay(io.waveformSelect, 33)
  val delayedAmplitude = delay(io.amplitude, 33)
  val delayedEnable    = delay(io.enable, 33)

  // 1. Select the raw waveform
  val rawWave = delayedSelect.mux(
    0 -> delayedWaves.saw,
    1 -> delayedWaves.square,
    2 -> delayedWaves.pwm,
    3 -> delayedWaves.triangle,
    4 -> delayedWaves.sine,
    5 -> delayedWaves.noise,
    default -> S(0, 18 bits)
  )

  // 2. Apply PolyBLEP correction (Saturating Addition - Section 4.2.3)
  // Only apply to Saw, Square, PWM (indices 0, 1, 2)
  val needsBlep = delayedSelect <= 2
  val correctedWave = needsBlep ? (rawWave + io.correction) | rawWave
  val saturatedWave = correctedWave.sat(17) // Ensure 18-bit signed range

  // 3. Amplitude Scaling (18-bit signed * 16-bit unsigned -> 34-bit signed)
  val scaledWaveLong = saturatedWave * delayedAmplitude.asSInt
  val scaledWave     = (scaledWaveLong >> 16).resize(18) // Explicitly set to 18-bit range

  // 4. Output Formatting (16-bit Signed Q1.15 - Section 4.3)
  // Convert 18-bit internal to 16-bit output with saturation.
  val finalOut = RegNextWhen(scaledWave.sat(15), io.sampleTick) init(0)
  
  // 5. Output Gating (Section 10.5)
  io.audioOutput := delayedEnable ? finalOut | S(0, 16 bits)
  
  // 6. Audio Valid Pulse (Section 10.8 - Total 35 cycles latency)
  io.audioValid := delay(io.sampleTick, 35)
}
