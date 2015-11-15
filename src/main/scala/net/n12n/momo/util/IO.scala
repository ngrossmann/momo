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
package net.n12n.momo.util

import java.io.{InputStreamReader, InputStream}

/**
 * IO utility functions.
 */
object IO {
  def toBytes(in: InputStream): Array[Byte] = {
    try {
      Stream.continually(in.read()).takeWhile(_ != -1).map(_.toByte).toArray
    } finally {
      in.close()
    }
  }

  def toString(in: InputStream, encoding: String): String = {
    val reader = new InputStreamReader(in)
    try {
      new String(Stream.continually(reader.read()).takeWhile(_ != -1).map(
        _.toChar).toArray)
    } finally {
      reader.close()
    }
  }
}
