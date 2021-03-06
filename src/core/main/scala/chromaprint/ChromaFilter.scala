package chromaprint

import fs2.Pipe

object ChromaFilter {

  val coefficients: IndexedSeq[Double] =
    Vector(
      0.25,
      0.75,
      1.0,
      0.75,
      0.25
    )

  val defaultParams: IndexedSeq[Double] = coefficients

  val bufferLength = 8

  def pipe[F[_]]: Pipe[F,IndexedSeq[Double],IndexedSeq[Double]] =
    pipe(coefficients)

  def pipe[F[_]](coefficients: IndexedSeq[Double]): Pipe[F,IndexedSeq[Double],IndexedSeq[Double]] = {
    var buffer: IndexedSeq[IndexedSeq[Double]] = IndexedSeq.fill[IndexedSeq[Double]](bufferLength)(
      IndexedSeq.fill[Double](Chroma.numBands)(0)
    )
    var bufferSize: Int = 1
    var bufferOffset: Int = 0

    _.map(fs => {
      buffer = buffer.updated(bufferOffset, fs)
      bufferOffset = (bufferOffset + 1) % bufferLength
      if (bufferSize >= coefficients.length) {
        val offset: Int = (bufferOffset + bufferLength - coefficients.length) % bufferLength
        Some((0 until Chroma.numBands).foldLeft(Vector.fill[Double](Chroma.numBands)(0)) {
          (r, i) =>
            coefficients.indices.foldLeft(r) {
              (rr, j) =>
                rr.updated(
                  i,
                  rr(i) + buffer((offset + j) % bufferLength)(i) * coefficients(j)
                )
            }
        })
      } else {
        bufferSize += 1
        None
      }
    }).unNone
  }

}
