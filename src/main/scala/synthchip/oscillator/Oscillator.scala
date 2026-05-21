package synthchip.oscillator

import spinal.core._
import spinal.lib._

/**
 * Oscillator: Top-level orchestrator for the SynthChip oscillator core.
 * Connects the PhaseUnit, WaveformGeneratorBank, PolyBlepEngine, and DspOutputStage.
 * Aligned with Specification Sections 2 and 10.
 */
class Oscillator extends Component {
  val io = new Bundle {
    // Timing Interface (Section 10.3)
    val sample_tick = in Bool()

    // Control Interface (Section 10.4)
    val phase_increment  = in UInt(24 bits)
    val waveform_select  = in UInt(3 bits)
    val pulse_width      = in UInt(24 bits)
    val amplitude        = in UInt(16 bits)
    val phase_modulation = in SInt(24 bits)
    val enable           = in Bool()

    // Synchronization Interface (Section 10.6)
    val phase_reset       = in Bool()
    val phase_reset_value = in UInt(24 bits)
    val sync_input        = in Bool()
    val sync_output       = out Bool()

    // Audio Interface (Section 10.7)
    val audio_output = out SInt(16 bits)
    val audio_valid  = out Bool()
  }

  // --- Stage 1: Phase Unit ---
  // Responsible for phase accumulation and sync priority (Cycle 0).
  val phaseUnit = new PhaseUnit
  phaseUnit.io.cmd.sampleTick      := io.sample_tick
  phaseUnit.io.cmd.phaseIncrement  := io.phase_increment
  phaseUnit.io.cmd.phaseReset      := io.phase_reset
  phaseUnit.io.cmd.phaseResetValue := io.phase_reset_value
  phaseUnit.io.cmd.syncInput       := io.sync_input
  phaseUnit.io.cmd.phaseModulation := io.phase_modulation
  
  // Sync Output is a Stage 1 event (immediate)
  io.sync_output := phaseUnit.io.syncOutput

  // --- Stage 2: Waveform Generation ---
  // Produces parallel naive waveforms (Cycle 1).
  val genBank = new WaveformGeneratorBank
  genBank.io.sampleTick     := io.sample_tick
  genBank.io.effectivePhase := phaseUnit.io.effectivePhase
  genBank.io.pulseWidth     := io.pulse_width

  // --- Stages 2-4: PolyBLEP Engine ---
  // Detects discontinuities and calculates correction (Cycles 1-34).
  val blep = new PolyBlepEngine
  blep.io.sampleTick     := io.sample_tick
  blep.io.phase          := phaseUnit.io.phase
  blep.io.phaseIncrement := io.phase_increment
  blep.io.pulseWidth     := io.pulse_width
  blep.io.syncActive     := io.sync_input

  // --- Stage 5: Output Stage ---
  // Handles selection, correction application, and scaling (Cycle 35).
  val outputStage = new DspOutputStage
  outputStage.io.sampleTick     := io.sample_tick
  outputStage.io.waveformSelect := io.waveform_select
  outputStage.io.waveforms      := genBank.io.waveforms
  outputStage.io.correction     := blep.io.correction
  outputStage.io.amplitude      := io.amplitude
  outputStage.io.enable         := io.enable

  // Final assignments
  io.audio_output := outputStage.io.audioOutput
  io.audio_valid  := outputStage.io.audioValid
}

/**
 * Generator object for Verilog output.
 */
object OscillatorVerilog {
  def main(args: Array[String]): Unit = {
    SpinalVerilog(new Oscillator)
  }
}
