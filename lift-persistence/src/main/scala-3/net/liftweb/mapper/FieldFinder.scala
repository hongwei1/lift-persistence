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
        def validActualType(meth: Method): Boolean = {
          // Scala 3 emits mapper `object` fields as lazy accessors.  Invoking
          // those accessors while MetaMapper's superclass is being
          // initialised can observe an uninitialised value and discard every
          // field.  The concrete return type carries the same information and
          // does not require running user initialization code.
          val returnType = meth.getReturnType
          meth.getName != "primaryKeyField" && typeFilter(returnType)
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
