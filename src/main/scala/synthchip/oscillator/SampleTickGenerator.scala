package synthchip.oscillator

import spinal.core._

/**
 * SampleTickGenerator: Generates a single-cycle pulse at 44.1 kHz 
 * derived from a 50 MHz master clock.
 */
class SampleTickGenerator(masterClkFreq: HertzNumber = 50 MHz, 
                          sampleRate: HertzNumber = 44.1 kHz) extends Component {
  val io = new Bundle {
    val tick = out Bool()
  }

  // To get exactly 44.1kHz from 50MHz, the division ratio is 1133.7868...
  // We use a 32-bit accumulator to handle the fractional part accurately.
  
  // Calculate the increment: (SampleRate / MasterClock) * 2^32
  val incrementValue = ((sampleRate.toBigDecimal / masterClkFreq.toBigDecimal) * BigDecimal(BigInt(1) << 32)).toBigInt
  val increment = U(incrementValue, 32 bits)

  val accumulator = Reg(UInt(32 bits)) init(0)
  val nextAccumulator = accumulator + increment
  
  accumulator := nextAccumulator

  // The tick is generated whenever the accumulator wraps around
  // This provides a mean frequency of exactly 44.1kHz with jitter of only 1 master clock cycle.
  io.tick := nextAccumulator < accumulator
}

/**
 * Example of how to connect it in a System Top
 */
class SynthSystemTop extends Component {
  val osc = new Oscillator
  val tickGen = new SampleTickGenerator()
  
  osc.io.sample_tick := tickGen.io.tick
  // ... connect other control signals
}
