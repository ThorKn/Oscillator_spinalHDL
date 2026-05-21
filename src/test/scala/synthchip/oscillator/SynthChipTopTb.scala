package synthchip.oscillator

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

class SynthChipTopTb extends AnyFunSuite {
  // Setup configuration to include waves and use the Verilator backend
  val simConfig = SimConfig.withWave.withVerilator

  test("SynthChipTop: Run 1,000,000 cycles with fixed inputs") {
    simConfig.compile(new SynthChipTop).doSim { dut =>
      // Initialize clock and reset
      dut.clockDomain.forkStimulus(period = 10)
      
      // Apply fixed inputs as requested
      dut.io.phase_increment #= 1000
      dut.io.waveform_select #= 0
      dut.io.pulse_width #= 0
      dut.io.amplitude #= 0xFFFF
      dut.io.phase_modulation #= 0
      dut.io.enable #= true
      dut.io.phase_reset #= false
      dut.io.phase_reset_value #= 0
      dut.io.sync_input #= false

      // Wait for 1,000,000 clock cycles
      // This will generate the VCD output for the entire duration
      dut.clockDomain.waitSampling(10000000)
    }
  }
}
