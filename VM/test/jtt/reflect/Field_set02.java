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
package jtt.reflect;

/*
 * @Harness: java
 * @Runs: 0=true; 1=true; 2=true; 3=true; 4=true; 5=true; 6=true; 7=true; 8=false;
 */
public class Field_set02 {
    private static final Field_set02 object = new Field_set02();

    public byte byteField;
    public short shortField;
    public char charField;
    public int intField;
    public long longField;
    public float floatField;
    public double doubleField;
    public boolean booleanField;

    public static boolean test(int arg) throws NoSuchFieldException, IllegalAccessException {
        if (arg == 0) {
            Field_set02.class.getField("byteField").set(object, Byte.valueOf((byte) 11));
            return object.byteField == 11;
        } else if (arg == 1) {
            Field_set02.class.getField("shortField").set(object, Short.valueOf((short) 12));
            return object.shortField == 12;
        } else if (arg == 2) {
            Field_set02.class.getField("charField").set(object, Character.valueOf((char) 13));
            return object.charField == 13;
        } else if (arg == 3) {
            Field_set02.class.getField("intField").set(object, Integer.valueOf(14));
            return object.intField == 14;
        } else if (arg == 4) {
            Field_set02.class.getField("longField").set(object, Long.valueOf(15L));
            return object.longField == 15;
        } else if (arg == 5) {
            Field_set02.class.getField("floatField").set(object, Float.valueOf(16));
            return object.floatField == 16;
        } else if (arg == 6) {
            Field_set02.class.getField("doubleField").set(object, Double.valueOf(17));
            return object.doubleField == 17;
        } else if (arg == 7) {
            Field_set02.class.getField("booleanField").set(object, true);
            return object.booleanField == true;
        }
        return false;
    }
}