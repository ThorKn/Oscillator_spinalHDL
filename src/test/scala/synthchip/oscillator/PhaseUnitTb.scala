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

      dut.clockDomain.waitSampling()

      // 1. Verify Normal Increment
      dut.io.cmd.sampleTick #= true
      dut.io.cmd.phaseIncrement #= 1000
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 1000, "Phase should increment by 1000")

      // 2. Verify sample_tick gating (state should not change when tick is low)
      dut.io.cmd.sampleTick #= false
      dut.io.cmd.phaseIncrement #= 5000
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 1000, "Phase should remain 1000 when sampleTick is low")

      // 3. Verify phase_reset priority over increment
      dut.io.cmd.sampleTick #= true
      dut.io.cmd.phaseReset #= true
      dut.io.cmd.phaseResetValue #= 0x123456
      dut.io.cmd.phaseIncrement #= 99
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 0x123456, "phaseReset should override increment")
      dut.io.cmd.phaseReset #= false

      // 4. Verify sync_input priority
      dut.io.cmd.syncInput #= true
      dut.io.cmd.phaseResetValue #= 0xABCDEF
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 0xABCDEF, "syncInput should override increment")
      dut.io.cmd.syncInput #= false

      // 5. Verify 24-bit wrap-around and sync_output generation
      // Force phase near the limit
      dut.io.cmd.phaseReset #= true
      dut.io.cmd.phaseResetValue #= 0xFFFF00
      dut.clockDomain.waitSampling()
      dut.io.cmd.phaseReset #= false

      // Add increment that causes wrap: 0xFFFF00 + 0x200 = 0x1000100 -> truncated to 0x000100
      dut.io.cmd.phaseIncrement #= 0x200
      
      // syncOutput is combinatorial in Stage 1 based on phaseReg and willWrap
      // It should be visible as soon as inputs are set and sampleTick is high
      assert(dut.io.syncOutput.toBoolean == true, "syncOutput should be high when wrap is detected")
      
      dut.clockDomain.waitSampling()
      assert(dut.io.phase.toLong == 0x100, "Phase should wrap naturally at 24 bits")
      assert(dut.io.syncOutput.toBoolean == false, "syncOutput should drop after wrap is processed")

      // 6. Verify Phase Modulation (does not affect accumulator)
      // Current phase is 0x100 (256)
      dut.io.cmd.phaseModulation #= 1000
      sleep(1) // Allow combinatorial logic to propagate
      assert(dut.io.effectivePhase.toLong == 1256, "effectivePhase should include positive modulation")
      assert(dut.io.phase.toLong == 256, "Accumulator should remain unchanged by modulation")

      // Negative modulation with modular wrap
      dut.io.cmd.phaseModulation #= -500
      sleep(1)
      // 256 - 500 = -244 -> in 24-bit unsigned: 16777216 - 244 = 16776972
      assert(dut.io.effectivePhase.toLong == 16776972, "effectivePhase should wrap naturally with negative modulation")
    }
  }
}