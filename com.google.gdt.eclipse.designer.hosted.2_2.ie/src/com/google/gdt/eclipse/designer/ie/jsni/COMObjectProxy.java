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
package com.google.gdt.eclipse.designer.ie.jsni;

import org.eclipse.swt.internal.ole.win32.COMObject;

import com.google.gdt.eclipse.designer.ie.util.Utils;

import java.util.Map;

/**
 * A proxy object that allows you to override behavior in an existing COM object. This is used
 * primarily for fixing up the {@link org.eclipse.swt.browser.Browser} object's 'window.external'
 * handling.
 */
class COMObjectProxy extends COMObject {
  private static final int MAX_METHODS_WRAPPED = 23;
  private COMObject target;

  /**
   * Construct a proxy object.
   * 
   * @param argCounts
   *          must be the same array of argCounts used to contruct the wrapped object.
   */
  public COMObjectProxy(int[] argCounts) {
    // Construct myself as a COMObject, even though my vtbl will never
    // actually be used, I need to castable to a COMObject when I injected
    // myself into the ObjectMap.
    super(argCounts);
    // Because I will never be called through my vtbl, I can free my OS
    // memory created in the superclass ctor and remove myself from the
    // ObjectMap. If this didn't work, we'd be leaking memory when
    // the last release is called (unless we assume that method index 2 was
    // Release(), which actually is likely a safe assumption.)
    dispose();
    // Make sure the interface isn't too big.
    if (argCounts != null && argCounts.length >= MAX_METHODS_WRAPPED) {
      throw new IllegalArgumentException("No more than "
        + MAX_METHODS_WRAPPED
        + " methods can be wrapped right now.");
    }
  }

  /**
   * Interpose this object in front of an existing object.
   */
  public void interpose(COMObject victim) {
    if (this.target != null) {
      throw new IllegalStateException("interpose() can only be called once");
    }
    // Hang onto the object we're wrapping so that we can delegate later.
    this.target = victim;
    // Get the COMObject ObjectMap so that we can hijack the target's slot.
    // First, make sure that the target is still actually in the map.
    // If it isn't still in there, then the caller is using me incorrectly.
    COMObject currValue = getCOMObject(target.getAddress());
    if (currValue != target) {
      throw new IllegalStateException("target object is not currently mapped");
    }
    // Replace target's entry in COMObject's (vtbl -> instance) map with
    // a reference to this object instead. Calls still come in on the
    // target's vtbl, but COMObject will route them to me instead,
    // so that I can hook/delegate them.
    setCOMObject(target.getAddress(), this);
  }

  public static COMObject getCOMObject(int address) {
    return (COMObject) getObjectMap().get(getKey(address));
  }

  public static void setCOMObject(int address, COMObject obj) {
    getObjectMap().put(getKey(address), obj);
  }

  private static Object getKey(int address) {
    return new org.eclipse.swt.internal.LONG(address);
  }

  private static Map getObjectMap() {
    // Get the COMObject ObjectMap so that we can hijack the target's slot.
    Map<org.eclipse.swt.internal.LONG, COMObject> objectMap =
        (Map<org.eclipse.swt.internal.LONG, COMObject>) Utils.getFieldObjectValue(
          COMObject.class,
          null,
          "ObjectMap");
    return objectMap;
  }

  @Override
  public int method0(int[] args) {
    return target.method0(args);
  }

  @Override
  public int method1(int[] args) {
    return target.method1(args);
  }

  @Override
  public int method10(int[] args) {
    return target.method10(args);
  }

  @Override
  public int method11(int[] args) {
    return target.method11(args);
  }

  @Override
  public int method12(int[] args) {
    return target.method12(args);
  }

  @Override
  public int method13(int[] args) {
    return target.method13(args);
  }

  @Override
  public int method14(int[] args) {
    return target.method14(args);
  }

  @Override
  public int method15(int[] args) {
    return target.method15(args);
  }

  @Override
  public int method16(int[] args) {
    return target.method16(args);
  }

  @Override
  public int method17(int[] args) {
    return target.method17(args);
  }

  @Override
  public int method18(int[] args) {
    return target.method18(args);
  }

  @Override
  public int method19(int[] args) {
    return target.method19(args);
  }

  @Override
  public int method2(int[] args) {
    return target.method2(args);
  }

  @Override
  public int method20(int[] args) {
    return target.method20(args);
  }

  @Override
  public int method21(int[] args) {
    return target.method21(args);
  }

  @Override
  public int method22(int[] args) {
    return target.method22(args);
  }
}
