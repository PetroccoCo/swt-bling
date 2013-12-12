package com.readytalk.swt.widgets.notifications;

import com.readytalk.swt.effects.FadeEffect;
import com.readytalk.swt.effects.FadeEffect.Fadeable;
import com.readytalk.swt.effects.FadeEffect.FadeEffectBuilder;
import com.readytalk.swt.effects.InvalidEffectArgumentException;
import com.readytalk.swt.helpers.AncestryHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import java.util.logging.Logger;

/**
 * Instances of this class represent contextual information about a UI element.<br/>
 * <br/>
 * Bubble will attempt to always be visible on screen.<br/>
 * If the default Bubble would appear off-screen, we will calculate a suitable location to appear.<br/>
 * <br/>
 * Bubble utilizes the system font, and provides a constructor to indicate use of a bolded font.<br/>
 * <br/>
 * Bubble will also break up lines that would be longer than 400 pixels when drawn.<br/>
 * You can short-circuit this logic by providing your own line-breaks with <code>\n</code> characters in the text.<br/>
 * We will never format your text if your provide your own formatting.<br/>
 */
public class Bubble extends Widget implements Fadeable {
  private static final Logger LOG = Logger.getLogger(Bubble.class.getName());

  public enum BubbleDisplayLocation { BELOW_PARENT, ABOVE_PARENT }
  public enum BubblePointCenteredOnParent { TOP_RIGHT_CORNER, TOP_LEFT_CORNER }

  static final int MAX_STRING_LENGTH = 400; //pixels
  private static final RGB BACKGROUND_COLOR = new RGB(74, 74, 74);
  private static final RGB TEXT_COLOR = new RGB(204, 204, 204);
  private static final int TEXT_HEIGHT_PADDING = 5; //pixels
  private static final int TEXT_WIDTH_PADDING = 10; //pixels
  private static final int BORDER_THICKNESS = 1; //pixels
  private static final int FADE_OUT_TIME = 200; //milliseconds
  private static final int FULLY_VISIBLE_ALPHA = 255; //fully opaque
  private static final int FULLY_HIDDEN_ALPHA = 0; //fully transparent
  static final BubbleDisplayLocation DEFAULT_DISPLAY_LOCATION = BubbleDisplayLocation.BELOW_PARENT;
  static final BubblePointCenteredOnParent DEFAULT_POINT_CENTERED = BubblePointCenteredOnParent.TOP_LEFT_CORNER;

  private boolean disableAutoHide;

  private Object fadeLock = new Object();

  private Listener listener;
  private String tooltipText;
  private BubbledItem bubbledItem;
  private Control parentControl;
  private Shell parentShell, tooltip;
  private Region tooltipRegion;

  private Rectangle containingRectangle;
  private Rectangle borderRectangle;
  private Color textColor;

  private Color backgroundColor;
  private Color borderColor;
  private Font boldFont;

  private Listener parentListener;
  BubbleDisplayLocation bubbleDisplayLocation = DEFAULT_DISPLAY_LOCATION;
  BubblePointCenteredOnParent bubblePointCenteredOnParent = DEFAULT_POINT_CENTERED;
  private boolean bubbleIsFullyConfigured = false;
  private boolean fadeEffectInProgress = false;

  /**
   * Creates and attaches a bubble to a component that implements <code>CustomElementDataProvider</code>.
   * This is a convenience constructor which assumes you do not want the Bubble text to appear
   * in a Bold font.
   *
   * @param customElementDataProvider The CustomElementDataProvider element that the Bubble provides contextual help about
   * @param text The text you want to appear in the Bubble
   * @throws IllegalArgumentException Thrown if the parentControl or text is <code>null</code>
   */
  public static Bubble createBubbleForCustomWidget(CustomElementDataProvider customElementDataProvider, String text, BubbleTag ... tags)
          throws IllegalArgumentException {
    return new Bubble(customElementDataProvider.getPaintedElement(), customElementDataProvider, text, tags);
  }

  /**
   * Creates and attaches a Bubble to a component that implements <code>CustomElementDataProvider</code>
   *
   * @param customElementDataProvider The CustomElementDataProvider element that the Bubble provides contextual help about
   * @param text The text you want to appear in the Bubble
   * @param useBoldFont Whether or not we should draw the Bubble's text in a bold version of the system font
   * @throws IllegalArgumentException Thrown if the parentControl or text is <code>null</code>
   */
  public static Bubble createBubbleForCustomWidget(CustomElementDataProvider customElementDataProvider, String text, boolean useBoldFont, BubbleTag ... tags)
          throws IllegalArgumentException {
    return new Bubble(customElementDataProvider.getPaintedElement(), customElementDataProvider, text, useBoldFont, tags);
  }

  /**
   * Creates and attaches a Bubble to any SWT Control (or descendant).
   * This is a convenience constructor which assumes you do not want the Bubble text to appear
   * in a Bold font.
   *
   * @param parentControl The parent element that the Bubble provides contextual help about
   * @param text The text you want to appear in the Bubble
   * @throws IllegalArgumentException Thrown if the parentControl or text is <code>null</code>
   */
  public static Bubble createBubble(Control parentControl, String text, BubbleTag ... tags) {
    return new Bubble(parentControl, null, text, false, tags);
  }

  /**
   * Creates and attaches a Bubble to any SWT Control (or descendant).
   *
   * @param parentControl The parent element that the Bubble provides contextual help about
   * @param text The text you want to appear in the Bubble
   * @param useBoldFont Whether or not we should draw the Bubble's text in a bold version of the system font
   * @throws IllegalArgumentException Thrown if the parentControl or text is <code>null</code>
   */
  public static Bubble createBubble(Control parentControl, String text, boolean useBoldFont, BubbleTag ... tags)
          throws IllegalArgumentException {
    return new Bubble(parentControl, null, text, useBoldFont, tags);
  }

  private Bubble(Control parentControl, CustomElementDataProvider customElementDataProvider, String text, BubbleTag ... tags) {
    this(parentControl, customElementDataProvider, text, false, tags);
  }

  private Bubble(Control parentControl, CustomElementDataProvider customElementDataProvider, String text, boolean useBoldFont, BubbleTag ... tags)
          throws IllegalArgumentException {
    super(parentControl, SWT.NONE);

    if (customElementDataProvider != null) {
      bubbledItem = new BubbledItem(customElementDataProvider);
    } else {
      bubbledItem = new BubbledItem(parentControl);
    }

    if (text == null) {
      throw new IllegalArgumentException("Bubble text cannot be null.");
    }

    this.parentControl = parentControl;
    this.tooltipText = maybeBreakLines(text);
    parentShell = AncestryHelper.getShellFromControl(bubbledItem.getControl());

    // Remember to clean up after yourself onDispose.
    backgroundColor = new Color(getDisplay(), BACKGROUND_COLOR);
    textColor = new Color(getDisplay(), TEXT_COLOR);
    borderColor = textColor;
    if (useBoldFont) {
      Font font = getDisplay().getSystemFont();
      FontData fontData = font.getFontData()[0];
      boldFont = new Font(getDisplay(), fontData.getName(), fontData.getHeight(), SWT.BOLD);
    }

    tooltip = new Shell(parentShell, SWT.ON_TOP | SWT.NO_TRIM);
    tooltip.setBackground(backgroundColor);

    attachListeners();
    registerBubble(bubbledItem, tags);
  }

  private void registerBubble(BubbledItem bubbledItem, BubbleTag ... tags) {
    BubbleRegistry bubbleRegistry = BubbleRegistry.getInstance();

    if (bubbledItem.getCustomElementDataProvider() != null) {
      bubbleRegistry.register(bubbledItem.customElementDataProvider, this, tags);
    } else {
      bubbleRegistry.register(bubbledItem.getControl(), this, tags);
    }
  }

  /**
   * Shows the Bubble in a suitable location relative to the parent component.
   */
  public void show() {
    Point textExtent = getTextSize(tooltipText);

    tooltipRegion = new Region();
    containingRectangle = calculateContainingRectangleRegion(textExtent);
    tooltipRegion.add(containingRectangle);

    borderRectangle = calculateBorderRectangle(containingRectangle);

    Point location = getShellDisplayLocation(parentShell, bubbledItem, bubbleDisplayLocation,
            bubblePointCenteredOnParent, containingRectangle);

    while (!bubbleIsFullyConfigured) {
      bubbleIsFullyConfigured = configureBubbleIfWouldBeCutOff(parentShell.getDisplay().getClientArea(),
              location, containingRectangle);
      location = getShellDisplayLocation(parentShell, bubbledItem, bubbleDisplayLocation,
              bubblePointCenteredOnParent, containingRectangle);
    }

    tooltip.setRegion(tooltipRegion);
    tooltip.setSize(containingRectangle.width, containingRectangle.height);
    tooltip.setLocation(location);
    tooltip.setAlpha(FULLY_VISIBLE_ALPHA);
    tooltip.setVisible(true);
  }

  /**
   * Add tags to a previously created Bubble. <br/>
   * <br/>
   * Tags can be shown by calling <code>BubbleRegistry.getInstance.showBubblesByTag(BubbleTag tag)</code>
   * @param bubbleTags Tags to associate with this Bubble
   */
  public void addTags(BubbleTag ... bubbleTags) {
    BubbleRegistry.getInstance().addTags(bubbledItem.getControlOrCustomElement(), bubbleTags);
  }

  /**
   * Remove tags from a previously created Bubble.
   * @param bubbleTags Tags to un-associate with this Bubble
   */
  public void removeTags(BubbleTag ... bubbleTags) {
    BubbleRegistry bubbleRegistry = BubbleRegistry.getInstance();
    BubbleRegistry.BubbleRegistrant registrant = bubbleRegistry.findRegistrant(bubbledItem.getControlOrCustomElement());
    BubbleRegistry.getInstance().removeTags(registrant, bubbleTags);
  }

  /**
   * Remove the Bubble from the Component.<br/>
   * Note, this will also dispose the Bubble. Future interactions with this Bubble will result in Widget Diposed exceptions.
   */
  public void deactivateBubble() {
    BubbleRegistry.getInstance().unregister(bubbledItem.getControlOrCustomElement());
    dispose();
  }

  /**
   * Fades the Bubble off the screen.
   */
  public void fadeOut() {
    if (fadeEffectInProgress) {
      return;
    }

    try {
      FadeEffect fade = new FadeEffectBuilder().
                            setFadeable(this).
                            setFadeCallback(new BubbleFadeCallback()).
                            setFadeTimeInMilliseconds(FADE_OUT_TIME).
                            setCurrentAlpha(FULLY_VISIBLE_ALPHA).
                            setTargetAlpha(FULLY_HIDDEN_ALPHA).build();

      fade.startEffect();
      fadeEffectInProgress = true;
    } catch (InvalidEffectArgumentException e) {
      LOG.warning("Invalid argument provided to FadeEffect.");
    }
  }

  /**
   * Returns whether the Bubble is currently fading from the screen.
   * Calls to <code>Bubble.isVisible()</code> will return true while the Bubble is dismissing.
   * @return Whether or not the Bubble is currently fading from the screen
   */
  public boolean getIsFadeEffectInProgress() {
    return fadeEffectInProgress;
  }

  /**
   * Returns whether the Bubble is currently visible on screen.
   * Note: If you utilize <code>Bubble.fadeOut()</code>, this method will return true while it's fading.
   * To determine if it's fading out, call <code>Bubble.getIsFadeEffectInProgress</code>
   * @return Visibility state of the Bubble
   */
  public boolean isVisible() {
    return tooltip.isVisible();
  }

  private void hide() {
    tooltip.setVisible(false);
    resetState();
  }

  /**
   * Returns a boolean describing whether or not auto-hide functionality is disabled for this Bubble.
   * @return Whether or not auto-hide functionality is disabled
   */
  protected boolean isDisableAutoHide() {
    return disableAutoHide;
  }

  /**
   * Tells the Bubble whether or not it should auto-hide when the user mouses off the Bubble'd item.
   * @param disableAutoHide whether or not auto-hide should be disabled
   */
  protected void setDisableAutoHide(boolean disableAutoHide) {
    this.disableAutoHide = disableAutoHide;
  }

  private void attachListeners() {
    listener = new Listener() {
      public void handleEvent(Event event) {
        switch (event.type) {
          case SWT.Dispose:
            onDispose(event);
            break;
          case SWT.Paint:
            onPaint(event);
            break;
          case SWT.MouseDown:
            onMouseDown(event);
            break;
          default:
            break;
        }
      }
    };
    addListener(SWT.Dispose, listener);
    tooltip.addListener(SWT.Paint, listener);
    tooltip.addListener(SWT.MouseDown, listener);

    parentListener = new Listener() {
      public void handleEvent(Event event) {
        dispose();
      }
    };
    parentControl.addListener(SWT.Dispose, parentListener);
    addAccessibilityHooks(parentControl);
  }

  boolean configureBubbleIfWouldBeCutOff(Rectangle displayBounds, Point locationRelativeToDisplay, Rectangle containingRectangle) {
    if (configureBubbleIfBottomCutOff(displayBounds, locationRelativeToDisplay, containingRectangle)) {
      return false;
    } else if  (configureBubbleIfRightmostTextCutOff(displayBounds, locationRelativeToDisplay, containingRectangle)) {
      return false;
    }

    return true;
  }

  boolean configureBubbleIfBottomCutOff(Rectangle displayBounds, Point locationRelativeToDisplay, Rectangle containingRectangle) {
    Point lowestYPosition = new Point(locationRelativeToDisplay.x, locationRelativeToDisplay.y + containingRectangle.height);

    if (!displayBounds.contains(lowestYPosition)) {
      bubbleDisplayLocation = BubbleDisplayLocation.ABOVE_PARENT;
      return true;
    } else {
      return false;
    }
  }

  boolean configureBubbleIfRightmostTextCutOff(Rectangle displayBounds, Point locationRelativeToDisplay, Rectangle containingRectangle) {
    Point farthestXPosition = new Point(locationRelativeToDisplay.x + containingRectangle.width, locationRelativeToDisplay.y);

    if (!displayBounds.contains(farthestXPosition)) {
      bubblePointCenteredOnParent = BubblePointCenteredOnParent.TOP_RIGHT_CORNER;
      return true;
    } else {
      return false;
    }
  }

  private Point getShellDisplayLocation(Shell parentShell, BubbledItem bubbledItem, BubbleDisplayLocation aboveOrBelow,
                                        BubblePointCenteredOnParent bubblePointCenteredOnParent, Rectangle bubbleRectangle) {
    Point bubbledItemSize = bubbledItem.getSize();
    Point parentLocationRelativeToDisplay = parentShell.getDisplay().map(parentShell, null, bubbledItem.getLocation());
    Point appropriateDisplayLocation = new Point(0, 0);

    switch (aboveOrBelow) {
      case ABOVE_PARENT:
        appropriateDisplayLocation.y = parentLocationRelativeToDisplay.y - bubbleRectangle.height;
        break;
      case BELOW_PARENT:
        appropriateDisplayLocation.y = parentLocationRelativeToDisplay.y + bubbledItemSize.y;
        break;
    }

    switch(bubblePointCenteredOnParent) {
      case TOP_LEFT_CORNER:
        appropriateDisplayLocation.x = parentLocationRelativeToDisplay.x + (bubbledItemSize.x / 2);
        break;
      case TOP_RIGHT_CORNER:
        appropriateDisplayLocation.x = parentLocationRelativeToDisplay.x - bubbleRectangle.width + (bubbledItemSize.x / 2);
        break;
    }

    return appropriateDisplayLocation;
  }

  private Rectangle calculateContainingRectangleRegion(Point textExtent) {
    return new Rectangle(0, 0,
            textExtent.x + TEXT_WIDTH_PADDING,
            textExtent.y + TEXT_HEIGHT_PADDING);
  }

  private Rectangle calculateBorderRectangle(Rectangle containingRectangle) {
    return new Rectangle(containingRectangle.x,
            containingRectangle.y,
            containingRectangle.width - BORDER_THICKNESS,
            containingRectangle.height - BORDER_THICKNESS);
  }

  private void resetState() {
    bubbleDisplayLocation = DEFAULT_DISPLAY_LOCATION;
    bubblePointCenteredOnParent = DEFAULT_POINT_CENTERED;
    bubbleIsFullyConfigured = false;
    fadeEffectInProgress = false;
    disableAutoHide = false;
  }

  public void checkSubclass() {
    //no-op
  }

  private void onDispose(Event event) {
    parentControl.removeListener(SWT.Dispose, parentListener);
    removeListener(SWT.Dispose, listener);
    notifyListeners(SWT.Dispose, event);
    event.type = SWT.None;

    backgroundColor.dispose();
    textColor.dispose();
    tooltip.dispose();
    tooltip = null;
    if (tooltipRegion != null) {
      tooltipRegion.dispose();
    }
    tooltipRegion = null;
    if (boldFont != null) {
      boldFont.dispose();
    }
    boldFont = null;
  }

  private void onPaint(Event event) {
    GC gc = event.gc;

    gc.setForeground(borderColor);
    gc.setLineWidth(BORDER_THICKNESS);
    gc.drawRectangle(borderRectangle);

    if (boldFont != null) {
      gc.setFont(boldFont);
    }

    gc.setForeground(textColor);
    gc.drawText(tooltipText, containingRectangle.x + (TEXT_WIDTH_PADDING / 2), containingRectangle.y + (TEXT_HEIGHT_PADDING / 2));
  }

  private void onMouseDown(Event event) {
    if(!isDisableAutoHide()) {
      hide();
    }
  }

  private void addAccessibilityHooks(Control parentControl) {
    parentControl.getAccessible().addAccessibleListener(new AccessibleAdapter() {
      public void getHelp(AccessibleEvent e) {
        e.result = tooltipText;
      }
    });
  }

  String maybeBreakLines(String rawString) {
    GC gc = new GC(getDisplay());
    if (boldFont != null) {
      gc.setFont(boldFont);
    }

    String returnString;
    Point textExtent = gc.textExtent(rawString, SWT.DRAW_DELIMITER);
    if (textExtent.x > MAX_STRING_LENGTH && !rawString.contains("\n")) {
      StringBuilder sb = new StringBuilder();
      String[] words = rawString.split(" ");
      int spaceInPixels = gc.textExtent(" ").x;

      int currentPixelCount = 0;
      for (String word : words) {
        int wordPixelWidth = gc.textExtent(word).x;
        if (currentPixelCount + wordPixelWidth + spaceInPixels < MAX_STRING_LENGTH) {
          sb.append(word);
          sb.append(" ");
          currentPixelCount += wordPixelWidth + spaceInPixels;
        } else {
          sb.append("\n");
          sb.append(word);
          sb.append(" ");
          currentPixelCount = wordPixelWidth + spaceInPixels;
        }
      }

      returnString = sb.toString();
    } else {
      returnString = rawString;
    }

    gc.dispose();
    return returnString;
  }

  private Point getTextSize(String text) {
    GC gc = new GC(getDisplay());
    if (boldFont != null) {
      gc.setFont(boldFont);
    }

    Point textExtent = gc.textExtent(text, SWT.DRAW_DELIMITER);
    gc.dispose();
    return textExtent;
  }

  private class BubbledItem {
    private Control control;
    private CustomElementDataProvider customElementDataProvider;

    public BubbledItem(Control control) {
      this.control = control;
    }

    public BubbledItem(CustomElementDataProvider customElementDataProvider) {
      this.customElementDataProvider = customElementDataProvider;
    }

    Point getSize() {
      if (control != null) {
        return control.getSize();
      } else {
        return customElementDataProvider.getSize();
      }
    }

    Point getLocation() {
      if (control != null) {
        return control.getLocation();
      } else {
        return customElementDataProvider.getLocation();
      }
    }

    Control getControl() {
      if (control != null) {
        return control;
      } else {
        return customElementDataProvider.getPaintedElement();
      }
    }

    Object getControlOrCustomElement() {
      if (customElementDataProvider != null) {
        return customElementDataProvider;
      } else {
        return control;
      }
    }

    CustomElementDataProvider getCustomElementDataProvider() {
      return customElementDataProvider;
    }
  }

  /**
   * Implemented as part of Fadeable. <br/>
   * Users should not interact directly invoke this method.
   */
  public boolean fadeComplete(int targetAlpha) {
    synchronized (fadeLock) {
      if (tooltip.getAlpha() == targetAlpha) {
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * Implemented as part of Fadeable. <br/>
   * Users should not interact directly invoke this method.
   */
  public void fade(int alpha) {
    synchronized (fadeLock) {
      tooltip.setAlpha(alpha);
    }
  }

  private class BubbleFadeCallback implements FadeEffect.FadeCallback {
    public void fadeComplete() {
      hide();
    }
  }
}