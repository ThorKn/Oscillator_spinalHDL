package synthchip.oscillator

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

class PolyBlepEngineTb extends AnyFunSuite {
  val simConfig = SimConfig.withWave.withVerilator

  test("PolyBlepEngine: Divider and Sequencer Logic") {
    simConfig.compile(new PolyBlepEngine).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.sampleTick #= false
      dut.io.phase #= 0
      dut.io.phaseIncrement #= 0x2000 // Small increment
      dut.io.pulseWidth #= 0x800000
      dut.io.syncActive #= false

      dut.clockDomain.waitSampling()

      // 1. Trigger a Saw Wrap Discontinuity
      // Phase goes from near max to near zero
      dut.io.sampleTick #= true
      dut.io.phase #= 0x000100 
      // prevPhase was 0, but isSawWrap logic is (phase < prevPhase). 
      // Let's set prevPhase high first.
      dut.clockDomain.waitSampling() // Set prevPhase to 0x100
      
      dut.io.phase #= 0x000050 // Trigger wrap (0x50 < 0x100)
      dut.clockDomain.waitSampling()
      dut.io.sampleTick #= false

      // 2. Verify Divider Activity
      // The divider should now be busy for 31 cycles.
      // We can't access divider.busy directly if it's private, but we can observe 
      // that no correction happens yet.
      for(_ <- 0 until 10) {
        assert(dut.io.correction.toInt == 0, "Correction output early")
        dut.clockDomain.waitSampling()
      }

      // 3. Verify Sequence Completion
      // Wait enough cycles for the 31-cycle division to finish.
      for(_ <- 0 until 30) dut.clockDomain.waitSampling()

      // Trigger sample_ticks to pump the sequence out
      var foundCorrection = false
      for(_ <- 0 until 5) {
        dut.io.sampleTick #= true
        dut.clockDomain.waitSampling()
        dut.io.sampleTick #= false
        
        if(dut.io.correction.toInt != 0) {
          foundCorrection = true
        }
      }
      
      assert(foundCorrection, "PolyBLEP correction never appeared after division")
    }
  }
}