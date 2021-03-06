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
package com.google.gdt.eclipse.designer.gxt.model.widgets;

import com.google.common.collect.Lists;

import org.eclipse.wb.internal.core.model.JavaInfoUtils;
import org.eclipse.wb.internal.core.model.creation.CreationSupport;
import org.eclipse.wb.internal.core.model.creation.ThisCreationSupport;
import org.eclipse.wb.internal.core.model.description.ComponentDescription;
import org.eclipse.wb.internal.core.model.property.Property;
import org.eclipse.wb.internal.core.model.property.category.PropertyCategory;
import org.eclipse.wb.internal.core.model.util.PropertyUtils;
import org.eclipse.wb.internal.core.utils.GenericsUtils;
import org.eclipse.wb.internal.core.utils.ast.AstEditor;
import org.eclipse.wb.internal.core.utils.ast.StatementTarget;
import org.eclipse.wb.internal.core.utils.reflect.ReflectionUtils;

import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.util.List;

/**
 * Model for <code>com.extjs.gxt.ui.client.widget.Composite</code>.
 * 
 * @author scheglov_ke
 * @coverage ExtGWT.model
 */
public class CompositeInfo extends BoxComponentInfo {
  ////////////////////////////////////////////////////////////////////////////
  //
  // Constructor
  //
  ////////////////////////////////////////////////////////////////////////////
  public CompositeInfo(AstEditor editor,
      ComponentDescription description,
      CreationSupport creationSupport) throws Exception {
    super(editor, description, creationSupport);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Access
  //
  ////////////////////////////////////////////////////////////////////////////
  private boolean m_empty;

  /**
   * @return <code>true</code> if this <code>Composite</code> does not have widget.
   */
  public boolean isEmpty() {
    return m_empty;
  }

  /**
   * @return the single {@link ComponentInfo} child or <code>null</code> if component is not set
   *         yet.
   */
  public ComponentInfo getComponent() {
    return GenericsUtils.getFirstOrNull(getChildren(ComponentInfo.class));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Properties
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  protected List<Property> getPropertyList() throws Exception {
    List<Property> properties = super.getPropertyList();
    // no content - no properties
    if (isEmpty()) {
      return Lists.newArrayList();
    }
    // don't suggest users to resize Composite, it is better to resize content
    if (getCreationSupport() instanceof ThisCreationSupport) {
      PropertyUtils.getByTitle(properties, "Size").setCategory(PropertyCategory.ADVANCED);
    }
    //
    return properties;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // MethodInvocation
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  protected StatementTarget getMethodInvocationTarget(String newSignature) throws Exception {
    if (getCreationSupport() instanceof ThisCreationSupport) {
      ThisCreationSupport creationSupport = (ThisCreationSupport) getCreationSupport();
      MethodDeclaration constructor = creationSupport.getConstructor();
      return new StatementTarget(constructor, false);
    }
    return super.getMethodInvocationTarget(newSignature);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Refresh
  //
  ////////////////////////////////////////////////////////////////////////////
  @Override
  public void setObject(Object object) throws Exception {
    super.setObject(object);
    // it is possible that Composite was not created fully, so has no component
    if (!(getCreationSupport() instanceof ThisCreationSupport)) {
      ensureWidget();
    }
  }

  @Override
  protected void attachAfterConstructor() throws Exception {
    ensureWidget();
    super.attachAfterConstructor();
  }

  private void ensureWidget() throws Exception {
    // may be placeholder
    if (isPlaceholder()) {
      m_empty = false;
      return;
    }
    // OK, real Composite
    m_empty = ReflectionUtils.invokeMethod(getObject(), "getComponent()") == null;
    JavaInfoUtils.executeScriptParameter(this, "ensureComponentScript");
  }
}
