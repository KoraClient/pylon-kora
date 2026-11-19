package pl.tomgirl.pylon.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Rewrites a class's static calls into {@code org/lwjgl/opengl} classes as the installed {@link GlRouting.Router} says
 * (Kora fork, added 2026-10-18). A routed call keeps its descriptor, so the stack and the frames stay as they are. An
 * {@link GlRouting#IGNORE} or {@link GlRouting#MISSING} call becomes a call to a small static method added to the same
 * class, one per kind and descriptor. Method handles to GL methods (lambdas, method references) are routed the same way.
 */
final class GlRouteTransformer {
    private static final String GL_PACKAGE = "org/lwjgl/opengl/";
    private static final String HOOK = "pl/tomgirl/pylon/render/GlRouting";
    private static final String PREFIX = "pylon$gl$";
    private static final int CONSTANT_CLASS = 7;

    private GlRouteTransformer() {}

    /** The rewritten class, or null when it has nothing to route. */
    static byte[] transform(byte[] bytecode, GlRouting.Router router) {
        ClassReader reader = new ClassReader(bytecode);
        if (!router.routes(reader.getClassName()) || !referencesGl(reader)) {
            return null;
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        Rewriter rewriter = new Rewriter(writer, router);
        reader.accept(rewriter, 0);
        return rewriter.changed ? writer.toByteArray() : null;
    }

    /** Whether the constant pool names an {@code org/lwjgl/opengl} class: every call and method handle goes through one. */
    private static boolean referencesGl(ClassReader reader) {
        char[] buffer = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int offset = reader.getItem(i);
            if (offset > 0 && reader.readByte(offset - 1) == CONSTANT_CLASS
                && reader.readUTF8(offset, buffer).startsWith(GL_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private static final class Rewriter extends ClassVisitor {
        private final GlRouting.Router router;
        private final Map<String, String> added = new HashMap<>();
        private final List<String[]> pending = new ArrayList<>();
        private String owner;
        private boolean isInterface;
        private int version;
        boolean changed;

        Rewriter(ClassVisitor next, GlRouting.Router router) {
            super(Opcodes.ASM9, next);
            this.router = router;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.version = version & 0xFFFF;
            this.owner = name;
            this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public void visitMethodInsn(int opcode, String callOwner, String callName, String callDescriptor, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && callOwner.startsWith(GL_PACKAGE)) {
                        Handle target = target(callOwner, callName, callDescriptor);
                        if (target != null) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, target.getOwner(), target.getName(),
                                target.getDesc(), target.isInterface());
                            return;
                        }
                    }
                    super.visitMethodInsn(opcode, callOwner, callName, callDescriptor, itf);
                }

                @Override
                public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                    Object[] routed = arguments;
                    for (int i = 0; i < arguments.length; i++) {
                        if (arguments[i] instanceof Handle) {
                            Handle handle = (Handle) arguments[i];
                            if (handle.getTag() == Opcodes.H_INVOKESTATIC && handle.getOwner().startsWith(GL_PACKAGE)) {
                                Handle target = target(handle.getOwner(), handle.getName(), handle.getDesc());
                                if (target != null) {
                                    if (routed == arguments) {
                                        routed = arguments.clone();
                                    }
                                    routed[i] = target;
                                }
                            }
                        }
                    }
                    super.visitInvokeDynamicInsn(name, descriptor, bootstrap, routed);
                }
            };
        }

        /** Where the call goes (as an H_INVOKESTATIC handle), or null to leave it. */
        private Handle target(String callOwner, String callName, String callDescriptor) {
            String route = router.route(callOwner, callName, callDescriptor);
            if (route == null) {
                return null;
            }
            if (!route.equals(GlRouting.IGNORE) && !route.equals(GlRouting.MISSING)) {
                changed = true;
                return new Handle(Opcodes.H_INVOKESTATIC, route, callName, callDescriptor, false);
            }
            // Static methods in interfaces need Java 8 class files, private ones Java 9.
            if (isInterface && version < Opcodes.V1_8) {
                return null;
            }
            boolean missing = route.equals(GlRouting.MISSING);
            String call = callOwner.substring(GL_PACKAGE.length()) + "." + callName + callDescriptor;
            String key = (missing ? call : "") + "|" + callDescriptor;
            String method = added.get(key);
            if (method == null) {
                method = PREFIX + added.size();
                added.put(key, method);
                pending.add(new String[] {method, callDescriptor, missing ? call : null});
            }
            changed = true;
            return new Handle(Opcodes.H_INVOKESTATIC, owner, method, callDescriptor, isInterface);
        }

        @Override
        public void visitEnd() {
            for (String[] method : pending) {
                addMethod(method[0], method[1], method[2]);
            }
            super.visitEnd();
        }

        /** {@code static <descriptor> name(...) { [GlRouting.missing(call);] return 0 / false / null; }} */
        private void addMethod(String name, String descriptor, String missingCall) {
            int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                | (isInterface && version < Opcodes.V9 ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
            MethodVisitor method = super.visitMethod(access, name, descriptor, null, null);
            method.visitCode();
            if (missingCall != null) {
                method.visitLdcInsn(missingCall);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "missing", "(Ljava/lang/String;)V", false);
            }
            Type returnType = Type.getReturnType(descriptor);
            switch (returnType.getSort()) {
                case Type.VOID:
                    method.visitInsn(Opcodes.RETURN);
                    break;
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    method.visitInsn(Opcodes.ICONST_0);
                    method.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.FLOAT:
                    method.visitInsn(Opcodes.FCONST_0);
                    method.visitInsn(Opcodes.FRETURN);
                    break;
                case Type.LONG:
                    method.visitInsn(Opcodes.LCONST_0);
                    method.visitInsn(Opcodes.LRETURN);
                    break;
                case Type.DOUBLE:
                    method.visitInsn(Opcodes.DCONST_0);
                    method.visitInsn(Opcodes.DRETURN);
                    break;
                default:
                    method.visitInsn(Opcodes.ACONST_NULL);
                    method.visitInsn(Opcodes.ARETURN);
                    break;
            }
            int sizes = Type.getArgumentsAndReturnSizes(descriptor);
            int locals = (sizes >> 2) - 1;
            method.visitMaxs(Math.max(missingCall != null ? 1 : 0, sizes & 3), locals);
            method.visitEnd();
        }
    }
}
