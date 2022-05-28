import syntaxtree.*;
import visitor.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

class VTArgs {
    String fileName;
    Map<String, classInfo> symbolTable;
    Map<String, OTEntry> offsetTable;
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
        if (argu.symbolTable == null) throw new Exception();
        if (argu.offsetTable == null) throw new Exception();
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
        System.out.print("wrote\n" + "@." + n.f1.accept(this, argu) + "_vtable = global [0 x i8*] []\n");
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
        String scope = n.f1.accept(this, argu);
        String vTEInfo = "";
        int vTESize = 0;
        classInfo classI;
        if ((classI = argu.symbolTable.get(scope)) == null) throw new Exception();
        OTEntry OTE;
        if ((OTE = argu.offsetTable.get(scope)) == null) throw new Exception();
        int size = OTE.methods.size();
        for (OTData data : OTE.methods) {
            methodInfo methodI;
            if ((methodI = classI.methods.get(data.identifier)) == null) throw new Exception();
            vTEInfo += "i8* bitcast (" + getTypeLLVMA(methodI.returnValue) + " (i8*";
            if (methodI.argNum != 0) {
                String[] args = methodI.argTypes.split(", ");
                for (String arg : args) vTEInfo += ", " + getTypeLLVMA(arg);
            }
            vTEInfo += ")* @" + scope + "." + data.identifier + " to i8*)";
            if (++vTESize < size) vTEInfo += ", ";
        }
        if (vTableEntries.put(scope, new VTEntry(vTEInfo, vTESize)) != null) throw new Exception();
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("@." + scope + "_vtable = global [" + vTESize + " x i8*] [" + vTEInfo + "]\n");
        writer.close();
        System.out.print("wrote\t" + "@." + scope + "_vtable = global [" + vTESize + " x i8*] [" + vTEInfo + "]\n");
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
        String superClass = n.f3.accept(this, argu);
        VTEntry entry;
        if ((entry = vTableEntries.get(superClass)) == null) throw new Exception();
        // String vTEInfo = new String(entry.entry);
        // int vTESize = new Integer(entry.size);
        String vTEInfo = entry.entry;
        int vTESize = entry.size;
        String scope = n.f1.accept(this, argu);
        classInfo classI;
        if ((classI = argu.symbolTable.get(scope)) == null) throw new Exception();
        OTEntry OTE;
        if ((OTE = argu.offsetTable.get(scope)) == null) throw new Exception();
        int size = OTE.methods.size();
        for (OTData data : OTE.methods) {
            methodInfo methodI;
            if ((methodI = classI.methods.get(data.identifier)) == null) throw new Exception();
            vTEInfo += "i8* bitcast (" + getTypeLLVMA(methodI.returnValue) + " (i8*";
            if (methodI.argNum != 0) {
                String[] args = methodI.argTypes.split(", ");
                for (String arg : args) vTEInfo += ", " + getTypeLLVMA(arg);
            }
            vTEInfo += ")* @" + scope + "." + data.identifier + " to i8*)";
            if (++vTESize < entry.size + size) vTEInfo += ", ";
        }
        if (vTableEntries.put(scope, new VTEntry(vTEInfo, vTESize)) != null) throw new Exception();
        FileWriter writer = new FileWriter(argu.fileName);
        writer.write("@." + scope + "_vtable = global [" + vTESize + " x i8*] [" + vTEInfo + "]\n");
        writer.close();
        System.out.print("wrote\t" + "@." + scope + "_vtable = global [" + vTESize + " x i8*] [" + vTEInfo + "]\n");
        return null;
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Identifier n, VTArgs argu) throws Exception {
        return n.f0.toString();
    }
}