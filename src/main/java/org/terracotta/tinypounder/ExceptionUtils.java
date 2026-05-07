/*
 * Copyright Terracotta, Inc.
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

package org.terracotta.tinypounder;

import java.lang.reflect.InvocationTargetException;

public class ExceptionUtils {

  /**
   * Get the root cause of the specified throwable.
   *
   * @param t the throwable.
   * @return the root cause.
   */
  public static Throwable getRootCause(Throwable t) {
    Throwable last = null;
    while (t != null && t != last) {
      last = t;
      t = t.getCause();
    }
    // Java 21 pattern matching for instanceof
    if (last instanceof InvocationTargetException ite) {
      Throwable target = ite.getTargetException();
      if (target != null) {
        last = target;
      }
    }
    return last;
  }

  /**
   * Get the root cause message of the specified throwable.
   *
   * @param t the throwable.
   * @return the root cause.
   */
  public static String getRootCauseMessage(Throwable t) {
    String message;
    Throwable rootCause = getRootCause(t);
    message = rootCause.getMessage();
    if (message == null) {
      message = rootCause.getClass().toString();
    }
    return message;
  }

}
