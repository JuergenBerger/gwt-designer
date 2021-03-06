/*******************************************************************************
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.google.gwt.dev.jjs.ast;

import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.util.collect.HashMap;

import java.util.Map;

/**
 * Base class for all Java primitive types.
 */
public class JPrimitiveType extends JType {

  private static final class Singletons {
    public static final Map<String, JPrimitiveType> map = new HashMap<String, JPrimitiveType>();
  }

  public static final JPrimitiveType BOOLEAN = new JPrimitiveType("boolean",
      "Z", "java.lang.Boolean", JBooleanLiteral.FALSE);

  public static final JPrimitiveType BYTE = new JPrimitiveType("byte", "B",
      "java.lang.Byte", JIntLiteral.ZERO);

  public static final JPrimitiveType CHAR = new JPrimitiveType("char", "C",
      "java.lang.Character", JCharLiteral.NULL);

  public static final JPrimitiveType DOUBLE = new JPrimitiveType("double", "D",
      "java.lang.Double", JDoubleLiteral.ZERO);

  public static final JPrimitiveType FLOAT = new JPrimitiveType("float", "F",
      "java.lang.Float", JFloatLiteral.ZERO);

  public static final JPrimitiveType INT = new JPrimitiveType("int", "I",
      "java.lang.Integer", JIntLiteral.ZERO);

  public static final JPrimitiveType LONG = new JPrimitiveType("long", "J",
      "java.lang.Long", JLongLiteral.ZERO);

  public static final JPrimitiveType SHORT = new JPrimitiveType("short", "S",
      "java.lang.Short", JIntLiteral.ZERO);

  public static final JPrimitiveType VOID = new JPrimitiveType("void", "V",
      "java.lang.Void", null);

  private final String signatureName;

  private final String wrapperTypeName;

  private JPrimitiveType(String name, String signatureName,
      String wrapperTypeName, JLiteral defaultValue) {
    super(SourceOrigin.UNKNOWN, name, defaultValue);
    this.signatureName = signatureName;
    this.wrapperTypeName = wrapperTypeName;
    Singletons.map.put(name, this);
  }

  /**
   * Returns a literal which has been coerced to this type, or <code>null</code>
   * if no such coercion is possible.
   */
  public JValueLiteral coerceLiteral(JValueLiteral value) {
    JLiteral defaultValue = getDefaultValue();
    if (defaultValue instanceof JValueLiteral) {
      JValueLiteral defaultValueLiteral = (JValueLiteral) defaultValue;
      return defaultValueLiteral.cloneFrom(value);
    }
    return null;
  }

  @Override
  public String getClassLiteralFactoryMethod() {
    return "Class.createForPrimitive";
  }

  public String getJavahSignatureName() {
    return signatureName;
  }

  public String getJsniSignatureName() {
    return signatureName;
  }

  public String getWrapperTypeName() {
    return wrapperTypeName;
  }

  public boolean isFinal() {
    return true;
  }

  public void traverse(JVisitor visitor, Context ctx) {
    if (visitor.visit(this, ctx)) {
    }
    visitor.endVisit(this, ctx);
  }

  private Object readResolve() {
    return Singletons.map.get(name);
  }
}
