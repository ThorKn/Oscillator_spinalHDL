package synthchip.oscillator

import spinal.core._

/**
 * PhaseUnit: Handles DDS phase accumulation, synchronization, and modulation.
 * Aligned with Specification Sections 4.1, 5, and 8.
 */
case class PhaseUnitSignals() extends Bundle {
  val phaseIncrement  = in UInt(24 bits)
  val phaseReset      = in Bool()
  val phaseResetValue = in UInt(24 bits)
  val syncInput       = in Bool()
  val phaseModulation = in SInt(24 bits)
  val sampleTick      = in Bool()
}

class PhaseUnit extends Component {
  val io = new Bundle {
    val cmd = PhaseUnitSignals()
    val phase          = out UInt(24 bits)
    val effectivePhase = out UInt(24 bits)
    val syncOutput     = out Bool()
  }

  // 24-bit Phase Accumulator (Section 4.1)
  val phaseReg = Reg(UInt(24 bits)) init(0)

  // Phase Update Logic (Section 8.7)
  // Priority: reset (implicit) > phaseReset > syncInput > increment
  when(io.cmd.sampleTick) {
    when(io.cmd.phaseReset || io.cmd.syncInput) {
      phaseReg := io.cmd.phaseResetValue
    } otherwise {
      phaseReg := phaseReg + io.cmd.phaseIncrement
    }
  }

  // Sync Output (Section 8.5)
  // Generated in Stage 1 (Cycle 0). Asserted when the natural increment causes a wrap.
  // We check if the addition would overflow.
  val carries = (phaseReg.resize(25) + io.cmd.phaseIncrement.resize(25))
  val willWrap = carries(24)

  io.syncOutput := io.cmd.sampleTick && !io.cmd.phaseReset && !io.cmd.syncInput && willWrap

  // Phase Modulation (Section 8.4)
  // Affects generation only, does not modify the accumulator.
  // Uses natural modular wrapping (24-bit unsigned).
  io.phase := phaseReg
  io.effectivePhase := (phaseReg.asSInt + io.cmd.phaseModulation).asUInt.resized
}