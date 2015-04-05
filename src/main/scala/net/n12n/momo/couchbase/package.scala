/*
 * Copyright 2015 Niklas Grossmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.n12n.momo

import com.couchbase.client.java.view.AsyncViewResult
import rx.functions.{Func2, Func1, Action1}

package object couchbase {
  implicit class Action1Fun[T](f: (T) => Unit) extends Action1[T] {
    override def call(a1: T): Unit = {
      f(a1)
    }
  }

  implicit class Func1Adapter[T, R](f: (T) => R) extends Func1[T, R] {
    override def call(a1: T): R = f(a1)
  }

  implicit class Func2Adapter[T, U, R](f: (T, U) => R) extends Func2[T, U, R] {
    override def call(a1: T, a2: U): R = f(a1, a2)
  }

  val view2rows = (result: AsyncViewResult) => result.rows()

}
