package synthchip.oscillator

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

class OscillatorTb extends AnyFunSuite {
  val simConfig = SimConfig.withWave.withVerilator

  test("Oscillator: End-to-End Latency Verification") {
    simConfig.compile(new Oscillator).doSim { dut =>
      // Initialize
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.sample_tick #= false
      dut.io.enable #= true
      dut.io.amplitude #= 0x7FFF // Max
      dut.io.waveform_select #= 0 // Saw
      dut.io.phase_increment #= 0x1000
      dut.io.phase_modulation #= 0
      dut.io.phase_reset #= false
      dut.io.sync_input #= false

      dut.clockDomain.waitSampling()

      // 1. Verify Sample Tick Gating
      // Without a sample_tick, audio_valid should never fire.
      for(_ <- 0 until 100) {
        dut.clockDomain.waitSampling()
        assert(!dut.io.audio_valid.toBoolean, "audio_valid fired without sample_tick")
      }

      // 2. Measure Latency
      // We trigger a sample_tick and count how many clock cycles until audio_valid asserts.
      var cycles = 0
      var foundValid = false
      
      // Pulse sample_tick for 1 cycle
      dut.io.sample_tick #= true
      dut.clockDomain.waitSampling()
      dut.io.sample_tick #= false

      // Count cycles until audio_valid
      while(cycles < 100 && !foundValid) {
        if(dut.io.audio_valid.toBoolean) {
          foundValid = true
        } else {
          cycles += 1
          dut.clockDomain.waitSampling()
        }
      }

      assert(foundValid, "audio_valid never asserted")
      assert(cycles == 35, s"Latency mismatch: expected 35 cycles, got $cycles")

      // 3. Verify Enable Gating
      // Wait for next valid window
      dut.io.sample_tick #= true
      dut.io.enable #= false
      dut.clockDomain.waitSampling()
      dut.io.sample_tick #= false

      // Wait 35 cycles
      for(_ <- 0 until 35) dut.clockDomain.waitSampling()
      
      assert(dut.io.audio_valid.toBoolean, "audio_valid should still fire when disabled")
      assert(dut.io.audio_output.toInt == 0, "audio_output should be 0 when enable is low")
    }
  }
}