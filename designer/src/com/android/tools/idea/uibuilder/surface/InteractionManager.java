/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.surface;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_MARGIN;
import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_RADIUS;

/**
 * The {@linkplain InteractionManager} is is the central manager of interactions; it is responsible
 * for recognizing when particular interactions should begin and terminate. It
 * listens to the drag, mouse and keyboard systems to find out when to start
 * interactions and in order to update the interactions along the way.
 */
public class InteractionManager {
  private static final Logger LOG = Logger.getInstance(InteractionManager.class);

  /** The canvas which owns this {@linkplain InteractionManager}. */
  @NonNull
  private final DesignSurface mySurface;

  /** The currently executing {@link Interaction}, or null. */
  @Nullable
  private Interaction myCurrentInteraction;

  /**
   * The list of overlays associated with {@link #myCurrentInteraction}. Will be
   * null before it has been initialized lazily by the paint routine (the
   * initialized value can never be null, but it can be an empty collection).
   */
  @Nullable
  private List<Layer> myLayers;

  /**
   * Most recently seen mouse position (x coordinate). We keep a copy of this
   * value since we sometimes need to know it when we aren't told about the
   * mouse position (such as when a keystroke is received, such as an arrow
   * key in order to tweak the current drop position)
   */
  @SwingCoordinate
  protected int myLastMouseX;

  /**
   * Most recently seen mouse position (y coordinate). We keep a copy of this
   * value since we sometimes need to know it when we aren't told about the
   * mouse position (such as when a keystroke is received, such as an arrow
   * key in order to tweak the current drop position)
   */
  @SwingCoordinate
  protected int myLastMouseY;

  /**
   * Most recently seen mouse mask. We keep a copy of this since in some
   * scenarios (such as on a drag interaction) we don't get access to it.
   */
  protected int myLastStateMask;

  /**
   * Listener for mouse motion, click and keyboard events.
   */
  private Listener myListener;

  /** Drop target installed by this manager */
  private DropTarget myDropTarget;

  /**
   * Constructs a new {@link InteractionManager} for the given
   * {@link DesignSurface}.
   *
   * @param surface The surface which controls this {@link InteractionManager}
   */
  public InteractionManager(@NonNull DesignSurface surface) {
    mySurface = surface;
  }

  /**
   * Returns the canvas associated with this {@linkplain InteractionManager}.
   *
   * @return The {@link DesignSurface} associated with this {@linkplain InteractionManager}.
   *         Never null.
   */
  @NonNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  /**
   * Returns the current {@link Interaction}, if one is in progress, and otherwise returns
   * null.
   *
   * @return The current interaction or null.
   */
  @Nullable
  public Interaction getCurrentInteraction() {
    return myCurrentInteraction;
  }

  /**
   * Registers all the listeners needed by the {@link InteractionManager}.
   */
  public void registerListeners() {
    assert myListener == null;
    myListener = new Listener();
    JComponent layeredPane = mySurface.getLayeredPane();
    layeredPane.addMouseMotionListener(myListener);
    layeredPane.addMouseListener(myListener);
    layeredPane.addKeyListener(myListener);

    myDropTarget = new DropTarget(mySurface.getLayeredPane(), DnDConstants.ACTION_COPY_OR_MOVE, myListener, true, null);
  }

  /**
   * Unregisters all the listeners previously registered by
   * {@link #registerListeners}.
   */
  public void unregisterListeners() {
    myDropTarget.removeDropTargetListener(myListener);
  }

  /**
   * Starts the given interaction.
   */
  private void startInteraction(@SwingCoordinate int x, @SwingCoordinate int y, @Nullable Interaction interaction,
                                int modifiers) {
    if (myCurrentInteraction != null) {
      finishInteraction(x, y, modifiers, true);
      assert myCurrentInteraction == null;
    }

    if (interaction != null) {
      myCurrentInteraction = interaction;
      myCurrentInteraction.begin(x, y, modifiers);
      myLayers = interaction.createOverlays();
    }
  }

  /** Returns the currently active overlays, if any */
  @Nullable
  public List<Layer> getLayers() {
    return myLayers;
  }

  /**
   * Updates the current interaction, if any, for the given event.
   */
  private void updateMouse(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.update(x, y, myLastStateMask);
    }
  }

  /**
   * Finish the given interaction, either from successful completion or from
   * cancellation.
   *
   * @param x         The most recent mouse x coordinate applicable to the new
   *                  interaction, in Swing coordinates.
   * @param y         The most recent mouse y coordinate applicable to the new
   *                  interaction, in Swing coordinates.
   * @param modifiers The most recent modifier key state
   * @param canceled  True if and only if the interaction was canceled.
   */
  private void finishInteraction(@SwingCoordinate int x, @SwingCoordinate int y, int modifiers, boolean canceled) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.end(x, y, modifiers, canceled);
      if (myLayers != null) {
        for (Layer layer : myLayers) {
          //noinspection SSBasedInspection
          layer.dispose();
        }
        myLayers = null;
      }
      myCurrentInteraction = null;
      myLastStateMask = 0;
      updateCursor(x, y);
      mySurface.repaint();
    }
  }

  /**
   * Update the cursor to show the type of operation we expect on a mouse press:
   * <ul>
   * <li>Over a selection handle, show a directional cursor depending on the position of
   * the selection handle
   * <li>Over a widget, show a move (hand) cursor
   * <li>Otherwise, show the default arrow cursor
   * </ul>
   */
  void updateCursor(@SwingCoordinate int x, @SwingCoordinate int y) {
    // We don't hover on the root since it's not a widget per see and it is always there.
    ScreenView screenView = mySurface.getScreenView(x, y);
    if (screenView == null) {
      mySurface.setCursor(null);
      return;
    }
    SelectionModel selectionModel = screenView.getSelectionModel();
    if (!selectionModel.isEmpty()) {
      int mx = Coordinates.getAndroidX(screenView, x);
      int my = Coordinates.getAndroidY(screenView, y);
      int max = Coordinates.getAndroidDimension(screenView, PIXEL_RADIUS + PIXEL_MARGIN);
      SelectionHandle handle = selectionModel.findHandle(mx, my, max);
      if (handle != null) {
        Cursor cursor = handle.getCursor();
        if (cursor != mySurface.getCursor()) {
          mySurface.setCursor(cursor);
        }
        return;
      }

      // See if it's over a selected view
      NlComponent component = selectionModel.findComponent(mx, my);
      if (component == null || component.isRoot()) {
        // Finally pick any unselected component in the model under the cursor
        // Finally pick any unselected component in the model under the cursor
        component = screenView.getModel().findLeafAt(mx, my, false);
      }

      if (component != null && !component.isRoot()) {
        Cursor cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        if (cursor != mySurface.getCursor()) {
          mySurface.setCursor(cursor);
        }
        return;
      }
    }

    mySurface.setCursor(null);
  }

  /**
   * Helper class which implements the {@link MouseMotionListener},
   * {@link MouseListener} and {@link KeyListener} interfaces.
   */
  private class Listener implements MouseMotionListener, MouseListener, KeyListener, DropTargetListener {

    // --- Implements MouseListener ----

    @Override
    public void mouseClicked(@NonNull MouseEvent event) {
      if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
        // Warp to the text editor and show the corresponding XML for the
        // double-clicked widget
        int x = event.getX();
        int y = event.getY();
        ScreenView screenView = mySurface.getScreenView(x, y);
        if (screenView != null) {
          NlComponent component = Coordinates.findComponent(screenView, x, y);
          if (component != null) {
            PsiNavigateUtil.navigate(component.getTag());
          }
        }
      }
    }

    @Override
    public void mousePressed(@NonNull MouseEvent event) {
      if(event.getID() == MouseEvent.MOUSE_PRESSED){
        mySurface.getLayeredPane().requestFocusInWindow();
      }

      myLastMouseX = event.getX();
      myLastMouseY = event.getY();
      myLastStateMask = event.getModifiersEx();

      // Not yet used. Should be, for Mac and Linux.
    }

    @Override
    public void mouseReleased(@NonNull MouseEvent event) {
      //ControlPoint mousePos = ControlPoint.create(mySurface, e);

      int x = event.getX();
      int y = event.getY();
      int modifiers = event.getModifiersEx();
      if (myCurrentInteraction == null) {
        // Just a click, select
        ScreenView screenView = mySurface.getScreenView(x, y);
        if (screenView == null) {
          return;
        }
        SelectionModel selectionModel = screenView.getSelectionModel();
        NlComponent component = Coordinates.findComponent(screenView, x, y);

        if (component == null) {
          // Clicked component resize handle?
          int mx = Coordinates.getAndroidX(screenView, x);
          int my = Coordinates.getAndroidY(screenView, y);
          int max = Coordinates.getAndroidDimension(screenView, PIXEL_RADIUS + PIXEL_MARGIN);
          SelectionHandle handle = selectionModel.findHandle(mx, my, max);
          if (handle != null) {
            component = handle.component;
          }
        }

        List<NlComponent> components = component != null ? Collections.singletonList(component) : Collections.<NlComponent>emptyList();
        screenView.getSelectionModel().setSelection(components);
        mySurface.repaint();
      }
      if (myCurrentInteraction == null) {
        updateCursor(x, y);
      } else {
        finishInteraction(x, y, modifiers, false);
      }
      mySurface.repaint();
    }

    @Override
    public void mouseEntered(@NonNull MouseEvent event) {
    }

    @Override
    public void mouseExited(@NonNull MouseEvent event) {
    }

    // --- Implements MouseMotionListener ----

    @Override
    public void mouseDragged(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      if (myCurrentInteraction != null) {
        myLastMouseX = x;
        myLastMouseY = y;
        myLastStateMask = event.getModifiersEx();
        myCurrentInteraction.update(myLastMouseX, myLastMouseY, myLastStateMask);
      } else {
        x = myLastMouseX; // initiate the drag from the mousePress location, not the point we've dragged to
        y = myLastMouseY;
        int modifiers = event.getModifiersEx();
        boolean toggle = (modifiers & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK)) != 0;
        ScreenView screenView = mySurface.getScreenView(x, y);
        if (screenView == null) {
          return;
        }
        Interaction interaction;
        SelectionModel selectionModel = screenView.getSelectionModel();
        // Dragging on top of a selection handle: start a resize operation

        int ax = Coordinates.getAndroidX(screenView, x);
        int ay = Coordinates.getAndroidY(screenView, y);
        int max = Coordinates.getAndroidDimension(screenView, PIXEL_RADIUS + PIXEL_MARGIN);
        SelectionHandle handle = selectionModel.findHandle(ax, ay, max);
        if (handle != null) {
          interaction = new ResizeInteraction(screenView, handle.component, handle);
        } else {
          NlModel model = screenView.getModel();
          NlComponent component = model.findLeafAt(ax, ay, false);
          if (component == null || component.isRoot()) {
            // Dragging on the background/root view: start a marquee selection
            interaction = new MarqueeInteraction(screenView, toggle);
          }
          else {
            List<NlComponent> dragged;
            // Dragging over a non-root component: move the set of components (if the component dragged over is
            // part of the selection, drag them all, otherwise drag just this component)
            if (selectionModel.isSelected(component)) {
              dragged = Lists.newArrayList();

              // Make sure the primary is the first element
              NlComponent primary = selectionModel.getPrimary();
              if (primary != null) {
                if (primary.isRoot()) {
                  primary = null;
                } else {
                  dragged.add(primary);
                }
              }

              for (NlComponent selected : selectionModel.getSelection()) {
                if (!selected.isRoot() && selected != primary) {
                  dragged.add(selected);
                }
              }
            }
            else {
              dragged = Collections.singletonList(component);
            }
            interaction = new DragDropInteraction(mySurface, dragged);
          }
        }
        startInteraction(x, y, interaction, modifiers);
      }
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      myLastMouseX = x;
      myLastMouseY = y;
      myLastStateMask = event.getModifiersEx();

      if ((myLastStateMask & InputEvent.BUTTON1_DOWN_MASK) != 0) {
        if (myCurrentInteraction != null) {
          updateMouse(x, y);
          mySurface.repaint();
        }
      } else {
        updateCursor(x, y);
      }
    }

    // --- Implements KeyListener ----

    @Override
    public void keyTyped(KeyEvent event) {
    }

    @Override
    public void keyPressed(KeyEvent event) {
      myLastStateMask = event.getModifiersEx();
      // Workaround for the fact that in keyPressed the current state
      // mask is not yet updated
      int keyCode = event.getKeyCode();
      char keyChar = event.getKeyChar();
      if (keyCode == KeyEvent.VK_SHIFT) {
        myLastStateMask |= InputEvent.SHIFT_MASK;
      }
      if (keyCode == KeyEvent.VK_META) {
        myLastStateMask |= InputEvent.META_MASK;
      }
      if (keyCode == KeyEvent.VK_CONTROL) {
        myLastStateMask |= InputEvent.CTRL_MASK;
      }

      // Give interactions a first chance to see and consume the key press
      if (myCurrentInteraction != null) {
        // unless it's "Escape", which cancels the interaction
        if (keyCode == KeyEvent.VK_ESCAPE) {
          finishInteraction(myLastMouseX, myLastMouseY, myLastStateMask, true);
          return;
        }

        if (myCurrentInteraction.keyPressed(event)) {
          return;
        }
      }

      // Fall back to canvas actions for the key press
      //mySurface.handleKeyPressed(e);

      if (keyChar == '1') {
        mySurface.zoomActual();
      } else if (keyChar == 'r') {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        if (screenView != null) {
          screenView.getModel().requestRender();
        }
        mySurface.zoomIn();
      } else if (keyChar == '+') {
        mySurface.zoomIn();
      } else if (keyChar == 'b') {
        DesignSurface.ScreenMode nextMode = mySurface.getScreenMode().next();
        mySurface.setScreenMode(nextMode);
      } else if (keyChar == '-') {
        mySurface.zoomOut();
      } else if (keyChar == '0') {
        mySurface.zoomToFit();
      } else if (keyChar == 'd') {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        if (screenView != null) {
          screenView.switchDevice();
        }
      } else if (keyChar == 'o') {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        if (screenView != null) {
          screenView.toggleOrientation();
        }
      } else if (keyChar == 'f') {
        mySurface.toggleDeviceFrames();
      } else if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        if (screenView != null) {
          SelectionModel model = screenView.getSelectionModel();
          if (!model.isEmpty()) {
            List<NlComponent> selection = model.getSelection();
            screenView.getModel().delete(selection);
          }
        }
      }
    }

    @Override
    public void keyReleased(KeyEvent event) {
      myLastStateMask = event.getModifiersEx();
      // Workaround for the fact that in keyPressed the current state
      // mask is not yet updated
      if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
        myLastStateMask |= InputEvent.SHIFT_MASK;
      }
      if (event.getKeyCode() == KeyEvent.VK_META) {
        myLastStateMask |= InputEvent.META_MASK;
      }
      if (event.getKeyCode() == KeyEvent.VK_CONTROL) {
        myLastStateMask |= InputEvent.CTRL_MASK;
      }

      if (myCurrentInteraction != null) {
        myCurrentInteraction.keyReleased(event);
      }
    }

    // ---- Implements DropTargetListener ----

    @Override
    public void dragEnter(DropTargetDragEvent event) {
      if (myCurrentInteraction == null) {
        Point location = event.getLocation();
        myLastMouseX = location.x;
        myLastMouseY = location.y;

        for (DataFlavor flavor : event.getCurrentDataFlavors()) {
          if (flavor.equals(ItemTransferable.DESIGNER_FLAVOR) ||
              flavor.getMimeType().startsWith("text/plain;") || flavor.getMimeType().equals("text/plain")) {
            event.acceptDrag(DnDConstants.ACTION_COPY);
            ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
            if (screenView == null) {
              continue;
            }
            final NlModel model = screenView.getModel();
            try {
              DnDTransferItem item = getTransferItem(event.getTransferable(), true /* allow placeholders */);
              DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
              InsertType insertType = model.determineInsertType(dragType, item, true /* preview */);
              List<NlComponent> dragged = model.createComponents(screenView, item, insertType);
              if (dragged == null) {
                continue;
              }
              int yOffset = 0;
              for (NlComponent component : dragged) {
                // todo: place the components like they were originally placed?
                component.x = Coordinates.getAndroidX(screenView, myLastMouseX) - component.w / 2;
                component.y = Coordinates.getAndroidY(screenView, myLastMouseY) - component.h / 2 + yOffset;
                yOffset += component.h;
              }
              DragDropInteraction interaction = new DragDropInteraction(mySurface, dragged);
              interaction.setType(DragType.COPY);
              startInteraction(myLastMouseX, myLastMouseY, interaction, 0);
              break;
            }
            catch (Exception ignore) {
              LOG.debug(ignore);
            }
          }
        }
      }
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
      Point location = event.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;
      if (myCurrentInteraction instanceof DragDropInteraction) {
        myCurrentInteraction.update(myLastMouseX, myLastMouseY, myLastStateMask);
      }

      for (DataFlavor flavor : event.getCurrentDataFlavors()) {
        if (flavor.equals(ItemTransferable.DESIGNER_FLAVOR) || String.class == flavor.getRepresentationClass()) {
          event.acceptDrag(DnDConstants.ACTION_COPY);
          return;
        }
      }
      event.rejectDrag();
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

    @Override
    public void dragExit(DropTargetEvent event) {
      if (myCurrentInteraction instanceof DragDropInteraction) {
        finishInteraction(myLastMouseX, myLastMouseY, myLastStateMask, true);
      }
    }

    @Override
    public void drop(final DropTargetDropEvent event) {
      Point location = event.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;

      final ScreenView screenView = mySurface.getScreenView(location.x, location.y);
      if (screenView == null) {
        event.rejectDrop();
        return;
      }

      try {
        event.acceptDrop(DnDConstants.ACTION_MOVE);
        final NlModel model = screenView.getModel();
        final Project project = model.getFacet().getModule().getProject();
        final XmlFile file = model.getFile();
        WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, "Drop", file) {
          @Override
          protected void run(@NonNull Result<Void> result) throws Throwable {
            if (myCurrentInteraction instanceof DragDropInteraction) {
              DragDropInteraction dragDrop = (DragDropInteraction)myCurrentInteraction;
              List<NlComponent> draggedComponents = dragDrop.getDraggedComponents();
              NlComponent component = draggedComponents.size() == 1 ? draggedComponents.get(0) : null;
              boolean dropCancelled = true;
              if (component != null) {
                if (component.getTag().getName().equals("placeholder")) {
                  // If we were unable to read the transfer data on dragEnter, fix it here:
                  final DnDTransferItem item = getTransferItem(event.getTransferable(), false /* do not allow placeholders */);
                  DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
                  InsertType insertType = model.determineInsertType(dragType, item, true /* preview */);
                  draggedComponents = model.createComponents(screenView, item, insertType);
                  component = draggedComponents != null && draggedComponents.size() == 1 ? draggedComponents.get(0) : null;
                }
              }
              if (component != null) {
                if (component.needsDefaultId()) {
                  component.assignId();
                }
                ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(getProject());
                ViewHandler viewHandler = viewHandlerManager.getHandler(component);
                if (viewHandler != null && dragDrop.getDragReceiver() != null) {
                  ViewEditor editor = new ViewEditorImpl(screenView);
                  dropCancelled = !viewHandler.onCreate(editor, dragDrop.getDragReceiver(), component, InsertType.CREATE);
                } else {
                  dropCancelled = false;
                }
              }
              finishInteraction(myLastMouseX, myLastMouseY, myLastStateMask, dropCancelled);
              if (component != null && component.getTag().isValid() && component.getTag().getParent() instanceof XmlTag) {
                // Remove any xmlns: attributes once the element is added into the document
                for (XmlAttribute attribute : component.getTag().getAttributes()) {
                  if (attribute.getName().startsWith(XMLNS_PREFIX)) {
                    attribute.delete();
                  }
                }
              }
            }
          }
        };
        action.execute();
        event.getDropTargetContext().dropComplete(true); // or just event.dropComplete() ?
        model.notifyModified();
      }
      catch (Exception ignore) {
        LOG.debug(ignore);
        event.rejectDrop();
      }
    }
  }

  @NonNull
  private static DnDTransferItem getTransferItem(@NonNull Transferable transferable, boolean allowPlaceholder)
    throws IOException, UnsupportedFlavorException {
    DnDTransferItem item = null;
    try {
      if (transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
        item = (DnDTransferItem)transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
      }
      else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        String xml = (String)transferable.getTransferData(DataFlavor.stringFlavor);
        if (!StringUtil.isEmpty(xml)) {
          item = new DnDTransferItem(new DnDTransferComponent("", xml, 200, 100));
        }
      }
    } catch (InvalidDnDOperationException ex) {
      if (!allowPlaceholder) {
        throw ex;
      }
      String defaultXml = "<placeholder xmlns:android=\"http://schemas.android.com/apk/res/android\"/>";
      item = new DnDTransferItem(new DnDTransferComponent("", defaultXml, 200, 100));
    }
    if (item == null) {
      throw new UnsupportedFlavorException(null);
    }
    return item;
  }

  @VisibleForTesting
  public Object getListener() {
    return myListener;
  }
}
