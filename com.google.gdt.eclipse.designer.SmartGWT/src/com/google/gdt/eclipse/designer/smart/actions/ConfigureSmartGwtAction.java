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
package com.google.gdt.eclipse.designer.smart.actions;

import com.google.gdt.eclipse.designer.actions.AbstractModuleAction;
import com.google.gdt.eclipse.designer.actions.wizard.model.IModuleConfigurator;
import com.google.gdt.eclipse.designer.preferences.IPreferenceConstants;
import com.google.gdt.eclipse.designer.smart.Activator;
import com.google.gdt.eclipse.designer.util.ModuleDescription;

import org.eclipse.wb.internal.core.DesignerPlugin;
import org.eclipse.wb.internal.core.utils.execution.ExecutionUtils;
import org.eclipse.wb.internal.core.utils.execution.RunnableEx;
import org.eclipse.wb.internal.core.wizards.AbstractActionDelegate;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.DirectoryDialog;

import java.io.File;

/**
 * Action for configuring GWT module for using SmartGWT.
 * 
 * @author scheglov_ke
 * @coverage SmartGWT.actions
 */
public final class ConfigureSmartGwtAction extends AbstractActionDelegate
    implements
      IModuleConfigurator {
  ////////////////////////////////////////////////////////////////////////////
  //
  // IActionDelegate
  //
  ////////////////////////////////////////////////////////////////////////////
  public void run(IAction action) {
    ExecutionUtils.runLog(new RunnableEx() {
      public void run() throws Exception {
        ModuleDescription module = AbstractModuleAction.getSelectedModule(getSelection());
        configure(module);
      }
    });
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // IModuleConfigurator
  //
  ////////////////////////////////////////////////////////////////////////////
  public void configure(ModuleDescription module) throws Exception {
    String libraryLocation = getLibraryLocation();
    if (libraryLocation != null) {
      new ConfigureSmartGwtOperation(module, libraryLocation).run(null);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  ////////////////////////////////////////////////////////////////////////////
  private static String LOCATION_KEY = IPreferenceConstants.TOOLKIT_ID
      + ".SmartGWT.LibraryLocation";

  /**
   * @return the folder with "gwt.jar" file, or <code>null</code> if user cancelled selection.
   */
  private static String getLibraryLocation() {
    IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
    String libraryLocation = preferenceStore.getString(LOCATION_KEY);
    while (true) {
      DirectoryDialog directoryDialog = new DirectoryDialog(DesignerPlugin.getShell());
      directoryDialog.setText("Smart GWT Location");
      directoryDialog.setMessage("Choose folder with extracted SmartGWT, it should have smartgwt.jar file.\n"
          + "You can download it from http://code.google.com/p/smartgwt/");
      directoryDialog.setFilterPath(libraryLocation);
      libraryLocation = directoryDialog.open();
      // cancel
      if (libraryLocation == null) {
        return null;
      }
      // check for "gxt.jar" file
      {
        File file = new File(libraryLocation + "/smartgwt.jar");
        if (!file.exists() || !file.isFile()) {
          MessageDialog.openError(null, "Invalid folder", "No smartgwt.jar file in choosen folder.");
          continue;
        }
      }
      // OK
      preferenceStore.setValue(LOCATION_KEY, libraryLocation);
      return libraryLocation;
    }
  }
}