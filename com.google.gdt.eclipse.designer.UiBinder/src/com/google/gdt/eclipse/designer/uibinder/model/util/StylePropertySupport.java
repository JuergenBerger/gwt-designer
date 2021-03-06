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
package com.google.gdt.eclipse.designer.uibinder.model.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gdt.eclipse.designer.model.property.css.ContextDescription;
import com.google.gdt.eclipse.designer.model.property.css.FileContextDescription;
import com.google.gdt.eclipse.designer.model.property.css.StylePropertyEditor;
import com.google.gdt.eclipse.designer.model.property.css.StylePropertyEditorListener;
import com.google.gdt.eclipse.designer.uibinder.parser.AfterRunDesignTime;
import com.google.gdt.eclipse.designer.uibinder.parser.UiBinderContext;

import org.eclipse.wb.core.model.ObjectInfo;
import org.eclipse.wb.core.model.broadcast.EditorActivatedListener;
import org.eclipse.wb.core.model.broadcast.EditorActivatedRequest;
import org.eclipse.wb.core.model.broadcast.ObjectEventListener;
import org.eclipse.wb.internal.core.utils.execution.ExecutionUtils;
import org.eclipse.wb.internal.core.utils.execution.RunnableEx;
import org.eclipse.wb.internal.core.utils.jdt.core.CodeUtils;
import org.eclipse.wb.internal.core.utils.jdt.core.JavaDocUtils;
import org.eclipse.wb.internal.core.utils.reflect.ReflectionUtils;
import org.eclipse.wb.internal.core.utils.xml.DocumentElement;
import org.eclipse.wb.internal.core.utils.xml.DocumentTextNode;
import org.eclipse.wb.internal.core.xml.model.EditorContextCommitListener;
import org.eclipse.wb.internal.core.xml.model.utils.NamespacesHelper;
import org.eclipse.wb.internal.css.model.CssRuleNode;
import org.eclipse.wb.internal.css.parser.CssEditContext;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * Support for UiBinder template specific in {@link StylePropertyEditor}.
 * 
 * @author scheglov_ke
 * @coverage GWT.UiBinder.model
 */
public final class StylePropertySupport {
  private final UiBinderContext m_context;
  private final List<ContextDescription> m_contextDescriptions = Lists.newArrayList();

  ////////////////////////////////////////////////////////////////////////////
  //
  // Constructor
  //
  ////////////////////////////////////////////////////////////////////////////
  public StylePropertySupport(UiBinderContext context) throws Exception {
    m_context = context;
    createExternalContextDescriptions();
    createLocalContextDescription();
    // add context descriptions
    m_context.getBroadcastSupport().addListener(null, new StylePropertyEditorListener() {
      @Override
      public void addContextDescriptions(ObjectInfo object, List<ContextDescription> contexts)
          throws Exception {
        syncExternalContextDescriptions();
        for (ContextDescription context : m_contextDescriptions) {
          contexts.add(0, context);
        }
      }
    });
    // reload after render
    m_context.getBroadcastSupport().addListener(null, new AfterRunDesignTime() {
      public void invoke() throws Exception {
        reloadExternalClientBundles();
      }
    });
    // refresh on external CSS file change
    m_context.getBroadcastSupport().addListener(null, new EditorActivatedListener() {
      public void invoke(EditorActivatedRequest request) throws Exception {
        for (ContextDescription contextDescription : m_contextDescriptions) {
          if (contextDescription.isStale()) {
            request.requestRefresh();
          }
        }
      }
    });
    // dispose on hierarchy dispose
    m_context.getBroadcastSupport().addListener(null, new ObjectEventListener() {
      @Override
      public void dispose() throws Exception {
        for (ContextDescription context : m_contextDescriptions) {
          context.dispose();
        }
      }
    });
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // <ui:with field='resources' type='fully.qualified.Resources'/>
  //
  ////////////////////////////////////////////////////////////////////////////
  private static class WithObject {
    String field;
    IType withType;
    Class<?> withClass;
  }

  private final List<WithObject> m_withObjects = Lists.newArrayList();

  /**
   * @return the {@link ContextDescription}s for "<ui:with>" elements.
   */
  private void createExternalContextDescriptions() throws Exception {
    createWithObjects();
    for (final WithObject withObject : m_withObjects) {
      ExecutionUtils.runIgnore(new RunnableEx() {
        public void run() throws Exception {
          createExternalContextDescriptions(withObject);
        }
      });
    }
  }

  /**
   * May be adds single {@link UiBinderExternalContextDescription}.
   */
  private void createExternalContextDescriptions(WithObject withObject) throws Exception {
    String field = withObject.field;
    IType withType = withObject.withType;
    Class<?> withClass = withObject.withClass;
    Map<Class<?>, IFile> styleToFile = getExternalPossibleCssFiles(withType);
    // check every CSS method in "withClass"
    for (Method styleMethod : withClass.getMethods()) {
      Class<?> styleClass = styleMethod.getReturnType();
      if (isCssResource(styleClass)) {
        IFile file = styleToFile.get(styleClass);
        if (file != null) {
          // prepare selectors accessible via this "styleClass"
          Set<String> styleSelectors = Sets.newHashSet();
          for (Method singleStyleMethod : styleClass.getMethods()) {
            styleSelectors.add("." + singleStyleMethod.getName());
          }
          // add ContextDescription
          m_contextDescriptions.add(new UiBinderExternalContextDescription(field,
              styleMethod.getName(),
              styleSelectors,
              file));
        }
      }
    }
  }

  /**
   * Checks if {@link UiBinderExternalContextDescription} is out of sync with its {@link IFile} and
   * then makes it sync.
   */
  private void syncExternalContextDescriptions() throws Exception {
    for (ListIterator<ContextDescription> I = m_contextDescriptions.listIterator(); I.hasNext();) {
      ContextDescription context = I.next();
      if (context instanceof UiBinderExternalContextDescription) {
        if (context.isStale()) {
          ContextDescription syncCopy =
              ((UiBinderExternalContextDescription) context).createSyncCopy();
          context.dispose();
          I.set(syncCopy);
        }
      }
    }
  }

  /**
   * Parses all "<ui:with>" elements and fills {@link #m_withObjects}.
   */
  private void createWithObjects() throws Exception {
    for (final DocumentElement withElement : getExternalStyleElements()) {
      ExecutionUtils.runIgnore(new RunnableEx() {
        public void run() throws Exception {
          String field = withElement.getAttribute("field");
          String withTypeName = withElement.getAttribute("type");
          IType withType = m_context.getJavaProject().findType(withTypeName);
          Class<?> withClass = m_context.getClassLoader().loadClass(withTypeName);
          {
            WithObject withObject = new WithObject();
            withObject.field = field;
            withObject.withType = withType;
            withObject.withClass = withClass;
            m_withObjects.add(withObject);
          }
        }
      });
    }
  }

  /**
   * Tries to find and reload all used <code>ClientBundle</code>s.
   */
  private void reloadExternalClientBundles() throws Exception {
    for (final WithObject withObject : m_withObjects) {
      ExecutionUtils.runIgnore(new RunnableEx() {
        public void run() throws Exception {
          Class<?> withClass = withObject.withClass;
          for (Field fieldRef : withClass.getDeclaredFields()) {
            Class<?> bundleClass = fieldRef.getType();
            if (ReflectionUtils.isStatic(fieldRef) && isClientBundle(bundleClass)) {
              reloadExternalClientBundleField(withObject, fieldRef);
            }
          }
        }
      });
    }
  }

  /**
   * Attempts to reload <code>ClientBundle</code> {@link Field} according to the specified JavaDoc
   * annotation.
   */
  private void reloadExternalClientBundleField(WithObject withObject, Field fieldRef)
      throws Exception {
    fieldRef.setAccessible(true);
    Class<?> bundleClass = fieldRef.getType();
    String bundleClassName = bundleClass.getName();
    // prepare JavaDoc lines
    List<String> javaDocLines;
    {
      String fieldName = fieldRef.getName();
      IField fieldModel = withObject.withType.getField(fieldName);
      javaDocLines = JavaDocUtils.getJavaDocLines(fieldModel, false);
    }
    // check GWT specific JavaDoc marker
    for (String javaDocLine : javaDocLines) {
      javaDocLine = javaDocLine.trim();
      // set "null" to force lazy re-creation
      if (javaDocLine.equals("@gwtd.reload.null")) {
        m_context.getState().getDevModeBridge().invalidateRebind(bundleClassName);
        fieldRef.set(null, null);
      }
      // re-create ClientBundle of same type
      if (javaDocLine.equals("@gwtd.reload.create")) {
        m_context.getState().getDevModeBridge().invalidateRebind(bundleClassName);
        Class<?> classGWT = m_context.getClassLoader().loadClass("com.google.gwt.core.client.GWT");
        Object newBundle =
            ReflectionUtils.invokeMethod(classGWT, "create(java.lang.Class)", bundleClass);
        // set new ClientBundle instance
        fieldRef.set(null, newBundle);
        // inject CssResource objects
        Method[] styleMethods = newBundle.getClass().getMethods();
        for (Method styleMethod : styleMethods) {
          if (styleMethod.getParameterTypes().length == 0
              && isCssResource(styleMethod.getReturnType())) {
            Object cssResource = styleMethod.invoke(newBundle);
            ReflectionUtils.invokeMethod(cssResource, "ensureInjected()");
          }
        }
      }
    }
  }

  /**
   * @return the {@link Map} of <code>ClientBundle</code> classes into resource {@link IFile}s.
   */
  private Map<Class<?>, IFile> getExternalPossibleCssFiles(final IType withType) throws Exception {
    final Map<Class<?>, IFile> styleToFile = Maps.newHashMap();
    // check every inner type
    for (final IType clientBundleType : withType.getTypes()) {
      if (isClientBundle(clientBundleType)) {
        ExecutionUtils.runIgnore(new RunnableEx() {
          public void run() throws Exception {
            addExternalPossibleCssFiles(styleToFile, withType, clientBundleType);
          }
        });
      }
    }
    return styleToFile;
  }

  /**
   * Adds CSS {@link IFile}s for {@link CssResource} methods of given {@link ClientBundle} type.
   */
  private void addExternalPossibleCssFiles(Map<Class<?>, IFile> styleToFile,
      IType withType,
      IType clientBundleType) throws Exception {
    IFolder withFolder = (IFolder) withType.getPackageFragment().getUnderlyingResource();
    String clientBundleName = clientBundleType.getFullyQualifiedName();
    Class<?> clientBundleClass = m_context.getClassLoader().loadClass(clientBundleName);
    // check every method
    for (Method styleMethod : clientBundleClass.getMethods()) {
      Class<?> styleClass = styleMethod.getReturnType();
      String fileName = getClientBundleSource(styleMethod);
      // we need CssResource return type, and existing @Source annotation
      if (isCssResource(styleClass) && fileName != null) {
        IFile file = withFolder.getFile(new Path(fileName));
        if (file.exists()) {
          styleToFile.put(styleClass, file);
        }
      }
    }
  }

  /**
   * @return the @Source path to resource.
   */
  @SuppressWarnings("unchecked")
  private String getClientBundleSource(AnnotatedElement obj) throws Exception {
    Class<Annotation> sourceClass =
        (Class<Annotation>) m_context.getClassLoader().loadClass(
            "com.google.gwt.resources.client.ClientBundle$Source");
    Annotation annotation = obj.getAnnotation(sourceClass);
    if (annotation != null) {
      return ((String[]) ReflectionUtils.invokeMethod(annotation, "value()"))[0];
    } else {
      return null;
    }
  }

  private static boolean isCssResource(Class<?> clazz) {
    return ReflectionUtils.isSuccessorOf(clazz, "com.google.gwt.resources.client.CssResource");
  }

  private static boolean isClientBundle(Class<?> clazz) throws JavaModelException {
    return ReflectionUtils.isSuccessorOf(clazz, "com.google.gwt.resources.client.ClientBundle");
  }

  private static boolean isClientBundle(IType type) throws JavaModelException {
    return CodeUtils.isSuccessorOf(type, "com.google.gwt.resources.client.ClientBundle");
  }

  /**
   * @return the {@link DocumentElement}s with "ui:with" tag.
   */
  private List<DocumentElement> getExternalStyleElements() {
    List<DocumentElement> elements = Lists.newArrayList();
    DocumentElement rootElement = m_context.getRootElement();
    String uiName = NamespacesHelper.getName(rootElement, "urn:ui:com.google.gwt.uibinder");
    for (DocumentElement withElement : rootElement.getChildren()) {
      if (withElement.getTag().equals(uiName + ":with")) {
        String field = withElement.getAttribute("field");
        String typeName = withElement.getAttribute("type");
        if (field != null && typeName != null) {
          elements.add(withElement);
        }
      }
    }
    return elements;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // UiBinderExternalContextDescription
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * {@link ContextDescription} for context from "<ui:style>" element.
   */
  private static class UiBinderExternalContextDescription extends FileContextDescription {
    private final String m_fieldName;
    private final String m_resourceName;
    private final Set<String> m_styleSelectors;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////
    public UiBinderExternalContextDescription(String fieldName,
        String resourceName,
        Set<String> styleSelectors,
        IFile file) throws Exception {
      super(file);
      m_fieldName = fieldName;
      m_resourceName = resourceName;
      m_styleSelectors = styleSelectors;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Special access
    //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @return the {@link UiBinderExternalContextDescription} which is in sync with CSS file.
     */
    public UiBinderExternalContextDescription createSyncCopy() throws Exception {
      return new UiBinderExternalContextDescription(m_fieldName,
          m_resourceName,
          m_styleSelectors,
          getFile());
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Access
    //
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public String getStyleName(CssRuleNode rule) {
      String selector = rule.getSelector().getValue();
      if (selector.startsWith(".")) {
        return MessageFormat.format(
            "'{'{0}.{1}.{2}'}'",
            m_fieldName,
            m_resourceName,
            selector.substring(1));
      }
      return null;
    }

    @Override
    public List<CssRuleNode> getRules() {
      List<CssRuleNode> rules = Lists.newArrayList();
      List<CssRuleNode> allRules = super.getRules();
      for (CssRuleNode rule : allRules) {
        String selector = rule.getSelector().getValue();
        if (m_styleSelectors.contains(selector)) {
          rules.add(rule);
        }
      }
      return rules;
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // <ui:style> implementation
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * Creates {@link ContextDescription} for "<ui:style>" element.
   */
  private void createLocalContextDescription() throws Exception {
    DocumentElement styleElement = getLocalStyleElement();
    if (styleElement != null) {
      DocumentTextNode textNode = styleElement.getTextNode();
      String cssSource = textNode != null ? textNode.getRawText() : "";
      IDocument cssDocument = new Document(cssSource);
      CssEditContext cssContext = new CssEditContext(cssDocument);
      ContextDescription contextDescription =
          new UiBinderLocalContextDescription(m_context, styleElement, cssContext);
      m_contextDescriptions.add(contextDescription);
    }
  }

  /**
   * @return the "<ui:style>" element, may be <code>null</code>.
   */
  private DocumentElement getLocalStyleElement() {
    DocumentElement rootElement = m_context.getRootElement();
    String uiName = NamespacesHelper.getName(rootElement, "urn:ui:com.google.gwt.uibinder");
    return rootElement.getChild(uiName + ":style", true);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // UiBinderLocalContextDescription
  //
  ////////////////////////////////////////////////////////////////////////////
  /**
   * {@link ContextDescription} for context from "<ui:style>" element.
   */
  private static class UiBinderLocalContextDescription extends ContextDescription {
    private final UiBinderContext m_uiContext;
    private final DocumentElement m_styleElement;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////
    public UiBinderLocalContextDescription(UiBinderContext uiContext,
        DocumentElement styleElement,
        CssEditContext cssContext) {
      super(cssContext);
      m_uiContext = uiContext;
      m_styleElement = styleElement;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Access
    //
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public String getStyleName(CssRuleNode rule) {
      String selector = rule.getSelector().getValue();
      if (selector.startsWith(".")) {
        return "{style." + selector.substring(1) + "}";
      }
      return null;
    }

    @Override
    public void commit() throws Exception {
      getCommitListener().aboutToCommit();
      try {
        DocumentTextNode textNode = m_styleElement.getTextNode();
        String cssSource = getContext().getText();
        textNode.setText(cssSource);
      } finally {
        getCommitListener().doneCommit();
      }
    }

    private EditorContextCommitListener getCommitListener() {
      return m_uiContext.getBroadcastSupport().getListener(EditorContextCommitListener.class);
    }
  }
}
