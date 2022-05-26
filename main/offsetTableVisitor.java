import syntaxtree.*;
import visitor.*;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

class OTData {
    String scope, identifier;
    int offset;

    OTData(String scope, String name, int position) {
        this.scope = scope;
        this.identifier = name;
        this.offset = position;
    }
}

class OTEntry {
    String className;
    List<OTData> variables = new ArrayList<OTData>();
    List<OTData> methods = new ArrayList<OTData>();
    int posV = 0, posM = 0;

    OTEntry(String name) {
        this.className = name;
    }
}

class OTArgs {
    Map<String, classInfo> symbolTable = null;
    String scope = null;
}

class offsetTableVisitor extends GJDepthFirst<String, OTArgs> {
    Map<String, OTEntry> stack = new HashMap<String, OTEntry>();

    public void printResult() {
        for (Map.Entry<String, OTEntry> entry : this.stack.entrySet()) {
            System.out.println("-----------" + entry.getKey() + "-----------");
            OTEntry value = entry.getValue();
            System.out.println("--Variables--");
            for (OTData data : value.variables) System.out.println(data.scope + "." + data.identifier + " : " + data.offset);
            System.out.println("---Methods---");
            for (OTData data : value.methods) System.out.println(data.scope + "." + data.identifier + " : " + data.offset);
        }
        return;
    }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, OTArgs argu) throws Exception {
        if (argu.symbolTable == null) throw new Exception();
        n.f1.accept(this, argu);
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
    public String visit(ClassDeclaration n, OTArgs argu) throws Exception {
        String oldArgu = argu.scope;
        String className = n.f1.accept(this, null);
        argu.scope = className;
        this.stack.put(className, new OTEntry(className));
        n.f3.accept(this, argu);
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
    public String visit(ClassExtendsDeclaration n, OTArgs argu) throws Exception {
        String oldArgu = argu.scope;
        String className = n.f1.accept(this, null);
        argu.scope = className;
        this.stack.put(className, new OTEntry(className));
        n.f5.accept(this, argu);
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
    public String visit(VarDeclaration n, OTArgs argu) throws Exception {
        if (argu.scope.contains("->")) return null;
        classInfo classI;
        if ((classI = argu.symbolTable.get(argu.scope)) == null) throw new Exception();
        String type = n.f0.accept(this, null), name = n.f1.accept(this, null);
        while ((classI = classI.superclass) != null) if (classI.fields.containsKey(name)) return null;
        OTEntry entry;
        if ((entry = this.stack.get(argu.scope)) == null) throw new Exception();
        entry.variables.add(new OTData(argu.scope, name, entry.posV));
        int offset;
        if (type.compareTo("boolean[]") == 0 || type.compareTo("int[]") == 0 || argu.symbolTable.containsKey(type)) offset = 8;
        else if (type.compareTo("boolean") == 0) offset = 1;
        else if (type.compareTo("int") == 0) offset = 4;
        else throw new Exception();
        entry.posV += offset;
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
    public String visit(MethodDeclaration n, OTArgs argu) throws Exception {
        if (argu.scope.contains("->")) return null;
        classInfo classI;
        if ((classI = argu.symbolTable.get(argu.scope)) == null) throw new Exception();
        String name = n.f2.accept(this, null);
        while ((classI = classI.superclass) != null) if (classI.methods.containsKey(name)) return null;
        OTEntry entry;
        if ((entry = this.stack.get(argu.scope)) == null) throw new Exception();
        entry.methods.add(new OTData(argu.scope, name, entry.posM));
        entry.posM += 8;
        return null;
    }

    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    @Override
    public String visit(BooleanArrayType n, OTArgs argu) throws Exception {
        return "boolean[]";
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    @Override
    public String visit(IntegerArrayType n, OTArgs argu) throws Exception {
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    @Override
    public String visit(BooleanType n, OTArgs argu) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> "int"
     */
    @Override
    public String visit(IntegerType n, OTArgs argu) throws Exception {
        return "int";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Identifier n, OTArgs argu) throws Exception {
        return n.f0.toString();
    }
}