/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.gui;

import java.awt.*;

import javax.swing.table.*;

import com.sun.max.ins.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.unsafe.*;


/**
 * A renderer suitable for a table "Tag" cell in an Inspector display where each row corresponds to a memory location.
 * Displays text identifying registers, if any, that point at this location; displays a special border if there is a
 * watchpoint at the location.
 *
 * @author Michael Van De Vanter
 */
public abstract class MemoryTagTableCellRenderer extends InspectorLabel implements TableCellRenderer {

    public MemoryTagTableCellRenderer(Inspection inspection) {
        super(inspection);
    }

    /**
     * Returns a cell render suitable for the "Tag" cell in an Inspector display whre each row corresponds
     * to a register location.  The text and tooltip text of this label/renderer to display informative strings
     * if one or more integer registers in the specified thread point at this location.
     * The text is empty if no registers point at this location.
     * <br>
     *
     * @param address a memory location in the VM
     * @param thread the thread from which to read registers
     * @param watchpoint the watchpoint at this location, null if none.
     * @return a component for displaying the cell
     */
    public Component getRenderer(Address address, MaxThread thread, MaxWatchpoint watchpoint) {
        InspectorLabel label = this;
        String labelText = "";
        String toolTipText = "";
        // See if any registers point here
        if (thread != null) {
            final TeleIntegerRegisters teleIntegerRegisters = thread.integerRegisters();
            if (teleIntegerRegisters == null) {
                // Return a specialized renderer with its own content.
                label = gui().getMissingDataTableCellRenderer();
            } else {
                final String registerNameList = teleIntegerRegisters.findAsNameList(address, address.plus(maxVM().wordSize()));
                if (registerNameList.isEmpty()) {
                    label.setForeground(style().memoryDefaultTagTextColor());
                } else {
                    labelText += registerNameList + "-->";
                    toolTipText += "Register(s): " + registerNameList + " in thread " + inspection().nameDisplay().longName(thread) + " point at this location";
                    setForeground(style().memoryRegisterTagTextColor());
                }
            }
        }
        if (watchpoint != null) {
            toolTipText += "  " + watchpoint.toString();
            label.setText(labelText);
            label.setToolTipText(toolTipText);
            return label.getBorderWrappedPanel(style().debugEnabledTargetBreakpointTagBorder());
        }
        label.setText(labelText);
        label.setToolTipText(toolTipText);
        return label;
    }

    public void redisplay() {
    }

    public void refresh(boolean force) {
    }

}
