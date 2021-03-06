package chromaprint

import scala.concurrent.ExecutionContext

import fs2.{Pipe, Stream}
import spire.math.UInt
import cats.effect._
import cats.implicits._

abstract class Fingerprinter {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  def apply(audioSource: AudioSource)(implicit fftImpl: FFT): IO[Fingerprint] =
    apply(Config.default, audioSource)

  def apply(config: Config, audioSource: AudioSource)(implicit fftImpl: FFT): IO[Fingerprint] =
    IO.shift *> streamFingerprint(config, audioSource).
      last.compile.toVector.map(_.flatten).map(_(0))

  def streamFingerprint(config: Config, audioSource: AudioSource)(implicit fftImpl: FFT): Stream[IO,Fingerprint] =
    Stream.bracket[IO,Float](
      audioSource.duration
    )(_ => IO.unit) flatMap { duration: Float =>
      streamRaw(config, audioSource) through pipeFingerprint(config.algorithm, duration)
    }

  def streamRaw(config: Config, audioSource: AudioSource)(implicit fftImpl: FFT): Stream[IO,UInt] =
    audioSource.audioStream(config.sampleRate) through pipeRaw(config)

  def pipeFingerprint(algorithm: Int, duration: Float): Pipe[IO,UInt,Fingerprint] = {
    val empty = Fingerprint(algorithm, duration, Vector.empty)
    data => Stream[IO,Fingerprint](empty) ++ data.mapAccumulate[Fingerprint,Fingerprint](empty) {
      case (fp, el) =>
        val nextFp = fp.append(el)
        (nextFp, nextFp)
    }.map(_._2)
  }

  def pipeRaw(config: Config)(implicit fftImpl: FFT): Pipe[IO,Short,UInt] =
    audio => (config.maxLength match {
      case length if length > 0 =>
        audio take length
      case _ =>
        audio
    }).prefetchN(config.maxLength max config.sampleRate).
      through(SilenceRemover.pipe(config.silenceRemover)).
      through(Framer.pipe(config.framerConfig)).
      through(HammingWindow.pipe(config.hammingWindow)).
      through(fftImpl.pipe(config.frameSize)).
      through(Chroma.pipe(config.chromaConfig)).
      through(ChromaFilter.pipe).
      through(ChromaNormalizer.pipe).
      through(IntegralImage.pipe).
      through(FingerprintCalculator.pipe(config.classifiers))

}

object Fingerprinter extends Fingerprinter {

  class Impl(fft: FFT) extends Fingerprinter {
    implicit val fftImpl: FFT = fft

    def apply(audioSource: AudioSource): IO[Fingerprint] =
      super.apply(audioSource)

    def apply(config: Config, audioSource: AudioSource): IO[Fingerprint] =
      super.apply(config, audioSource)
  }
}
