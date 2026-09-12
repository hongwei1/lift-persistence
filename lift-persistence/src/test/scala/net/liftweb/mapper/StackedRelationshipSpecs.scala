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
 * through super, so an entity may mix in both. Either implementation calling the
 * Mapper base method directly instead of super silently skips whichever trait
 * linearizes below it, leaving children unsaved while save still returns true.
 *
 * Both mixin orders are covered, because each only exercises the trait that ends
 * up outermost: with ManyToMany last it is ManyToMany's chaining under test, and
 * with OneToMany last it is OneToMany's.
 */
class StackedRelationshipSpecs extends Specification {
  "Stacked OneToMany and ManyToMany Specification".title
  sequential

  val provider = DbProviders.H2MemoryProvider

  private def ignoreLogger(f: => AnyRef): Unit = ()

  def setupDB: Unit = {
    MapperRules.createForeignKeys_? = c => false
    provider.setupDB
    Schemifier.destroyTables_!!(ignoreLogger _, TutorCourse, TeacherCourse, Course, Pupil, Tutor, Teacher)
    Schemifier.schemify(true, ignoreLogger _, Teacher, Tutor, Pupil, Course, TeacherCourse, TutorCourse)
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

  // The reverse order, which is what downstream entities actually write (OBP's
  // ResourceUser is `with ManyToMany with OneToMany[...]`). Here OneToMany is the
  // outer trait, so a regression in OneToMany rather than ManyToMany would be
  // invisible to the specs above.
  "An entity mixing OneToMany and ManyToMany, OneToMany outermost" should {
    "save its one-to-many children" in {
      setupDB
      val tutor = new Tutor
      tutor.pupils += Pupil.create

      tutor.save must_== true

      Tutor.count must_== 1
      Pupil.count must_== 1
    }

    // The one that actually pins OneToMany's chaining: the many-to-many work
    // lives in the trait below it, so it only happens if OneToMany.save reaches
    // the rest of the chain through super.
    "save its many-to-many children" in {
      setupDB
      val tutor = new Tutor
      val course = new Course
      course.save
      tutor.courses += course

      tutor.save must_== true

      tutor.courses.refresh
      tutor.courses.length must_== 1
    }

    "cascade delete to its one-to-many children" in {
      setupDB
      val tutor = new Tutor
      tutor.pupils += Pupil.create
      tutor.save

      Pupil.count must_== 1

      tutor.delete_! must_== true

      Tutor.count must_== 0
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

class Tutor extends LongKeyedMapper[Tutor] with IdPK
  with ManyToMany with OneToMany[Long, Tutor] {
  def getSingleton = Tutor
  object pupils extends MappedOneToMany(Pupil, Pupil.tutor) with Cascade[Pupil]
  object courses extends MappedManyToMany(TutorCourse, TutorCourse.tutor, TutorCourse.course, Course)
}
object Tutor extends Tutor with LongKeyedMetaMapper[Tutor]

class TutorCourse extends Mapper[TutorCourse] {
  def getSingleton = TutorCourse
  object tutor extends MappedLongForeignKey(this, Tutor)
  object course extends MappedLongForeignKey(this, Course)
}
object TutorCourse extends TutorCourse with MetaMapper[TutorCourse]

class Pupil extends LongKeyedMapper[Pupil] with IdPK {
  def getSingleton = Pupil
  object teacher extends MappedLongForeignKey(this, Teacher)
  object tutor extends MappedLongForeignKey(this, Tutor)
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
