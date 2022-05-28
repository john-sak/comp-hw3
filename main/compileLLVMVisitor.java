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
    String scope = null, fileName = null;
}

class methInfoNode {
    String name, retType, argTypes;
}

class compileLLVMVisitor extends GJDepthFirst<String, CLLVMArgs> {

    public String getTypeLLVMA(String type, CLLVMArgs argu) throws Exception {
        if (type.compareTo("boolean[]") == 0) return "i1*";
        else if (type.compareTo("int[]") == 0) return "i32*";
        else if (type.compareTo("boolean") == 0) return "i1";
        else if (type.compareTo("int") == 0) return "i32";
        else if (argu.symbolTable.containsKey(type)) return "i8*";
        throw new Exception();
    }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    @Override
    public String visit(Goal n, CLLVMArgs argu) throws Exception {
        if (!argu.fileName.endsWith("\\.java")) throw new Exception();
        argu.fileName = argu.fileName.split("\\.")[0] + ".ll";
        try {
            File file = new File(argu.fileName);
            if (!file.createNewFile()) System.err.println("File already exists.");
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            throw new Exception();
        }
        FileWriter writer;
        for (Map.Entry<String, classInfo> entry : argu.symbolTable.entrySet()) {
            String className = entry.getKey(), vTable = "@." + className + "_vtable = global [";
            classInfo classI = entry.getValue();
            if (classI.methods.containsKey("main")) {
                vTable += "0 x i8*] []\n";
                writer = new FileWriter(argu.fileName);
                writer.write(vTable);
                writer.close();
                continue;
            }
            List<methInfoNode> methInfoList = new ArrayList<methInfoNode>();
            classInfo superClassI;
            while ((superClassI = classI.superclass) != null) {
                for (Map.Entry<String, methodInfo> entryIN : superClassI.methods.entrySet()) {
                    
                }
            }
            Map<String, methodInfo> classMethods;
            if ((classMethods = classI.methods) == null) throw new Exception();
            for (Map.Entry<String, methodInfo> entryIN : classMethods.entrySet()) {

            }
            int size = classMethods.size();
            vTable += size + " x i8*] [";
            for (Map.Entry<String, methodInfo> entryIN : classMethods.entrySet()) {
                methodInfo methodI = entryIN.getValue();
                String retType = methodI.returnValue;
                vTable += "i8* bitcast (" + getTypeLLVMA(retType, argu) + " (i8*";
                if (methodI.argNum != 0) {
                    String[] argTypes = methodI.argTypes.split(", ");
                    for (String argType : argTypes) vTable += ", " + getTypeLLVMA(argType, argu);
                }
                String methName = entryIN.getKey();
                vTable += ")* @" + className + "." + methName + " to i8*), ";
            }
            int length = vTable.length();
            if (vTable.substring(length - 2, length).compareTo(", ") == 0) vTable = vTable.substring(0, length - 2);
            vTable += "]\n";
            writer = new FileWriter(argu.fileName);
            writer.write(vTable);
            writer.close();
        }
        writer = new FileWriter(argu.fileName);
        writer.write("\ndeclare i8* @calloc(i32, i32)\ndeclare i32 @printf(i8*, ...)\ndeclare void @exit(i32)\n");
        writer.write("\n@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n");
        writer.write("\ndefine void @print_int(i32 %i) {\n\t%_str = bitcast [4 x i8]* @_cint to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n\tret void\n}\n");
        writer.write("\ndefine voide @throw_oob() {\n\t%_str = bitcast [15 x i8]* @cOOB to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str)\n\tcall void @exit(i32 1)\n\tret void\n}\n");
        writer.close();
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    @Override
    public String visit(MainClass n, CLLVMArgs argu) throws Exception {
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("\ndefine i32 @main {\n");
        writer.close();
        String oldArgu = argu.scope;
        argu.scope = n.f1.accept(this, argu) + "->main";
        n.f15.accept(this, argu);
        argu.scope = oldArgu;
        writer = new FileWriter(argu.fileName);
        writer.write("}\n");
        writer.close();
        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public String visit(ClassDeclaration n, CLLVMArgs argu) throws Exception {
        String oldArgu = argu.scope;
        argu.scope = n.f1.accept(this, argu);
        n.f4.accept(this, argu);
        argu.scope = oldArgu;
        return null;
    }
    
    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public String visit(ClassExtendsDeclaration n, CLLVMArgs argu) throws Exception {
        String oldArgu = argu.scope;
        argu.scope = n.f1.accept(this, argu);
        n.f6.accept(this, argu);
        argu.scope = oldArgu;
        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public String visit(VarDeclaration n, CLLVMArgs argu) throws Exception {
        return null;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public String visit(MethodDeclaration n, CLLVMArgs argu) throws Exception {
        String retType = n.f1.accept(this, argu), methName = n.f2.accept(this, argu);
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("\ndefine " + getTypeLLVMA(retType, argu) + " @" + argu.scope + "." + methName + "(i8* %this");
        writer.close();
        String oldArgu = argu.scope;
        argu.scope += "->" + methName;
        String params = n.f4.accept(this, argu);
        // todo
        n.f8.accept(this, argu);
        String ret = n.f10.accept(this, argu);
        writer = new FileWriter(argu.fileName);
        writer.write("ret " + ret + "\n");
        writer.close();
        argu.scope = oldArgu;
        return null;
    }
}