package net.liftweb.util

import scala.reflect.ClassTag

object ReflectionCompat {
  type TypeTag[A] = ClassTag[A]

  inline def typeTag[A](using tag: ClassTag[A]): ClassTag[A] = tag
}
