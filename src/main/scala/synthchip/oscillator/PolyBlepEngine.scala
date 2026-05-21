package synthchip.oscillator

import spinal.core._
import spinal.lib._

/**
 * PolyBlepEngine: Encapsulates discontinuity detection, iterative division,
 * and polynomial evaluation for anti-aliasing.
 * Aligned with Specification Sections 2.3, 2.4.2, and 7.
 */
class PolyBlepEngine extends Component {
  val io = new Bundle {
    val sampleTick     = in Bool()
    val phase          = in UInt(24 bits)
    val phaseIncrement = in UInt(24 bits)
    val pulseWidth     = in UInt(24 bits)
    val syncActive     = in Bool()
    
    // Correction output (Targeted for Stage 5 Final Mix)
    val correction = out SInt(18 bits)
  }

  // --- Stage 2: Discontinuity Detection (Cycle 1) ---
  val prevPhase = RegNextWhen(io.phase, io.sampleTick)
  
  val isSawWrap    = (io.phase < prevPhase)
  val isSquareEdge = (prevPhase(23) ^ io.phase(23))
  val isPwmEdge    = (prevPhase < io.pulseWidth) ^ (io.phase < io.pulseWidth)
  val isSync       = io.syncActive

  val trig = io.sampleTick && (isSawWrap || isSquareEdge || isPwmEdge || isSync)
  
  // Step Direction (Normalized to 18-bit signed range)
  // Negative step: High -> Low. Positive step: Low -> High.
  val stepSign = RegNextWhen(False, io.sampleTick)
  when(isSawWrap || isSync) {
    stepSign := False // Negative jump
  } elsewhen(isSquareEdge) {
    stepSign := !io.phase(23) // Transition into [0, 0.5) which is High (Positive Step)
  } elsewhen(isPwmEdge) {
    stepSign := io.phase < io.pulseWidth // Transition into [0, PW) which is High (Positive Step)
  }

  // Distance past boundary (fractional remainder)
  val dist = UInt(24 bits)
  dist := 0
  when(isSawWrap || isSync) { dist := io.phase }
  elsewhen(isSquareEdge)    { dist := (io.phase ^ 0x800000).resized }
  elsewhen(isPwmEdge)       { dist := (io.phase - io.pulseWidth).resized }

  // --- Stage 3: Iterative Division (Cycles 2-32) ---
  // Calculate t = dist / increment. 
  // t is represented as Q0.18 unsigned.
  val divider = new Area {
    val num   = Reg(UInt(55 bits)) // dist << 31 (Align with Stage 3 timing window)
    val den   = Reg(UInt(24 bits))
    val quot  = Reg(UInt(31 bits))
    val count = Reg(UInt(5 bits))
    val busy  = Reg(Bool()) init(False)
    val done  = Reg(Bool()) init(False)
    
    done := False
    when(trig) {
      num   := (dist.resized << 31)
      den   := io.phaseIncrement
      quot  := 0
      count := 31
      busy  := True
    } elsewhen(busy) {
      val canSub = num(54 downto 31) >= den
      val sub    = num(54 downto 31) - den
      when(canSub) {
        num  := (sub.resized @@ num(30 downto 0)) << 1
        quot := (quot << 1) | 1
      } otherwise {
        num  := num << 1
        quot := quot << 1
      }
      count := count - 1
      when(count === 0) {
        busy := False
        done := True
      }
    }
  }

  // --- Stage 4 & 5: Polynomial Evaluation & Sequencing ---
  // f(t) = t^2/2 - t + 1/2
  // Two-sample correction: 
  //   Sample n-1: -H * (t^2 / 2)
  //   Sample n:    H * ((1-t)^2 / 2)
  // With H = 2.0 (full jump), correction is -/+ t^2 and +/- (1-t)^2.
  val poly = new Area {
    // Extract top 18 bits for 18-bit DSP correction logic
    val t = RegNextWhen(divider.quot(30 downto 13).asSInt, divider.done)
    val sign = RegNextWhen(stepSign, divider.done)
    val active = RegInit(False)
    val sequence = Reg(UInt(1 bit))
    
    when(divider.done) { active := True; sequence := 0 }
    
    val oneMinusT = S(1 << 18, 19 bits) - t.resized
    val tSq       = (t * t) >> 18           // Q0.18
    val omTSq     = (oneMinusT * oneMinusT) >> 18 // Q0.18
    
    val result = SInt(18 bits)
    result := 0
    when(active && io.sampleTick) {
      result   := (sequence === 0) ? (sign ? -tSq.resized | tSq.resized) : (sign ? omTSq.resized | -omTSq.resized)
      sequence := sequence + 1
      when(sequence === 1) { active := False }
    }
  }

  io.correction := poly.result
}