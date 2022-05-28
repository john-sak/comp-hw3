import syntaxtree.*;
import visitor.*;

import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

class VTArgs {
    String fileName, scope, vTableEntry;
    int entryLength;
}

class VTEntry {
    String entry;
    int size;

    VTEntry(String entry, int size) {
        this.entry = entry;
        this.size = size;
    }
}

class vTableVisitor extends GJDepthFirst<String, VTArgs> {

    public Map<String, VTEntry> vTableEntries = new HashMap<String, VTEntry>();

    public String getTypeLLVMA(String type) throws Exception {
        if (type.compareTo("boolean[]") == 0) return "i1*";
        else if (type.compareTo("int[]") == 0) return "i32*";
        else if (type.compareTo("boolean") == 0) return "i1";
        else if (type.compareTo("int") == 0) return "i32";
        else return "i8*";
    }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    @Override
    public String visit(Goal n, VTArgs argu) throws Exception {
        if (argu.fileName == null || !argu.fileName.endsWith(".java")) throw new Exception();
        argu.fileName = argu.fileName.split("\\.")[0] + ".ll";
        try {
            File file = new File(argu.fileName);
            if (!file.createNewFile()) System.err.println("File already exists.");
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            throw new Exception();
        }
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
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
    public String visit(MainClass n, VTArgs argu) throws Exception {
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("@." + n.f1.accept(this, argu) + "_vtable = global [0 x i8*] []\n");
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
    public String visit(ClassDeclaration n, VTArgs argu) throws Exception {
        argu.scope = n.f1.accept(this, argu);
        argu.vTableEntry = "";
        argu.entryLength = 0;
        n.f4.accept(this, argu);
        if (argu.vTableEntry.endsWith(", ")) argu.vTableEntry = argu.vTableEntry.substring(0, argu.vTableEntry.length() - 2);
        if (vTableEntries.put(argu.scope, new VTEntry(argu.vTableEntry, argu.entryLength)) != null) throw new Exception();
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("@." + argu.scope + "_vtable = global [" + argu.entryLength + " x i8*] [" + argu.vTableEntry + "]");
        writer.close();
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
    public String visit(ClassExtendsDeclaration n, VTArgs argu) throws Exception {
        argu.scope = n.f1.accept(this, argu);
        String extnd = n.f3.accept(this, argu);
        argu.vTableEntry = "";
        argu.entryLength = 0;
        n.f6.accept(this, argu);
        if (argu.vTableEntry.endsWith(", ")) argu.vTableEntry = argu.vTableEntry.substring(0, argu.vTableEntry.length() - 2);
        VTEntry entrySuper;
        if ((entrySuper = vTableEntries.get(extnd)) == null) throw new Exception();
        // clean vTableSuper and vTableThis
        String vTableEntryNew = entrySuper.entry + ", " + argu.vTableEntry;
        int vTableEntrySize = entrySuper.size + argu.entryLength;
        if (vTableEntries.put(argu.scope, new VTEntry(vTableEntryNew, vTableEntrySize)) != null) throw new Exception();
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("@." + argu.scope + "_vtable = global [" + vTableEntrySize + " x i8*] [" + vTableEntryNew + "]");
        writer.close();
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
    public String visit(MethodDeclaration n, VTArgs argu) throws Exception {
        String retType = n.f1.accept(this, argu), methName = n.f2.accept(this, argu);
        String vTableEntry = "@." + argu.scope + "_vtable = global [";

        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("\ndefine " + getTypeLLVMA(retType) + " @" + argu.scope + "." + methName + "(i8* %this");
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