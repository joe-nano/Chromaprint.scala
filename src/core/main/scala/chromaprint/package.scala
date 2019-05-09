package object chromaprint {

  implicit class Pipe[T](val v: T) extends AnyVal {
    def |>[U] (f: T => U): U = f(v)
  }
}
