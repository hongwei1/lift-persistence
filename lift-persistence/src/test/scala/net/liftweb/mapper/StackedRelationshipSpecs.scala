/*
 * Copyright 2009-2011 WorldWide Conferencing, LLC
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

import org.specs2.mutable.Specification

/**
 * OneToMany and ManyToMany are stackable: both override save/delete_! and chain
 * through super, so an entity may mix in both. These specs pin that chaining in
 * the order that puts ManyToMany outermost, where a ManyToMany implementation
 * calling the Mapper base method directly instead of super would silently skip
 * OneToMany entirely.
 */
class StackedRelationshipSpecs extends Specification {
  "Stacked OneToMany and ManyToMany Specification".title
  sequential

  val provider = DbProviders.H2MemoryProvider

  private def ignoreLogger(f: => AnyRef): Unit = ()

  def setupDB: Unit = {
    MapperRules.createForeignKeys_? = c => false
    provider.setupDB
    Schemifier.destroyTables_!!(ignoreLogger _, TeacherCourse, Course, Pupil, Teacher)
    Schemifier.schemify(true, ignoreLogger _, Teacher, Pupil, Course, TeacherCourse)
  }

  "An entity mixing OneToMany and ManyToMany, ManyToMany outermost" should {
    "save its one-to-many children" in {
      setupDB
      val teacher = new Teacher
      teacher.pupils += Pupil.create

      teacher.save must_== true

      Teacher.count must_== 1
      Pupil.count must_== 1
    }

    "save its many-to-many children" in {
      setupDB
      val teacher = new Teacher
      val course = new Course
      course.save
      teacher.courses += course

      teacher.save must_== true

      teacher.courses.refresh
      teacher.courses.length must_== 1
    }

    "cascade delete to its one-to-many children" in {
      setupDB
      val teacher = new Teacher
      teacher.pupils += Pupil.create
      teacher.save

      Pupil.count must_== 1

      teacher.delete_! must_== true

      Teacher.count must_== 0
      Pupil.count must_== 0
    }
  }
}

class Teacher extends LongKeyedMapper[Teacher] with IdPK
  with OneToMany[Long, Teacher] with ManyToMany {
  def getSingleton = Teacher
  object pupils extends MappedOneToMany(Pupil, Pupil.teacher) with Cascade[Pupil]
  object courses extends MappedManyToMany(TeacherCourse, TeacherCourse.teacher, TeacherCourse.course, Course)
}
object Teacher extends Teacher with LongKeyedMetaMapper[Teacher]

class Pupil extends LongKeyedMapper[Pupil] with IdPK {
  def getSingleton = Pupil
  object teacher extends MappedLongForeignKey(this, Teacher)
  object name extends MappedString(this, 10)
}
object Pupil extends Pupil with LongKeyedMetaMapper[Pupil]

class Course extends LongKeyedMapper[Course] with IdPK {
  def getSingleton = Course
  object title extends MappedString(this, 10)
}
object Course extends Course with LongKeyedMetaMapper[Course]

class TeacherCourse extends Mapper[TeacherCourse] {
  def getSingleton = TeacherCourse
  object teacher extends MappedLongForeignKey(this, Teacher)
  object course extends MappedLongForeignKey(this, Course)
}
object TeacherCourse extends TeacherCourse with MetaMapper[TeacherCourse]
