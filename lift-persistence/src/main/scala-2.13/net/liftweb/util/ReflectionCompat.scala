package net.liftweb.util

object ReflectionCompat {
  type TypeTag[A] = scala.reflect.runtime.universe.TypeTag[A]

  def typeTag[A](implicit tag: TypeTag[A]): TypeTag[A] = tag
}
