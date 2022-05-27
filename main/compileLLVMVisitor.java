import syntaxtree.*;
import visitor.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

class CLLVMArgs {
    Map<String, classInfo> symbolTable = null;
    Map<String, OTEntry> offsetTable = null;
    String scope;
}

class compileLLVMVisitor extends GJDepthFirst<String, CLLVMArgs> {

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    @Override
    public String visit(Goal n, CLLVMArgs argu) throws Exception {
        String name = argu.scope + ".ll";
        try {
            File file = new File(name);
            if (!file.createNewFile()) System.err.println("File already exists.");
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            throw new Exception();
        }
        FileWriter writer = new FileWriter(name);
        for (Map.Entry<String, classInfo> entry : argu.symbolTable.entrySet()) {
            String className = entry.getKey(), vTable = "@." + className + "_vtable = global [";
            Map<String, methodInfo> classMethods;
            if ((classMethods = entry.getValue().methods) == null) throw new Exception();
            if (classMethods.containsKey("main")) {
                vTable += "0 x i8*] []\n";
                writer.write(vTable);
                continue;
            }
            int size = classMethods.size();
            vTable += size + " x i8*] [";
            for (Map.Entry<String, methodInfo> entryIN : classMethods.entrySet()) {
                vTable += "i8* bitcast (";
                methodInfo methodI = entryIN.getValue();
                String methName = entryIN.getKey(), retType = methodI.returnValue;
                if (retType.compareTo("boolean[]") == 0) vTable += "i1*";
                else if (retType.compareTo("int[]") == 0) vTable += "i32*";
                else if (retType.compareTo("boolean") == 0) vTable += "i1";
                else if (retType.compareTo("int") == 0) vTable += "i32";
                else if (argu.symbolTable.containsKey(retType)) vTable += "i8*";
                else throw new Exception();
                vTable += " (i8*";
                if (methodI.argNum != 0) {
                    String[] argTypes = methodI.argTypes.split(", ");
                    for (String argType : argTypes) {
                        if (argType.compareTo("boolean[]") == 0) vTable += ", i1*";
                        else if (argType.compareTo("int[]") == 0) vTable += ", i32*";
                        else if (argType.compareTo("boolean") == 0) vTable += ", i1";
                        else if (argType.compareTo("int") == 0) vTable += ", i32";
                        else if (argu.symbolTable.containsKey(argType)) vTable += ", i8*";
                        else throw new Exception();
                    }
                }
                vTable += ")* @" + className + "." + methName + " to i8*)";
            }
            int length = vTable.length();
            if (vTable.substring(length - 2, length).compareTo(", ") == 0) vTable = vTable.substring(0, length - 2);
            vTable += "]\n";
            writer.write(vTable);
        }
        writer.write("\ndeclare i8* @calloc(i32, i32)\ndeclare i32 @printf(i8*, ...)\ndeclare void @exit(i32)\n");
        writer.write("\n@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n");
        writer.write("\ndefine void @print_int(i32 %i) {\n\t%_str = bitcast [4 x i8]* @_cint to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n\tret void\n}\n");
        writer.write("\ndefine voide @throw_oob() {\n\t%_str = bitcast [15 x i8]* @cOOB to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str)\n\tcall void @exit(i32 1)\n\tret void\n}\n");
        writer.write("\n");
        writer.close();
        return name;
    }
}