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
package com.google.gwt.dev.cfg;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.FileLocator;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.util.Util;
import com.google.gwt.dev.util.xml.ReflectiveParser;
import com.google.gwt.util.tools.Utility;

/**
 * The top-level API for loading module XML.
 */
public final class ModuleDefLoader {

  /**
   * Interface to provide a load strategy to the load process.
   */
  private interface LoadStrategy {
    /**
     * Perform loading on the specified module.
     * 
     * @param logger logs the process
     * @param moduleName the name of the process
     * @param moduleDef a module
     * @throws UnableToCompleteException
     */
    void load(TreeLogger logger, String moduleName, ModuleDef moduleDef)
        throws UnableToCompleteException;
  }

  /**
   * Filename suffix used for GWT Module XML files.
   */
  public static final String GWT_MODULE_XML_SUFFIX = ".gwt.xml";

  /**
   * Keep soft references to loaded modules so the VM can gc them when memory is
   * tight.  The module's physical name is used as a key.
   */
  @SuppressWarnings("unchecked")
  private static final Map<String, ModuleDef> loadedModules = new ReferenceMap(
      AbstractReferenceMap.HARD, AbstractReferenceMap.SOFT);
  
  /**
   * A mapping from effective to physical module names.
   */
  private static final Map<String, String> moduleEffectiveNameToPhysicalName =
    new HashMap<String, String>();

  /**
   * Creates a module in memory that is not associated with a
   * <code>.gwt.xml</code> file on disk.
   * 
   * @param logger logs the process
   * @param moduleName the synthetic module to create
   * @param inherits a set of modules to inherit from
   * @param refresh whether to refresh the module
   * @return the loaded module
   * @throws UnableToCompleteException
   */
  public static ModuleDef createSyntheticModule(TreeLogger logger,
      String moduleName, String[] inherits, boolean refresh)
      throws UnableToCompleteException {
    ModuleDef moduleDef = tryGetLoadedModule(logger, moduleName, refresh);
    if (moduleDef != null) {
      return moduleDef;
    }
    ModuleDefLoader loader = new ModuleDefLoader(inherits);
    ModuleDef module = loader.doLoadModule(logger, moduleName);
    /*
     * Must reset name override on synthetic modules. Otherwise they'll be
     * incorrectly affected by the last inherits tag, because they have no XML
     * which would reset the name at the end of parse.
     */
    module.setNameOverride(null);
    return module;
  }

  /**
   * Loads a new module from the class path.  Equivalent to
   * {@link #loadFromClassPath(logger, moduleName, false)}.
   * 
   * @param logger logs the process
   * @param moduleName the module to load
   * @return the loaded module
   * @throws UnableToCompleteException
   */
  public static ModuleDef loadFromClassPath(TreeLogger logger, String moduleName)
      throws UnableToCompleteException {
    return loadFromClassPath(logger, moduleName, false);
  }
  
  /**
   * Loads a new module from the class path.
   * 
   * @param logger logs the process
   * @param moduleName the module to load
   * @param refresh whether to refresh the module
   * @return the loaded module
   * @throws UnableToCompleteException
   */
  public static ModuleDef loadFromClassPath(TreeLogger logger,
      String moduleName, boolean refresh) throws UnableToCompleteException {
    // Look up the module's physical name; if null, we are either encountering
    // the module for the first time, or else the name is already physical
    String physicalName = moduleEffectiveNameToPhysicalName.get(moduleName);
    if (physicalName != null) {
      moduleName = physicalName;
    }
    ModuleDef moduleDef = tryGetLoadedModule(logger, moduleName, refresh);
    if (moduleDef != null) {
      return moduleDef;
    }
    ModuleDefLoader loader = new ModuleDefLoader();
    return loader.doLoadModule(logger, moduleName);
  }

  private static ModuleDef tryGetLoadedModule(TreeLogger logger,
      String moduleName, boolean refresh) {
	// XXX <Instantiations
    String moduleKey = getModuleKey(moduleName);
    ModuleDef moduleDef = loadedModules.get(moduleKey);
    // XXX >Instantiations
    if (moduleDef == null || moduleDef.isGwtXmlFileStale()) {
      return null;
    } else if (refresh) {
      moduleDef.refresh(logger);
    }
    return moduleDef;
  }

  private final Set<String> alreadyLoadedModules = new HashSet<String>();

  private final ClassLoader classLoader;

  private final LoadStrategy strategy;

  /**
   * Constructs a {@link ModuleDefLoader} that loads from the class path.
   */
  private ModuleDefLoader() {
    this.classLoader = Thread.currentThread().getContextClassLoader();
    this.strategy = new LoadStrategy() {
      public void load(TreeLogger logger, String moduleName, ModuleDef moduleDef)
          throws UnableToCompleteException {
        nestedLoad(logger, moduleName, moduleDef);
      }
    };
  }

  /**
   * Constructs a {@link ModuleDefLoader} that loads a synthetic module.
   * 
   * @param inherits a set of modules to inherit from
   */
  private ModuleDefLoader(final String[] inherits) {
    this.classLoader = Thread.currentThread().getContextClassLoader();
    this.strategy = new LoadStrategy() {
      public void load(TreeLogger logger, String moduleName, ModuleDef moduleDef)
          throws UnableToCompleteException {
        for (String inherit : inherits) {
          TreeLogger branch = logger.branch(TreeLogger.TRACE,
              "Loading inherited module '" + inherit + "'", null);
          nestedLoad(branch, inherit, moduleDef);
        }
      }
    };
  }

  /**
   * Loads a new module into <code>moduleDef</code> as an included module.
   * 
   * @param logger Logs the process.
   * @param moduleName The module to load.
   * @param moduleDef The module to add the new module to.
   * @throws UnableToCompleteException
   */
  void nestedLoad(TreeLogger logger, String moduleName, ModuleDef moduleDef)
      throws UnableToCompleteException {

    if (alreadyLoadedModules.contains(moduleName)) {
      logger.log(TreeLogger.TRACE, "Module '" + moduleName
          + "' has already been loaded and will be skipped", null);
      return;
    } else {
      alreadyLoadedModules.add(moduleName);
    }

    // Find the specified module using the classpath.
    //
    String slashedModuleName = moduleName.replace('.', '/');
    String resName = slashedModuleName + ModuleDefLoader.GWT_MODULE_XML_SUFFIX;
    URL moduleURL = classLoader.getResource(resName);

    if (moduleURL != null) {
      try {
        moduleURL = FileLocator.resolve(moduleURL);
      } catch (IOException e) {
      }
      // prepare external form
      String externalForm = moduleURL.toExternalForm();
      // XXX <Instantiations
   	  externalForm = StringUtils.replace(externalForm, " ", "%20");
      externalForm = StringUtils.replace(externalForm, "file://Users/", "file:/Users/");
      // XXX >Instantiations
   	  // parse URL
      logger.log(TreeLogger.TRACE, "Module location: " + externalForm, null);
      try {
        moduleURL = new URL(externalForm);
        if ((!(externalForm.startsWith("jar:file")))
            && (!(externalForm.startsWith("zip:file")))
            && (!(externalForm.startsWith("http://")))
            && (!(externalForm.startsWith("ftp://")))) {
          File gwtXmlFile = new File(moduleURL.toURI());
          moduleDef.addGwtXmlFile(gwtXmlFile);
        }
      } catch (URISyntaxException e) {
        logger.log(TreeLogger.ERROR, "Error parsing URI " + externalForm, e);
        throw new UnableToCompleteException();
      } catch (MalformedURLException e) {
        logger.log(TreeLogger.ERROR, "Error creating URL " + externalForm, e);
        throw new UnableToCompleteException();
      }
    }
    if (moduleURL == null) {
      String msg = "Unable to find '"
          + resName
          + "' on your classpath; could be a typo, or maybe you forgot to include a classpath entry for source?";
      logger.log(TreeLogger.ERROR, msg, null);
      throw new UnableToCompleteException();
    }

    // Extract just the directory containing the module.
    //
    String moduleDir = "";
    int i = slashedModuleName.lastIndexOf('/');
    if (i != -1) {
      moduleDir = slashedModuleName.substring(0, i) + "/";
    }

    // Parse it.
    //
    Reader r = null;
    try {
      r = Util.createReader(logger, moduleURL);
      ModuleDefSchema schema = new ModuleDefSchema(logger, this, moduleName,
          moduleURL, moduleDir, moduleDef);
      ReflectiveParser.parse(logger, schema, r);
    } catch (Throwable e) {
      logger.log(TreeLogger.ERROR, "Unexpected error while processing XML", e);
      throw new UnableToCompleteException();
    } finally {
      Utility.close(r);
    }
  }

  /**
   * This method loads a module.
   * 
   * @param logger used to log the loading process
   * @param moduleName the name of the module
   * @return the module returned -- cannot be null
   * @throws UnableToCompleteException if module loading failed
   */
  private ModuleDef doLoadModule(TreeLogger logger, String moduleName)
      throws UnableToCompleteException {
    if (!ModuleDef.isValidModuleName(moduleName)) {
      logger.log(TreeLogger.ERROR, "Invalid module name: '" + moduleName + "'",
          null);
      throw new UnableToCompleteException();
    }

    ModuleDef moduleDef = new ModuleDef(moduleName);
    strategy.load(logger, moduleName, moduleDef);

    // Do any final setup.
    //
    moduleDef.normalize(logger);
    // XXX <Instantiations: ensure resources while loading module because this is the only time 
    // when our project class-loader is set as the current thread class-loader
    moduleDef.getResourcesOracle();
    {
    	// Add the "physical" module name: com.google.Module
    	addCachedLoadedModule(moduleName, moduleDef);
    	// Add the module's effective name: some.other.Module
    	addCachedLoadedModule(moduleDef.getName(), moduleDef);
    }
    // XXX >Instantiations
    // Add a mapping from the module's effective name to its physical name
    moduleEffectiveNameToPhysicalName.put(moduleDef.getName(), moduleName);
    return moduleDef;
  }

	// XXX <Instantiations
	private void addCachedLoadedModule(String moduleName, ModuleDef moduleDef) {
		String key = getModuleKey(moduleName);
		if (key != null) {
			loadedModules.put(key, moduleDef);
		}
	}
	/**
	 * @return the fully qualified name for module, for example:
	 * <pre>
	 *   com.mycompany.project.ImageViewer = file:/C:/eclipsePL/runtime-workspace2/_P1/src/com/mycompany/project/ImageViewer.gwt.xml
	 *   com.google.gwt.user.User = jar:file:/C:/gwt-windows-1.5.0/gwt-user.jar!/com/google/gwt/user/User.gwt.xml
	 * </pre>
	 */
	private static String getModuleKey(String moduleName) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String slashedModuleName = moduleName.replace('.', '/');
		String resName = slashedModuleName + ModuleDefLoader.GWT_MODULE_XML_SUFFIX;
		URL moduleURL = classLoader.getResource(resName);
		if (moduleURL != null) {
			try {
				moduleURL = FileLocator.resolve(moduleURL);
			} catch (IOException e) {
			}
			String externalForm = moduleURL.toExternalForm();
			return externalForm;
		}
		// no such module found
		return null;
	}
	// XXX >Instantiations
}
