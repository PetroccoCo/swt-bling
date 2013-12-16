package com.readytalk.swt.widgets.notifications;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Enclosed.class)
public class PopOverShellIntegTest {
  @RunWith(Parameterized.class)
  public static class BubblePlacementTests {
    private static final String BUBBLE_TEXT = "Testing tooltip. It's quite concise, no?";

    static Display display;
    static Shell shell;
    static Button button;
    static Bubble bubble;
    static boolean initialized = false;

    @Before
    public void setUp() {
      if (needsInitialization()) {
        initialize();
      }
    }

    private static boolean needsInitialization() {
      if (!initialized || display.isDisposed() || shell.isDisposed()) {
        return true;
      } else {
        return false;
      }
    }

    @After
    public void tearDown() {
      shell.close();
    }

    @Parameterized.Parameters(name="{index}: BottomIsCutOff? {0}, RightIsCutOff? {1}")
    public static List<Object[]> data() {
      return Arrays.asList(new Object[][]{
              {false, false, getShellLocationNotCutOff(), Bubble.DEFAULT_DISPLAY_LOCATION, Bubble.DEFAULT_POINT_CENTERED},
              {false, true, getShellLocationForBubbleTextCutoff(BubbleTextCutoffPosition.RIGHT), Bubble.DEFAULT_DISPLAY_LOCATION, PopOverShell.PopOverShellPointCenteredOnParent.TOP_RIGHT_CORNER},
              {true, false, getShellLocationForBubbleTextCutoff(BubbleTextCutoffPosition.BOTTOM), PopOverShell.PopOverShellDisplayLocation.ABOVE_PARENT, Bubble.DEFAULT_POINT_CENTERED},
              {true, true, getShellLocationForBubbleTextCutoff(BubbleTextCutoffPosition.BOTTOM_RIGHT), PopOverShell.PopOverShellDisplayLocation.ABOVE_PARENT, PopOverShell.PopOverShellPointCenteredOnParent.TOP_RIGHT_CORNER}
      });
    }

    Point shellPoint;
    PopOverShell.PopOverShellDisplayLocation expectedDisplayLocation;
    PopOverShell.PopOverShellPointCenteredOnParent expectedCenteredOnParent;

    public BubblePlacementTests(boolean bottomIsCutOff, boolean rightIsCutOff, Point shellPoint,
                                PopOverShell.PopOverShellDisplayLocation expectedDisplayLocation, PopOverShell.PopOverShellPointCenteredOnParent expectedCenteredOnParent) {
      this.shellPoint = shellPoint;
      this.expectedDisplayLocation = expectedDisplayLocation;
      this.expectedCenteredOnParent = expectedCenteredOnParent;
    }

    @Test
    public void configureBubbleIfWouldBeCutOff_differingParameters_bubbleHasCorrectParams() {
      shell.setLocation(shellPoint);
      shell.open();
      bubble.show();

      assertEquals(bubble.popOverShellDisplayLocation, expectedDisplayLocation);
      assertEquals(bubble.popOverShellPointCenteredOnParent, expectedCenteredOnParent);
    }


    private enum BubbleTextCutoffPosition { BOTTOM, RIGHT, BOTTOM_RIGHT }
    private static Point getShellLocationForBubbleTextCutoff(BubbleTextCutoffPosition cutoffPosition) {
      Point appropriateShellLocation = null;

      if (needsInitialization()) {
        initialize();
      }

      Rectangle displayBounds = display.getClientArea();
      Rectangle buttonSize = button.getBounds();
      switch (cutoffPosition) {
        case BOTTOM:
          appropriateShellLocation = new Point(displayBounds.width / 2,
                  displayBounds.height - buttonSize.height);
          break;
        case RIGHT:
          appropriateShellLocation = new Point(displayBounds.width - buttonSize.width,
                  displayBounds.height / 2);
          break;
        case BOTTOM_RIGHT:
          appropriateShellLocation = new Point(displayBounds.width - buttonSize.width,
                  displayBounds.height - buttonSize.height);
          break;
      }

      return appropriateShellLocation;
    }

    private static Point getShellLocationNotCutOff() {
      return new Point(0, 0);
    }

    private static void initialize() {
      display = Display.getDefault();
      shell = new Shell(display);
      shell.setLayout(new FillLayout());
      Composite composite = new Composite(shell, SWT.NONE);
      composite.setLayout(new GridLayout());

      button = new Button(composite, SWT.PUSH);
      button.setText("Testing Button");

      bubble = Bubble.createBubble(button, BUBBLE_TEXT);
      button.setLayoutData(new GridData(SWT.LEFT, SWT.LEFT, true, true));
      shell.pack();
      shell.layout();

      initialized = true;
    }
  }
}
