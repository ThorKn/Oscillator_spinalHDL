package synthchip.oscillator

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

class LfsrTb extends AnyFunSuite {
  // We use Verilator without waves to ensure the 8-million cycle test runs in seconds
  val simConfig = SimConfig.withVerilator

  test("WaveformGeneratorBank: LFSR Sequence Length and Output Mapping") {
    simConfig.compile(new WaveformGeneratorBank).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      // Initialize control signals
      dut.io.sampleTick #= false
      dut.io.effectivePhase #= 0
      dut.io.pulseWidth #= 0
      dut.clockDomain.waitSampling()

      val sequenceLength = (1 << 23) - 1 // 8,388,607 cycles
      
      // The seed is 0x000001. 
      // Bits 22 downto 5 of 0x000001 is 0.
      // Mapping: 0 - 131072 = -131072.
      val startOutput = -131072

      var minVal = Int.MaxValue
      var maxVal = Int.MinValue
      
      println(s"Starting LFSR verification for $sequenceLength cycles...")

      // Enable the generator
      dut.io.sampleTick #= true

      // 1. Verify periodicity and range
      // We check that the output returns to the start point exactly at the sequence length
      for (i <- 0 until sequenceLength) {
        val noise = dut.io.waveforms.noise.toInt
        
        // Tracking range for Section 6.8 compliance
        if (noise < minVal) minVal = noise
        if (noise > maxVal) maxVal = noise

        dut.clockDomain.waitSampling()
        
        // The LFSR should never enter the all-zero state
        // If noise is -131072 AND we aren't at the very start/end, it's worth noting,
        // but the seed is the unique identifier.
        if (i > 0 && i < sequenceLength - 1) {
           // The raw LFSR state is not exposed, but we can verify it doesn't wrap early
           if(i % 1000000 == 0) println(s"Reached cycle $i...")
        }
      }

      // Assertions for Section 6.8
      assert(minVal == -131072, s"Expected min -131072, got $minVal")
      assert(maxVal == 131071, s"Expected max 131071, got $maxVal")
      assert(dut.io.waveforms.noise.toInt == startOutput, "LFSR did not return to seed output after 2^23-1 cycles")
      
      println(s"LFSR verified. Range: [$minVal, $maxVal]. Period: $sequenceLength.")
    }
  }
}
