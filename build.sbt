ThisBuild / version      := "1.0.0"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "synthchip"

val spinalVersion = "1.14.0"

lazy val oscillator = (project in file("."))
  .settings(
    name := "Oscillator_spinalHDL",
    libraryDependencies ++= Seq(
      "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion,
      "com.github.spinalhdl" %% "spinalhdl-lib"  % spinalVersion,
      compilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion),
      "org.scalatest" %% "scalatest" % "3.2.18" % "test"
    ),
    Compile / run / mainClass := Some("synthchip.oscillator.SynthChipVerilog")
  )

/**
 * Required for SpinalSim to fork a new process for the Verilator/GHDL simulation.
 */
fork := true

/**
 * Standard Scala compiler options for SpinalHDL projects.
 */
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls"
)
