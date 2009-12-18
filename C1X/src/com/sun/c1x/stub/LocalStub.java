/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.c1x.stub;

import com.sun.c1x.asm.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.lir.*;

/**
 * LocalStubs are small sequences of code that handle slow cases of operations.
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 */
public abstract class LocalStub {

    public LIRDebugInfo info;
    public final Label entry = new Label();            // label at the stub entry point
    public final Label continuation = new Label();     // label where stub continues, if any

    protected LIRInstruction instruction;

    public LIROperand[] operands;
    public LIROperand result;
    public LIRInstruction.OperandSlot resultSlot;

    public int tempCount;
    public int tempInputCount;

    public LocalStub(LIRDebugInfo info) {
        this(info, LIROperand.IllegalLocation);
    }

    public LocalStub(LIRDebugInfo info, LIROperand result) {
        this.info = info;
        this.result = result;
    }

    protected void setOperands(int tempInputCount, int tempCount, LIROperand... operands) {
        this.tempCount = tempCount;
        this.tempInputCount = tempInputCount;
        this.operands = operands;
    }

    public LIROperand operand(int index) {
        return instruction.stubOperand(index);
    }

    public boolean assertNoUnboundLabels() {
        assert !entry.isUnbound() && !continuation.isUnbound() : "Code stub has an unbound label";
        return true;
    }

    public boolean isExceptionThrowStub() {
        return false;
    }

    public abstract void accept(LocalStubVisitor visitor);

    public void printName(LogStream out) {
        out.print(name());
    }

    public String name() {
        return this.getClass().getSimpleName();
    }

    public LIROperand originalResult() {
        return result;
    }

    public LIROperand result() {
        return resultSlot.get(instruction);
    }

    public void setResultSlot(LIRInstruction.OperandSlot resultSlot) {
        this.resultSlot = resultSlot;
    }

    public void setInstruction(LIRInstruction instruction) {
        this.instruction = instruction;

    }
}