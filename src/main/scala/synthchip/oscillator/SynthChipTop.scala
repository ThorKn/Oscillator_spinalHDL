package synthchip.oscillator

import spinal.core._

/**
 * SynthChipTop: Connects the internal SampleTickGenerator to the Oscillator core.
 * This acts as the production top-level for Verilog generation.
 */
class SynthChipTop extends Component {
  val io = new Bundle {
    // Control Interface
    val phase_increment  = in UInt(24 bits)
    val waveform_select  = in UInt(3 bits)
    val pulse_width      = in UInt(24 bits)
    val amplitude        = in UInt(16 bits)
    val phase_modulation = in SInt(24 bits)
    val enable           = in Bool()

    // Synchronization Interface
    val phase_reset       = in Bool()
    val phase_reset_value = in UInt(24 bits)
    val sync_input        = in Bool()
    val sync_output       = out Bool()

    // Audio Interface
    val audio_output = out SInt(16 bits)
    val audio_valid  = out Bool()
  }

  val tickGen = new SampleTickGenerator()
  val osc = new Oscillator()

  // Timing connection: Internal pulse drives the core logic
  osc.io.sample_tick := tickGen.io.tick

  // Control mapping
  osc.io.phase_increment  := io.phase_increment
  osc.io.waveform_select  := io.waveform_select
  osc.io.pulse_width      := io.pulse_width
  osc.io.amplitude        := io.amplitude
  osc.io.phase_modulation := io.phase_modulation
  osc.io.enable           := io.enable

  osc.io.phase_reset       := io.phase_reset
  osc.io.phase_reset_value := io.phase_reset_value
  osc.io.sync_input        := io.sync_input

  // Output mapping
  io.sync_output  := osc.io.sync_output
  io.audio_output := osc.io.audio_output
  io.audio_valid  := osc.io.audio_valid
}
