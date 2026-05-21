package synthchip.oscillator

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

class PhaseUnitTb extends AnyFunSuite {
  // Compilation configuration for the simulator
  val simConfig = SimConfig.withWave.withVerilator

  test("PhaseUnit: Priority, Wrap-around and Modulation") {
    simConfig.compile(new PhaseUnit).doSim { dut =>
      // Initialize signals
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.cmd.sampleTick #= false
      dut.io.cmd.phaseIncrement #= 0
      dut.io.cmd.phaseReset #= false
      dut.io.cmd.phaseResetValue #= 0
      dut.io.cmd.syncInput #= false
      dut.io.cmd.phaseModulation #= 0

      // Wait for reset to settle
      dut.clockDomain.waitSampling(2)

      // 1. Verify Normal Increment (Section 5.2)
      dut.io.cmd.sampleTick #= true
      dut.io.cmd.phaseIncrement #= 1000
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 1000, "Phase should increment by 1000")

      // 2. Verify sample_tick Gating (Section 3.5)
      dut.io.cmd.sampleTick #= false
      dut.io.cmd.phaseIncrement #= 5000
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 1000, "Phase should remain 1000 when sampleTick is low")

      // 3. Verify phase_reset Priority (Section 8.6)
      dut.io.cmd.sampleTick #= true
      dut.io.cmd.phaseReset #= true
      dut.io.cmd.phaseResetValue #= 0x123456
      dut.io.cmd.phaseIncrement #= 99
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 0x123456, "phaseReset should override normal increment")
      dut.io.cmd.phaseReset #= false

      // 4. Verify sync_input Priority (Section 8.6)
      dut.io.cmd.syncInput #= true
      dut.io.cmd.phaseResetValue #= 0x800000
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 0x800000, "syncInput should override normal increment")
      dut.io.cmd.syncInput #= false

      // 5. Verify 24-bit wrap-around and gated sync_output (Section 8.5)
      // Force phase near the upper limit
      dut.io.cmd.phaseReset #= true
      dut.io.cmd.phaseResetValue #= 0xFFFF00
      dut.clockDomain.waitSampling()
      dut.io.cmd.phaseReset #= false

      // Increment that causes wrap: 0xFFFF00 + 0x200 = 0x1000100 -> truncated to 0x000100
      dut.io.cmd.phaseIncrement #= 0x200
      
      // A: Verify syncOutput is gated by sampleTick
      dut.io.cmd.sampleTick #= false
      sleep(1)
      assert(!dut.io.syncOutput.toBoolean, "syncOutput should be low if sampleTick is low")

      // B: Verify syncOutput is combinatorial (visible same cycle)
      dut.io.cmd.sampleTick #= true
      sleep(1)
      assert(dut.io.syncOutput.toBoolean, "syncOutput should assert immediately (Stage 1) when wrap is imminent")

      // C: Verify syncOutput is gated by reset/sync priority
      dut.io.cmd.syncInput #= true
      sleep(1)
      assert(!dut.io.syncOutput.toBoolean, "syncOutput should be suppressed if a sync/reset occurs")
      dut.io.cmd.syncInput #= false

      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 0x100, "Accumulator should have wrapped to 0x100")

      // 6. Verify Phase Modulation (Section 8.4)
      // Current phase is 0x100 (256)
      dut.io.cmd.phaseModulation #= 1000
      sleep(1)
      assert(dut.io.effectivePhase.toLong == 1256, "effectivePhase failed positive offset")
      assert(dut.io.phase.toLong == 256, "Modulation should not modify phase register")

      // Verify Negative modulation with modular wrap (Section 5.5)
      // 256 - 500 = -244. In 24-bit (16777216 - 244) = 16776972
      dut.io.cmd.phaseModulation #= -500 
      sleep(1)
      assert(dut.io.effectivePhase.toLong == 16776972, "effectivePhase failed modular negative wrap")

      // Verify Max modulation wrap
      dut.io.cmd.phaseModulation #= 0x7FFFFF // Largest positive SInt24
      sleep(1)
      // 256 + 8388607 = 8388863
      assert(dut.io.effectivePhase.toLong == 8388863, "effectivePhase failed positive wrap test")
    }
  }
}
