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
package test.com.sun.max.vm.compiler.c1x;

import static test.com.sun.max.vm.compiler.c1x.C1XTest.PatternType.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.Arrays;

import com.sun.c1x.*;
import com.sun.c1x.ci.*;
import com.sun.c1x.target.*;
import com.sun.c1x.target.x86.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;
import com.sun.max.program.option.OptionSet.*;
import com.sun.max.test.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.actor.Actor;
import com.sun.max.vm.compiler.c1x.*;
import com.sun.max.vm.prototype.*;
import com.sun.max.vm.type.*;

/**
 * A simple harness to run the C1X compiler and test it in various modes, without
 * needing JUnit.
 *
 * @author Ben L. Titzer
 */
public class C1XTest {

    private static final OptionSet options = new OptionSet(false);

    private static final Option<Integer> traceOption = options.newIntegerOption("trace", 0,
        "Set the tracing level of the Maxine VM and runtime.");
    private static final Option<Integer> verboseOption = options.newIntegerOption("verbose", 1,
        "Set the verbosity level of the testing framework.");
    private static final Option<Boolean> printBailoutOption = options.newBooleanOption("print-bailout", false,
        "Print bailout exceptions.");
    private static final Option<File> outFileOption = options.newFileOption("o", (File) null,
        "A file to which output should be sent. If not specified, then output is sent to stdout.");
    private static final Option<Boolean> clinitOption = options.newBooleanOption("clinit", true,
        "Compile class initializer (<clinit>) methods");
    private static final Option<Boolean> failFastOption = options.newBooleanOption("fail-fast", true,
        "Stop compilation upon the first bailout.");
    private static final Option<Boolean> targetOption = options.newBooleanOption("target", false,
        "Compile the method(s) all the way to target code.");
    private static final Option<Boolean> timingOption = options.newBooleanOption("timing", false,
        "Report compilation time for each successful compile.");
    private static final Option<Boolean> c1xOptionsOption = options.newBooleanOption("c1x-options", false,
        "Print settings of C1XOptions.");
    private static final Option<Boolean> averageOption = options.newBooleanOption("average", true,
        "Report only the average compilation speed.");
    private static final Option<Long> longerThanOption = options.newLongOption("longer-than", 0L,
        "Report only the compilation times that took longer than the specified number of nanoseconds.");
    private static final Option<Long> slowerThanOption = options.newLongOption("slower-than", 1000000000L,
        "Report only the compilation speeds that were slower than the specified number of bytes per second.");
    private static final Option<Long> biggerThanOption = options.newLongOption("bigger-than", 0L,
        "Report only the compilation speeds for methods larger than the specified threshold.");
    private static final Option<Integer> warmupOption = options.newIntegerOption("warmup", 0,
        "Set the number of warmup runs to execute before initiating the timed run.");
    private static final Option<Boolean> helpOption = options.newBooleanOption("help", false,
        "Show help message and exit.");

    static {
        // add all the fields from C1XOptions as options
        options.addFieldOptions(C1XOptions.class, "XX");
        // add a special option "c1x-optlevel" which adjusts the optimization level
        options.addOption(new Option<Integer>("c1x-optlevel", -1, OptionTypes.INT_TYPE, "Set the overall optimization level of C1X (-1 to use default settings)") {
            @Override
            public void setValue(Integer value) {
                C1XOptions.setOptimizationLevel(value);
            }
        }, Syntax.REQUIRES_EQUALS);
    }

    private static final List<Timing> timings = new ArrayList<Timing>();

    private static PrintStream out = System.out;

    public static void main(String[] args) {
        options.parseArguments(args);
        reportC1XOptions();
        final String[] arguments = options.getArguments();

        if (helpOption.getValue()) {
            options.printHelp(System.out, 80);
            return;
        }

        if (outFileOption.getValue() != null) {
            try {
                out = new PrintStream(new FileOutputStream(outFileOption.getValue()));
                Trace.setStream(out);
            } catch (FileNotFoundException e) {
                System.err.println("Could not open " + outFileOption.getValue() + " for writing: " + e);
                System.exit(1);
            }
        }

        Trace.on(traceOption.getValue());

        // create the prototype
        if (verboseOption.getValue() > 0) {
            out.print("Creating Java prototype... ");
        }
        new PrototypeGenerator(options).createJavaPrototype(false);
        if (verboseOption.getValue() > 0) {
            out.println("done");
        }

        // create MaxineRuntime
        final MaxCiRuntime runtime = new MaxCiRuntime();
        final List<MethodActor> methods = findMethodsToCompile(arguments);
        final ProgressPrinter progress = new ProgressPrinter(out, methods.size(), verboseOption.getValue(), false);
        final Target target = createTarget();
        final C1XCompiler compiler = new C1XCompiler(target, runtime);

        doWarmup(compiler, methods);
        doCompile(compiler, methods, progress);

        progress.report();
        reportTiming();
        reportMetrics();
    }

    private static void doCompile(C1XCompiler compiler, List<MethodActor> methods, ProgressPrinter progress) {
        // compile all the methods and report progress
        for (MethodActor methodActor : methods) {
            progress.begin(methodActor.toString());
            final boolean result = compile(compiler, methodActor, printBailoutOption.getValue(), false);
            if (result) {
                progress.pass();
            } else {
                progress.fail("failed");
                if (failFastOption.getValue()) {
                    out.println("");
                    break;
                }
            }
        }
    }

    private static void doWarmup(C1XCompiler compiler, List<MethodActor> methods) {
        // compile all the methods in the list some number of times first to warmup the C1X code in the host VM
        for (int i = 0; i < warmupOption.getValue(); i++) {
            if (i == 0) {
                out.print("Warming up");
            }
            out.print(".");
            out.flush();
            for (MethodActor actor : methods) {
                compile(compiler, actor, false, true);
            }
            if (i == warmupOption.getValue() - 1) {
                out.print("\n");
            }
        }
    }

    private static boolean compile(C1XCompiler compiler, MethodActor method, boolean printBailout, boolean warmup) {
        // compile a single method
        if (isCompilable(method)) {
            final long startNs = System.nanoTime();

            CiTargetMethod result = null;
            try {
                result = compiler.compileMethod(((MaxCiRuntime) compiler.runtime).getCiMethod(method));
            } catch (Bailout bailout) {
                if (printBailout) {
                    bailout.printStackTrace();
                }
                return false;
            }

            if (result != null) {
                long timeNs = System.nanoTime() - startNs;

                if (!warmup) {
                    // record the time for successful compilations
                    recordTime(method, result.totalInstructions(), timeNs);
                }
            }

            return true;
        }
        return true;
    }

    private static boolean isCompilable(MethodActor method) {
        return method instanceof ClassMethodActor && !method.isAbstract() && !method.isNative() && !method.isBuiltin() && !method.isUnsafeCast();
    }

    enum PatternType {
        EXACT("matching") {
            @Override
            public boolean matches(String input, String pattern) {
                return input.equals(pattern);
            }
        },
        PREFIX("starting with") {
            @Override
            public boolean matches(String input, String pattern) {
                return input.startsWith(pattern);
            }
        },
        SUFFIX("ending with") {
            @Override
            public boolean matches(String input, String pattern) {
                return input.endsWith(pattern);
            }
        },
        CONTAINS("containing") {
            @Override
            public boolean matches(String input, String pattern) {
                return input.contains(pattern);
            }
        };

        final String relationship;

        private PatternType(String relationship) {
            this.relationship = relationship;
        }

        abstract boolean matches(String input, String pattern);

        @Override
        public String toString() {
            return relationship;
        }
    }

    static class PatternMatcher {
        final String pattern;
        // 1: exact, 2: prefix, 3: suffix, 4: substring
        final PatternType type;

        public PatternMatcher(String pattern) {
            if (pattern.startsWith("^") && pattern.endsWith("^")) {
                this.type = EXACT;
                this.pattern = pattern.substring(1, pattern.length() - 1);
            } else if (pattern.startsWith("^")) {
                this.type = PREFIX;
                this.pattern = pattern.substring(1);
            } else if (pattern.endsWith("^")) {
                this.type = SUFFIX;
                this.pattern = pattern.substring(0, pattern.length() - 1);
            } else {
                this.type = CONTAINS;
                this.pattern = pattern;
            }
        }

        boolean matches(String input) {
            return type.matches(input, pattern);
        }
    }

    private static List<MethodActor> findMethodsToCompile(String[] arguments) {
        final Classpath classpath = Classpath.fromSystem();
        final List<MethodActor> methods = new ArrayList<MethodActor>() {
            @Override
            public boolean add(MethodActor e) {
                final boolean result = super.add(e);
                // register foldable methods with C1X.
                if (C1XOptions.CanonicalizeFoldableMethods && Actor.isDeclaredFoldable(e.flags())) {
                    final Method method = e.toJava();
                    assert method != null;
                    C1XIntrinsic.registerFoldableMethod(MaxCiRuntime.globalRuntime.getCiMethod(e), method);
                }
                if ((size() % 1000) == 0 && verboseOption.getValue() >= 1) {
                    out.print('.');
                }
                return result;
            }
        };

        for (int i = 0; i != arguments.length; ++i) {
            final String argument = arguments[i];
            final int colonIndex = argument.indexOf(':');
            final PatternMatcher classNamePattern = new PatternMatcher(colonIndex == -1 ? argument : argument.substring(0, colonIndex));

            // search for matching classes on the class path
            final AppendableSequence<String> matchingClasses = new ArrayListSequence<String>();
            if (verboseOption.getValue() > 0) {
                out.print("Classes " + classNamePattern.type + " '" + classNamePattern.pattern + "'... ");
            }

            new ClassSearch() {
                @Override
                protected boolean visitClass(String className) {
                    if (!className.endsWith("package-info")) {
                        if (classNamePattern.matches(className)) {
                            matchingClasses.append(className);
                        }
                    }
                    return true;
                }
            }.run(classpath);

            if (verboseOption.getValue() > 0) {
                out.println(matchingClasses.length());
            }

            final int startMethods = methods.size();
            if (verboseOption.getValue() > 0) {
                out.print("Gathering methods");
            }
            // for all found classes, search for matching methods
            for (String className : matchingClasses) {
                try {
                    final Class<?> javaClass = Class.forName(className, false, C1XTest.class.getClassLoader());
                    final ClassActor classActor = getClassActorNonfatal(javaClass);
                    if (classActor == null) {
                        continue;
                    }

                    if (colonIndex == -1) {
                        // Class only: compile all methods in class
                        for (MethodActor actor : classActor.localStaticMethodActors()) {
                            if (clinitOption.getValue() || actor != classActor.clinit) {
                                methods.add(actor);
                            }
                        }
                        methods.addAll(Arrays.asList(classActor.localVirtualMethodActors()));
                    } else {
                        // a method pattern was specified, find matching methods
                        final int parenIndex = argument.indexOf('(', colonIndex + 1);
                        final PatternMatcher methodNamePattern;
                        final SignatureDescriptor signature;
                        if (parenIndex == -1) {
                            methodNamePattern = new PatternMatcher(argument.substring(colonIndex + 1));
                            signature = null;
                        } else {
                            methodNamePattern = new PatternMatcher(argument.substring(colonIndex + 1, parenIndex));
                            signature = SignatureDescriptor.create(argument.substring(parenIndex));
                        }
                        addMatchingMethods(methods, classActor, methodNamePattern, signature, classActor.localStaticMethodActors());
                        addMatchingMethods(methods, classActor, methodNamePattern, signature, classActor.localVirtualMethodActors());
                    }
                } catch (ClassNotFoundException classNotFoundException) {
                    ProgramWarning.message(classNotFoundException.toString());
                }
            }
            if (verboseOption.getValue() > 0) {
                out.println(" " + (methods.size() - startMethods));
            }
        }
        return methods;
    }

    private static ClassActor getClassActorNonfatal(Class<?> javaClass) {
        ClassActor classActor = null;
        try {
            classActor = ClassActor.fromJava(javaClass);
        } catch (Throwable t) {
            // do nothing.
        }
        return classActor;
    }

    private static void addMatchingMethods(final List<MethodActor> methods, final ClassActor classActor, final PatternMatcher methodNamePattern, final SignatureDescriptor signature, MethodActor[] methodActors) {
        for (final MethodActor method : methodActors) {
            if (methodNamePattern.matches(method.name.toString())) {
                final SignatureDescriptor methodSignature = method.descriptor();
                if (signature == null || signature.equals(methodSignature)) {
                    methods.add(method);
                }
            }
        }
    }

    private static void recordTime(MethodActor method, int instructions, long ns) {
        if (timingOption.getValue()) {
            timings.add(new Timing((ClassMethodActor) method, instructions, ns));
        }
    }

    private static void reportTiming() {
        if (timingOption.getValue()) {
            double totalBcps = 0d;
            double totalIps = 0d;
            int count = 0;
            long longerThan = longerThanOption.getValue();
            long slowerThan = slowerThanOption.getValue();
            long biggerThan = biggerThanOption.getValue();
            boolean printAll = !averageOption.getValue();
            for (Timing timing : timings) {
                final MethodActor method = timing.classMethodActor;
                final long ns = timing.nanoSeconds;
                final int bytecodes = timing.bytecodes();
                final double bcps = timing.bytecodesPerSecond();
                final double ips = timing.instructionsPerSecond();
                if (printAll && ns >= longerThan && bcps <= slowerThan && bytecodes >= biggerThan) {
                    out.print(Strings.padLengthWithSpaces("#" + timing.number, 6));
                    out.print(Strings.padLengthWithSpaces(method.toString(), 80) + ": \n\t\t");
                    out.print(Strings.padLengthWithSpaces(6, bytecodes + " bytes   "));
                    out.print(Strings.padLengthWithSpaces(13, ns + " ns "));
                    out.print(Strings.padLengthWithSpaces(20, Strings.fixedDouble(bcps, 2) + " bytes/s"));
                    out.print(Strings.padLengthWithSpaces(20, Strings.fixedDouble(ips, 2) + " insts/s"));
                    out.println();
                }
                totalBcps += bcps;
                totalIps += ips;
                count++;
            }
            out.print("Average: ");
            out.print(Strings.fixedDouble(totalBcps / count, 2) + " bytes/s   ");
            out.print(Strings.fixedDouble(totalIps / count, 2) + " insts/s");
            out.println();
        }
    }

    private static void reportMetrics() {
        if (C1XOptions.PrintMetrics && verboseOption.getValue() > 0) {
            printClassFields(C1XMetrics.class);
        }
    }

    private static void reportC1XOptions() {
        if (c1xOptionsOption.getValue()) {
            printClassFields(C1XOptions.class);
        }
    }

    private static void printClassFields(Class<?> javaClass) {
        final String className = javaClass.getSimpleName();
        out.println(className + " {");
        for (final Field field : javaClass.getFields()) {
            final String fieldName = Strings.padLengthWithSpaces(field.getName(), 35);
            try {
                if (field.getType() == int.class) {
                    out.print("    " + fieldName + " = " + field.getInt(null) + "\n");
                } else if (field.getType() == boolean.class) {
                    out.print("    " + fieldName + " = " + field.getBoolean(null) + "\n");
                } else if (field.getType() == float.class) {
                    out.print("    " + fieldName + " = " + field.getFloat(null) + "\n");
                } else if (field.getType() == String.class) {
                    out.print("    " + fieldName + " = " + field.get(null) + "\n");
                }
            } catch (IllegalAccessException e) {
                // do nothing.
            }
        }
        out.println("}");
    }

    private static class Timing {
        private final int number;
        private final ClassMethodActor classMethodActor;
        private final int instructions;
        private final long nanoSeconds;

        Timing(ClassMethodActor classMethodActor, int instructions, long ns) {
            this.number = timings.size();
            this.classMethodActor = classMethodActor;
            this.instructions = instructions;
            this.nanoSeconds = ns;
        }

        public double bytecodesPerSecond() {
            return 1000000000 * (bytecodes() / (double) nanoSeconds);
        }

        public int bytecodes() {
            return classMethodActor.rawCodeAttribute().code().length;
        }

        public double instructionsPerSecond() {
            return 1000000000 * (instructions / (double) nanoSeconds);
        }
    }

    private static Target createTarget() {
        // TODO: configure architecture according to host platform
        final Architecture arch = Architecture.findArchitecture("amd64");


        // configure the allocatable registers
        List<Register> allocatable = new ArrayList<Register>(arch.registers.length);
        for (Register r : arch.registers) {
            if (r != X86.rsp && r != MaxCiRuntime.globalRuntime.exceptionOopRegister()) {
                allocatable.add(r);
            }
        }
        Register[] allocRegs = allocatable.toArray(new Register[allocatable.size()]);
        return new Target(arch, allocRegs, arch.registers, 1024, true);
    }
}
