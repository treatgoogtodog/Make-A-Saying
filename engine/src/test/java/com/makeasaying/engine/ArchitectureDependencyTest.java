package com.makeasaying.engine;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureDependencyTest {

    private static final String[] FORBIDDEN_PREFIXES = {
        "com/makeasaying/data",
        "com/makeasaying/editor",
        "com/makeasaying/game"
    };

    @Test
    void engineMustNeverDependOnEditorOrDataModulesBytecodeScan() throws IOException {
        // Enforce AGENTS.md principle 4.1 & DEC-002:
        // Engine must never reference data, editor, or game modules in any compiled main class.
        File testClassesDir = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
        File mainClassesDir = new File(testClassesDir.getParentFile(), "main");
        assertTrue(mainClassesDir.exists() && mainClassesDir.isDirectory(), "Engine main classes directory must exist: " + mainClassesDir);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(mainClassesDir.toPath())) {
            stream.filter(p -> p.toString().endsWith(".class")).forEach(path -> {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    List<String> refs = scanConstantPoolForForbidden(bytes);
                    for (String ref : refs) {
                        violations.add(path.getFileName() + " references forbidden: " + ref);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed reading class file: " + path, e);
                }
            });
        }

        assertTrue(violations.isEmpty(), "Engine classes must not reference forbidden packages:\n" + String.join("\n", violations));
    }

    @Test
    void positiveControlMustCatchForbiddenReference() throws IOException {
        // Positive control: Verify the bytecode scanner detects forbidden references when present.
        // Construct a mock class constant pool containing a reference to com/makeasaying/data/config/ConfigLoader.
        byte[] mockClass = createMockClassWithConstantString("com/makeasaying/data/config/ConfigLoader");
        List<String> detected = scanConstantPoolForForbidden(mockClass);
        assertFalse(detected.isEmpty(), "Positive control must detect forbidden reference to data");
        assertTrue(detected.get(0).contains("com/makeasaying/data/config/ConfigLoader"));

        byte[] cleanMockClass = createMockClassWithConstantString("com/makeasaying/engine/entity/World");
        List<String> cleanDetected = scanConstantPoolForForbidden(cleanMockClass);
        assertTrue(cleanDetected.isEmpty(), "Clean class must produce zero violations");
    }

    @Test
    void engineMustNeverDependOnEditorModules() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.core.imgui.ImGuiHost", false, classLoader)
        );
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.map.document.RoomDocument", false, classLoader)
        );
    }

    @Test
    void engineMustNeverDependOnDataModule() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.data.config.ConfigLoader", false, classLoader)
        );
    }

    /**
     * Parses the constant pool of a JVM .class file and returns any string or class ref matching forbidden prefixes.
     */
    static List<String> scanConstantPoolForForbidden(byte[] classBytes) throws IOException {
        List<String> violations = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                return violations; // not a class file
            }
            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            int cpCount = in.readUnsignedShort();

            String[] strings = new String[cpCount];
            // Constant pool tags:
            // 1: Utf8, 3: Integer, 4: Float, 5: Long, 6: Double, 7: Class, 8: String, 9..11: Ref, 12: NameAndType,
            // 15: MethodHandle, 16: MethodType, 17..18: Dynamic, 19..20: Module/Package
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: // Utf8
                        strings[i] = in.readUTF();
                        break;
                    case 3: // Integer
                    case 4: // Float
                        in.skipBytes(4);
                        break;
                    case 5: // Long
                    case 6: // Double
                        in.skipBytes(8);
                        i++; // takes 2 constant pool slots
                        break;
                    case 7: // Class ref
                    case 8: // String ref
                    case 16: // MethodType
                    case 19: // Module
                    case 20: // Package
                        in.skipBytes(2);
                        break;
                    case 9: // Fieldref
                    case 10: // Methodref
                    case 11: // InterfaceMethodref
                    case 12: // NameAndType
                    case 17: // Dynamic
                    case 18: // InvokeDynamic
                        in.skipBytes(4);
                        break;
                    case 15: // MethodHandle
                        in.skipBytes(3);
                        break;
                    default:
                        // unknown tag
                        return violations;
                }
            }

            for (int i = 1; i < cpCount; i++) {
                String s = strings[i];
                if (s != null) {
                    for (String forbidden : FORBIDDEN_PREFIXES) {
                        if (s.contains(forbidden)) {
                            violations.add(s);
                            break;
                        }
                    }
                }
            }
        }
        return violations;
    }

    private static byte[] createMockClassWithConstantString(String utf8Value) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream d = new java.io.DataOutputStream(out);
        d.writeInt(0xCAFEBABE);
        d.writeShort(0); // minor
        d.writeShort(52); // major (Java 8)
        d.writeShort(2); // cpCount = 2 (1 entry)
        d.writeByte(1); // tag 1: Utf8
        d.writeUTF(utf8Value);
        d.flush();
        return out.toByteArray();
    }
}

