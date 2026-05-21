package synthchip.oscillator

import spinal.core._

/**
 * Generator script for the SynthChip project.
 * This file centralizes the SpinalConfig and provides the main entry point for SBT.
 */
object Generator {
  def main(args: Array[String]): Unit = {
    SpinalConfig(
      targetDirectory = "rtl",
      defaultConfigForClockDomains = ClockDomainConfig(resetActiveLevel = HIGH)
    ).generateVerilog(new SynthChipTop)
  }
}
