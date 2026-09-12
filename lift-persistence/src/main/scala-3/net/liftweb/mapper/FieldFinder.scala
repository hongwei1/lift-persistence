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

  private val runtimeClass: Class[?] = classTag[T].runtimeClass

  def typeFilter: Class[?] => Boolean = runtimeClass.isAssignableFrom

  // `onMagic` is unused here - this variant decides statically and never invokes
  // an accessor - but the parameter is kept so all four per-version copies of
  // FieldFinder present the same signature.
  def findMagicFields(onMagic: AnyRef, startingClass: Class[?]): List[Method] = {
    def findForClass(clz: Class[?]): List[Method] = clz match {
      case null => Nil
      case c =>
        // Scala 3 backs both `object x` and `lazy val x` with `x$lzy<n>`, typed
        // `java.lang.Object`.  (`$module`, the Scala 2 spelling, cannot occur in
        // a class this build compiles.)  So the Scala 2 pairing - match the name,
        // then require the backing field's type to equal the accessor's return
        // type - cannot be reproduced exactly: the type half never holds for a
        // lazy val.  Accept a backing field that is either already a T or the
        // erased `Object` a lazy val leaves behind, and nothing else.
        def deMod(in: String): String =
          in.indexOf("$lzy") match {
            case -1 => in
            case i  => in.substring(0, i)
          }

        val backingFieldNames: Set[String] =
          c.getDeclaredFields.iterator
            .filter(f => typeFilter(f.getType) || (f.getType eq classOf[AnyRef]))
            .map(f => deMod(f.getName))
            .toSet

        // Scala 2 invoked the accessor and required the returned instance to be
        // an inner class of this mapper or one of its supertypes, which is what
        // stopped one entity adopting another's field.  For an `object` field the
        // declared return type carries the same fact - `Owner$name$` - so the
        // check survives statically.
        val ownerNames: List[String] = {
          def supers(k: Class[?]): List[Class[?]] =
            if (k eq null) Nil
            else k :: k.getInterfaces.toList.flatMap(supers) ::: supers(k.getSuperclass)
          supers(c).map(_.getName)
        }

        def ownedHere(meth: Method): Boolean = {
          val returned = meth.getReturnType.getName
          ownerNames.exists(returned.startsWith)
        }

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
          // real name disappears from mappedFields entirely.  An `object` field
          // additionally has to belong to this mapper, or `def lent = Other.lent`
          // would be adopted here and MetaMapper's setName_! would rename the
          // other entity's live column.
          //
          // Two Scala 2 behaviours are not reproduced.  That variant invoked each
          // accessor inside a try/catch and dropped any that threw, so a field
          // whose initializer fails went missing and the entity still loaded;
          // deciding statically means such a field now survives here and throws
          // where MetaMapper invokes it, failing the whole entity instead of one
          // column.  And a `val` initialised from another entity's field still
          // passes: it has a backing field of its own and the declared type says
          // nothing about which instance it holds.  Both would need the very
          // invocation this variant exists to avoid.
          typeFilter(meth.getReturnType) &&
            (if (isMagicObject(meth)) ownedHere(meth)
             else backingFieldNames.contains(meth.getName))
        }

        c.getDeclaredMethods.toList
          .filter(_.getParameterTypes.length == 0)
          .filter(method => Modifier.isPublic(method.getModifiers))
          // Scala 3 emits covariant-return bridges (javap shows two
          // primaryKeyField() on a Contact); Method.equals compares return types,
          // so .distinct would not collapse a bridge and its target and the field
          // would be registered twice.
          .filter(method => !method.isBridge && !method.isSynthetic)
          .filter(validActualType) ::: findForClass(c.getSuperclass)
    }

    findForClass(startingClass).distinct
  }

  lazy val accessorMethods: List[Method] = findMagicFields(metaMapper, metaMapper.getClass.getSuperclass)
}
