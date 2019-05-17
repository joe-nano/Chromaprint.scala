package chromaprint.fftw

import java.util.concurrent.locks.{Lock, ReentrantLock}

import chromaprint.{discard,FFT}
import org.bytedeco.javacpp.fftw3._
import org.bytedeco.javacpp.{DoublePointer, Loader, fftw3}

object FFTImpl {

  import FFT._

  discard(Loader.load(classOf[fftw3]))

  private val lock: Lock = new ReentrantLock()

  def computeFrames(input: Seq[Vector[Double]]): Seq[Vector[Complex]] =
    try {
      lock.lock()
      doComputeFrames(input.toVector)
    } finally {
      fftw_cleanup()
      lock.unlock()
    }

  private def doComputeFrames(input: Seq[Vector[Double]]): Seq[Vector[Complex]] = {

    val frameLength = input.headOption.map(_.length).getOrElse(0)

    val signal = new DoublePointer(frameLength * 2)
    val output = new DoublePointer(frameLength * 2)

    val plan = fftw_plan_r2r_1d(frameLength, signal, output, FFTW_FORWARD, FFTW_ESTIMATE.toInt)

    input.foldLeft(Vector.empty[Vector[Complex]]){ (fftFrames, frame) =>

      discard(signal.put(frame.toArray, 0, frameLength))

      fftw_execute(plan)

      val resultArray = new Array[Double](frameLength * 2)

      discard(output.get(resultArray))

      val result = resultArray.toVector

      fftFrames :+ (0 until frameLength).map { i =>
        Complex(
          result(i),
          result(frameLength - i)
        )
      }.toVector
    }
  }

}
