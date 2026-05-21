package synthchip.oscillator

import spinal.core._
import spinal.lib._
import scala.math._

/**
 * WaveformBundle: Container for parallel aliased waveform signals.
 */
case class WaveformBundle() extends Bundle {
  val saw      = SInt(18 bits)
  val square   = SInt(18 bits)
  val pwm      = SInt(18 bits)
  val triangle = SInt(18 bits)
  val sine     = SInt(18 bits)
  val noise    = SInt(18 bits)
}

/**
 * WaveformGeneratorBank: Implements aliased waveform generation logic.
 * Aligned with Specification Section 6.
 */
class WaveformGeneratorBank extends Component {
  val io = new Bundle {
    val sampleTick     = in Bool()
    val effectivePhase = in UInt(24 bits)
    val pulseWidth     = in UInt(24 bits)
    val waveforms      = out(WaveformBundle())
  }

  // Helper to pipeline signals to match Sine ROM latency (1 cycle)
  def pipeline[T <: Data](sig: T): T = RegNextWhen(sig, io.sampleTick)

  // --- 6.3 Saw Waveform ---
  // Direct mapping of top 18 bits centered to signed range.
  val rawSaw = (io.effectivePhase(23 downto 6).asSInt - 131072).resized
  io.waveforms.saw := pipeline(rawSaw)

  // --- 6.4 Square Waveform ---
  // Threshold comparison at 50% (0x800000).
  val rawSquare = (io.effectivePhase < 0x800000) ? S(131071, 18 bits) : S(-131072, 18 bits)
  io.waveforms.square := pipeline(rawSquare)

  // --- 6.5 PWM Waveform ---
  // Variable threshold comparison.
  val rawPwm = (io.effectivePhase < io.pulseWidth) ? S(131071, 18 bits) : S(-131072, 18 bits)
  io.waveforms.pwm := pipeline(rawPwm)

  // --- 6.6 Triangle Waveform ---
  // Reflected phase ramp logic.
  val triDir       = io.effectivePhase(23)
  val triMagnitude = io.effectivePhase(22 downto 5)
  val triRamp      = triDir ? ~triMagnitude | triMagnitude
  val rawTriangle  = (triRamp.asSInt - 131072).resized
  io.waveforms.triangle := pipeline(rawTriangle)

  // --- 6.7 Sine Waveform (Quarter-wave LUT) ---
  val sineLutContent = for (i <- 0 until 512) yield {
    val angle = (i.toDouble / 512.0) * (Pi / 2.0)
    val value = round(sin(angle) * 131071.0).toInt
    S(value, 18 bits)
  }
  val sineRom = Mem(SInt(18 bits), sineLutContent)

  val quadrant = io.effectivePhase(23 downto 22)
  val index    = io.effectivePhase(21 downto 13)
  
  // Invert index in Quadrant 2 (01) and Quadrant 4 (11)
  val lutIndex = quadrant(0) ? ~index | index
  val lutValue = sineRom.readSync(address = lutIndex, enable = io.sampleTick)
  
  // Negate value in Quadrant 3 (10) and Quadrant 4 (11)
  val invertValueReg = pipeline(quadrant(1))
  io.waveforms.sine := invertValueReg ? -lutValue | lutValue

  // --- 6.8 Noise Waveform (LFSR) ---
  // x^23 + x^18 + 1 primitive polynomial.
  val lfsr = Reg(UInt(23 bits)) init(1)
  when(io.sampleTick) {
    lfsr := (lfsr(21 downto 0) ## (lfsr(22) ^ lfsr(17)))
  }
  // Output extracted from top bits and centered.
  // LFSR register already provides 1-cycle latency relative to phase.
  val rawNoise = (lfsr(22 downto 5).asSInt - 131072).resized
  io.waveforms.noise := rawNoise
}
