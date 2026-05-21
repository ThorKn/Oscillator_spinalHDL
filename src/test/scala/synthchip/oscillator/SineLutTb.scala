package synthchip.oscillator

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import scala.math._

class SineLutTb extends AnyFunSuite {
  val simConfig = SimConfig.withWave.withVerilator

  test("WaveformGeneratorBank: Sine Waveform Accuracy") {
    simConfig.compile(new WaveformGeneratorBank).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      // Initialize inputs
      dut.io.sampleTick #= false
      dut.io.effectivePhase #= 0
      dut.io.pulseWidth #= 0 // Not used for sine, but needs a value

      dut.clockDomain.waitSampling() // Wait for initial reset/pipeline to settle

      // Allow for a small rounding error due to fixed-point conversion and LUT generation.
      // 18-bit signed range is -131072 to 131071. An error of 2 is acceptable.
      val maxError = 2 

      // Iterate through a full 24-bit phase cycle with a reasonable step size
      // Testing every 0x1000 (4096) phase value gives 4096 samples, covering all LUT indices and quadrant transitions.
      val phaseStep = 0x1000 
      val numIterations = (1 << 24) / phaseStep

      for (i <- 0 until numIterations) {
        val currentPhase = i * phaseStep
        dut.io.effectivePhase #= currentPhase
        dut.io.sampleTick #= true // Enable processing for this sample
        dut.clockDomain.waitSampling() // Advance one cycle for the sine ROM readSync and pipeline helper
        dut.io.sampleTick #= false // De-assert sampleTick

        // Calculate expected floating-point sine value
        // The phase is 0 to 2^24-1, mapping to 0 to 2*Pi radians
        val angle = (currentPhase.toDouble / (1 << 24)) * (2 * Pi)
        // Scale to 18-bit signed range (-131072 to 131071)
        val expectedSineFloat = sin(angle) * 131071.0 
        val expectedSineInt = round(expectedSineFloat).toInt

        // Get actual hardware output
        val actualSine = dut.io.waveforms.sine.toInt

        // Compare with tolerance
        val error = abs(actualSine - expectedSineInt)
        assert(error <= maxError,
          s"Phase: 0x${currentPhase.toHexString}, Angle: ${angle} rad, " +
          s"Expected: $expectedSineInt, Actual: $actualSine, Error: $error (Max allowed: $maxError)"
        )
      }
    }
  }
}
