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
package com.google.gdt.eclipse.designer.smartgwt.model.widgets;

import com.google.gdt.eclipse.designer.model.widgets.WidgetInfo;
import com.google.gdt.eclipse.designer.smart.model.CanvasInfo;
import com.google.gdt.eclipse.designer.smart.model.LayoutInfo;
import com.google.gdt.eclipse.designer.smartgwt.model.SmartGwtModelTest;

import org.eclipse.wb.internal.core.model.generic.FlowContainer;
import org.eclipse.wb.internal.core.model.generic.FlowContainerFactory;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

/**
 * Test <code>com.smartgwt.client.widgets.layout.VLayout</code>
 * 
 * @author scheglov_ke
 */
public class VLayoutTest extends SmartGwtModelTest {
  ////////////////////////////////////////////////////////////////////////////
  //
  // Exit zone :-) XXX
  //
  ////////////////////////////////////////////////////////////////////////////
  public void _test_exit() throws Exception {
    System.exit(0);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Tests
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_CREATE() throws Exception {
    CanvasInfo canvas =
        parseJavaInfo(
            "// filler filler filler",
            "public class Test extends Canvas {",
            "  public Test() {",
            "  }",
            "}");
    canvas.refresh();
    assertThat(canvas.getWidgets()).isEmpty();
    //
    LayoutInfo newLayout = createJavaInfo("com.smartgwt.client.widgets.layout.VLayout");
    canvas.command_absolute_CREATE(newLayout, null);
    assertEditor(
        "// filler filler filler",
        "public class Test extends Canvas {",
        "  public Test() {",
        "    {",
        "      VLayout layout = new VLayout();",
        "      addChild(layout);",
        "    }",
        "  }",
        "}");
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Flow container
  //
  ////////////////////////////////////////////////////////////////////////////
  public void test_flowContainer_CREATE() throws Exception {
    LayoutInfo layout =
        parseJavaInfo(
            "// filler filler filler",
            "public class Test extends VLayout {",
            "  public Test() {",
            "  }",
            "}");
    layout.refresh();
    // prepare FlowContainer
    FlowContainer flowContainer;
    {
      List<FlowContainer> flowContainers = new FlowContainerFactory(layout, true).get();
      assertThat(flowContainers).hasSize(1);
      flowContainer = flowContainers.get(0);
      assertFalse(flowContainer.isHorizontal());
    }
    // do create
    WidgetInfo newButton = createButton();
    assertTrue(flowContainer.validateComponent(newButton));
    flowContainer.command_CREATE(newButton, null);
    assertEditor(
        "// filler filler filler",
        "public class Test extends VLayout {",
        "  public Test() {",
        "    {",
        "      Button button = new Button();",
        "      addMember(button);",
        "    }",
        "  }",
        "}");
  }

  public void test_flowContainer_MOVE() throws Exception {
    LayoutInfo layout =
        parseJavaInfo(
            "public class Test extends VLayout {",
            "  public Test() {",
            "    {",
            "      Button button_1 = new Button();",
            "      addMember(button_1);",
            "    }",
            "    {",
            "      Button button_2 = new Button();",
            "      addMember(button_2);",
            "    }",
            "  }",
            "}");
    CanvasInfo button_1 = getJavaInfoByName("button_1");
    CanvasInfo button_2 = getJavaInfoByName("button_2");
    layout.refresh();
    //
    FlowContainer flowContainer = new FlowContainerFactory(layout, true).get().get(0);
    assertTrue(flowContainer.validateComponent(button_2));
    assertTrue(flowContainer.validateReference(button_1));
    flowContainer.command_MOVE(button_2, button_1);
    assertEditor(
        "public class Test extends VLayout {",
        "  public Test() {",
        "    {",
        "      Button button_2 = new Button();",
        "      addMember(button_2);",
        "    }",
        "    {",
        "      Button button_1 = new Button();",
        "      addMember(button_1);",
        "    }",
        "  }",
        "}");
  }
}