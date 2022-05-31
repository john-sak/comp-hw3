import syntaxtree.*;
import visitor.*;

import java.io.FileWriter;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

class CLLVMArgs {
    String scope = null, resReg = null, resType = null;
    FileWriter writer = null;
    Map<String, classInfo> symbolTable = null;
    Map<String, OTEntry> offsetTable = null;
    int tabs = 0, regCount = 0, ifCount = 0, loopCount = 0;

    public void writeLine(String str) throws Exception {
        this.writer.write("\t".repeat(this.tabs) + str + "\n");
        return;
    }

    public void writeLine(String str, int tabs) throws Exception {
        this.writer.write("\t".repeat(tabs) + str + "\n");
        return;
    }
}

class paramInfoNode {
    String identifier, type;

    paramInfoNode(String identifier, String type) {
        this.identifier = identifier;
        this.type = type;
    }
}

class compileLLVMVisitor extends GJDepthFirst<String, CLLVMArgs> {

    public String getTypeLLVMA(String type) throws Exception {
        if (type.compareTo("boolean[]") == 0) return "i1*";
        else if (type.compareTo("int[]") == 0) return "i32*";
        else if (type.compareTo("boolean") == 0) return "i1";
        else if (type.compareTo("int") == 0) return "i32";
        else return "i8*";
    }

    public String resolveIdentifier(String ID, CLLVMArgs argu) throws Exception {
        String[] scope = argu.scope.split("->");
        classInfo classI;
        if ((classI = argu.symbolTable.get(scope[0])) == null) throw new Exception();
        if (argu.scope.contains("->")) {
            methodInfo methodI;
            if ((methodI = classI.methods.get(scope[1])) == null) throw new Exception();
            if (ID.compareTo("this") == 0) return classI.name;
            fieldInfo fieldI;
            if ((fieldI = methodI.localVars.get(ID)) != null) return fieldI.type;
            while (classI != null) {
                if ((fieldI = classI.fields.get(ID)) != null) return fieldI.type;
                classI = classI.superclass;
            }
        }
        throw new Exception();
    }

    public int getOffsetVar(String identifier, CLLVMArgs argu) throws Exception {
        // String[] scope = argu.scope.split("->");
        // if (scope.length != 2) throw new Exception();
        // classInfo thisClass = argu.symbolTable.get(scope[0]);
        // int offset = -1;
        // while (thisClass != null) {
        //     OTEntry entry = argu.offsetTable.get(scope[0]);
        //     if (entry == null) throw new Exception();
        //     for (OTData data : entry.variables) {

        //     }
        // }
        // if (offset < 0) throw new Exception();
        // else return offset;
        return 0;
    }

    public int getOffsetMeth(String identifier, CLLVMArgs argu) throws Exception {
        String[] scope = argu.scope.split("->");
        if (scope.length != 2) throw new Exception();
        classInfo thisClass = argu.symbolTable.get(scope[0]);
        while (thisClass != null) {
            OTEntry entry = argu.offsetTable.get(scope[0]);
            for (OTData data : entry.methods) if (data.identifier.compareTo(scope[1]) == 0) return data.offset / 8;
            thisClass = thisClass.superclass;
        }
        throw new Exception();
    }

    public void arrayLookup(String identifier, CLLVMArgs argu) throws Exception {
        throw new Exception();
    }

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    @Override
    public String visit(Goal n, CLLVMArgs argu) throws Exception {
        if (argu.writer == null) throw new Exception();
        if (argu.symbolTable == null) throw new Exception();
        if (argu.offsetTable == null) throw new Exception();
        argu.writeLine("declare i8* @calloc(i32, i32)");
        argu.writeLine("declare i32 @printf(i8*, ...)");
        argu.writeLine("declare void @exit(i32)\n");
        argu.writeLine("@_cint = constant [4 x i8] c\"%d\\0a\\00\"");
        argu.writeLine("@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n");
        argu.writeLine("define void @print_int(i32 %i) {");
        argu.tabs++;
        argu.writeLine("%_str = bitcast [4 x i8]* @_cint to i8*");
        argu.writeLine("call i32 (i8*, ...) @printf(i8* %_str, i32 %i)");
        argu.writeLine("ret void");
        argu.tabs--;
        argu.writeLine("}\n");
        argu.writeLine("define voide @throw_oob() {");
        argu.tabs++;
        argu.writeLine("%_str = bitcast [15 x i8]* @cOOB to i8*");
        argu.writeLine("call i32 (i8*, ...) @printf(i8* %_str)");
        argu.writeLine("call void @exit(i32 1)");
        argu.writeLine("ret void");
        argu.tabs--;
        argu.writeLine("}\n");
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
        argu.writeLine("define i32 @main() {");
        String oldArgu = argu.scope;
        argu.scope = n.f1.accept(this, argu) + "->main";
        int oldRegCount = argu.regCount;
        argu.regCount = 0;
        argu.tabs++;
        n.f14.accept(this, argu);
        n.f15.accept(this, argu);
        argu.writeLine("ret i32 0");
        argu.tabs--;
        argu.writeLine("}\n");
        argu.regCount = oldRegCount;
        argu.scope = oldArgu;
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
        if (!argu.scope.contains("->")) throw new Exception();
        String type = n.f0.accept(this, argu), identifier = n.f1.accept(this, argu);
        argu.writeLine("%" + identifier + " = alloca " + getTypeLLVMA(type));
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
        String oldArgu = argu.scope, methName = n.f2.accept(this, argu);
        argu.scope += "->" + methName;
        int oldRegCount = argu.regCount;
        argu.regCount = 0;
        classInfo classI;
        if ((classI = argu.symbolTable.get(oldArgu)) == null) throw new Exception();
        methodInfo methodI;
        if ((methodI = classI.methods.get(methName)) == null) throw new Exception();
        argu.writeLine("define " + getTypeLLVMA(methodI.returnValue) + " @" + oldArgu + "." + methName + "(i8* %this");
        List<paramInfoNode> listInfo = new ArrayList<paramInfoNode>();
        if (n.f4.present())
        for (String param : n.f4.accept(this, argu).split(", ")) {
            String[] temp = param.split(" ");
            if (temp.length != 2) throw new Exception();
            listInfo.add(new paramInfoNode(temp[1], temp[0]));
            argu.writer.write(", " + getTypeLLVMA(temp[0]) + " %." + temp[1]);
        }
        argu.writer.write(") {\n");
        argu.tabs++;
        for (paramInfoNode node : listInfo) {
            String typeLLVM = getTypeLLVMA(node.type);
            argu.writeLine("%" + node.identifier + " = alloca " + typeLLVM);
            argu.writeLine("store " + typeLLVM + " %." + node.identifier + ", " + typeLLVM + "* %" + node.identifier);
        }
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        // argu.writer.write("\tret " + n.f10.accept(this, argu) + ";\n}\n");
        n.f10.accept(this, argu);
        argu.writeLine("ret " + argu.resType + " " + argu.resReg);
        argu.tabs--;
        argu.writeLine("}\n");
        argu.regCount = oldRegCount;
        argu.scope = oldArgu;
        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, CLLVMArgs argu) throws Exception {
        return n.f0.accept(this, argu) + n.f1.accept(this, argu);
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, CLLVMArgs argu) throws Exception {
        return n.f0.accept(this, argu) + " " + n.f1.accept(this, argu);
    }

    /**
     * f0 -> ( FormalParameterTerm() )*
     */
    @Override
    public String visit(FormalParameterTail n, CLLVMArgs argu) throws Exception {
        return n.f0.present() ? n.f0.accept(this, argu) : "";
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTerm n, CLLVMArgs argu) throws Exception {
        return ", " + n.f1.accept(this, argu);
    }

    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    @Override
    public String visit(BooleanArrayType n, CLLVMArgs argu) throws Exception {
        return "boolean[]";
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    @Override
    public String visit(IntegerArrayType n, CLLVMArgs argu) throws Exception {
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    @Override
    public String visit(BooleanType n, CLLVMArgs argu) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> "int"
     */
    @Override
    public String visit(IntegerType n, CLLVMArgs argu) throws Exception {
        return "int";
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    @Override
    public String visit(Block n, CLLVMArgs argu) throws Exception {
        argu.tabs++;
        n.f1.accept(this, argu);
        argu.tabs--;
        return null;
    }
    
    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    @Override
    public String visit(AssignmentStatement n, CLLVMArgs argu) throws Exception {
        if (!argu.scope.contains("->")) throw new Exception();
        String[] scope = argu.scope.split("->");
        classInfo classI;
        if ((classI = argu.symbolTable.get(scope[0])) == null) throw new Exception();
        methodInfo methodI;
        if ((methodI = classI.methods.get(scope[1])) == null) throw new Exception();
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        String ID = n.f0.accept(this, argu), hereType = getTypeLLVMA(resolveIdentifier(ID, argu)), hereLoc;
        if (methodI.localVars.containsKey(ID)) hereLoc = "%" + ID;
        else {
            argu.writeLine("%_" + argu.regCount++ + " = getelementptr i8, i8* this, i32 " + getOffsetVar(ID, argu));
            argu.writeLine("%_" + argu.regCount++ + " = bitcast i8* %_" + (argu.regCount - 2) + " to " + hereType + "*");
            hereLoc = "%_" + (argu.regCount - 1);
        }
        argu.writeLine("store " + argu.resType + " " + argu.resReg + ", " + hereType + "* " + hereLoc);
        return null;
    }
    
    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    @Override
    public String visit(ArrayAssignmentStatement n, CLLVMArgs argu) throws Exception {
        // if (!argu.scope.contains("->")) throw new Exception();
        // String[] scope = argu.scope.split("->");
        // classInfo classI;
        // if ((classI = argu.symbolTable.get(scope[0])) == null) throw new Exception();
        // methodInfo methodI;
        // if ((methodI = classI.methods.get(scope[1])) == null) throw new Exception();
        String ID = n.f0.accept(this, argu), hereType, hereLoc;
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        arrayLookup(ID, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        hereLoc = argu.resReg;
        hereType = argu.resType;
        n.f5.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        // hereType = argu.resType OR hereType = getTypeLLVMA(resolveIdentifier(ID, argu)) ?
        argu.writeLine("store " + argu.resType + " " + argu.resReg + ", " + hereType + "* " + hereLoc);
        return null;
    }
    
    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    @Override
    public String visit(IfStatement n, CLLVMArgs argu) throws Exception {
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        String label1 = "%if" + argu.ifCount++, label2 = "%if" + argu.ifCount++, label3 = "%if" + argu.ifCount++;
        argu.writeLine("br " + argu.resType + " " + argu.resReg + ", label %" + label1 + ", label %" + label2);
        argu.tabs++;
        argu.writeLine(label1 + ":", 0);
        n.f4.accept(this, argu);
        argu.writeLine("br label %" + label3);
        argu.writeLine(label2 + ":", 0);
        n.f5.accept(this, argu);
        argu.writeLine("br label %" + label3);
        argu.tabs--;
        argu.writeLine(label3 + ":", 0);
        return null;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    @Override
    public String visit(WhileStatement n, CLLVMArgs argu) throws Exception {
        String label1 = "%loop" + argu.loopCount++, label2 = "%loop" + argu.loopCount++, label3 = "%loop" + argu.loopCount++;
        argu.writeLine("br label " + label1);
        argu.writeLine(label1 + ":", 0);
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        argu.writeLine("br " + argu.resType + " " + argu.resReg + ", label " + label2 + ", label " + label3);
        argu.tabs++;
        argu.writeLine(label2 + ":", 0);
        n.f4.accept(this, argu);
        argu.writeLine("br label " + label1);
        argu.tabs--;
        argu.writeLine(label3 + ":", 0);
        return null;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    @Override
    public String visit(PrintStatement n, CLLVMArgs argu) throws Exception {
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        argu.writeLine("call void (i32) @print_int(i32 " + argu.resReg + ")");
        return null;
    }

    /**
     * f0 -> AndExpression()
     *       | CompareExpression()
     *       | PlusExpression()
     *       | MinusExpression()
     *       | TimesExpression()
     *       | ArrayLookup()
     *       | ArrayLength()
     *       | MessageSend()
     *       | Clause()
     */

    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Identifier n, CLLVMArgs argu) throws Exception {
        return n.f0.toString();
    }
}
