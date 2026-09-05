/*
 * Copyright 2006-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.liftweb
package mapper

import scala.reflect.{ClassTag, classTag}

class FieldFinder[T: ClassTag](metaMapper: AnyRef, logger: common.Logger) {
  import java.lang.reflect.*

  logger.debug("Created FieldFinder for " + classTag[T].runtimeClass)

  def isMagicObject(m: Method): Boolean = m.getReturnType.getName.endsWith("$" + m.getName + "$") && m.getParameterTypes.length == 0

  def typeFilter: Class[?] => Boolean = classTag[T].runtimeClass.isAssignableFrom

  def findMagicFields(onMagic: AnyRef, startingClass: Class[?]): List[Method] = {
    def findForClass(clz: Class[?]): List[Method] = clz match {
      case null => Nil
      case c =>
        // Scala 2 backs `object x` with a field `x$module` typed as the
        // object's own class.  Scala 3 backs both `object x` and `lazy val x`
        // with `x$lzy<n>` typed `java.lang.Object`, so the Scala 2 pairing -
        // match the name, then require the field's type to equal the
        // accessor's return type - cannot be reproduced: neither half holds.
        def deMod(in: String): String =
          if (in.endsWith("$module")) in.substring(0, in.length - 7)
          else in.indexOf("$lzy") match {
            case -1 => in
            case i  => in.substring(0, i)
          }

        val backingFieldNames: Set[String] =
          c.getDeclaredFields.iterator.map(f => deMod(f.getName)).toSet

        def validActualType(meth: Method): Boolean = {
          // Unlike the Scala 2 variant this one must decide without invoking
          // the accessor: Scala 3 compiles mapper `object` fields to lazy
          // accessors, and calling one while MetaMapper's superclass is still
          // initialising observes an uninitialised value and discards every
          // field.
          //
          // Two shapes are legitimate mapped fields, and both are recognisable
          // statically.  An `object` field's accessor returns the synthetic
          // class of that object - `Decoy$realField$` for `object realField` -
          // which is what isMagicObject tests.  A `val`/`lazy val` field (how
          // ProtoUser declares its fields) returns the plain field type, but
          // has a backing field of the same name.
          //
          // Requiring one of those two is what keeps a plain
          // `def alias: MappedString[T] = realField` out.  Such a def has no
          // backing field and returns the generic type; admitting it does not
          // merely add a spurious entry - it shares one instance with the real
          // field, so the field is reported twice under the def's name and the
          // real name disappears from mappedFields entirely.
          typeFilter(meth.getReturnType) &&
            (isMagicObject(meth) || backingFieldNames.contains(meth.getName))
        }

        c.getDeclaredMethods.toList
          .filter(_.getParameterTypes.length == 0)
          .filter(method => Modifier.isPublic(method.getModifiers))
          .filter(validActualType) ::: findForClass(c.getSuperclass)
    }

    findForClass(startingClass).distinct
  }

  lazy val accessorMethods: List[Method] = findMagicFields(metaMapper, metaMapper.getClass.getSuperclass)
}
