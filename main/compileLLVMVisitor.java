import syntaxtree.*;
import visitor.*;

import java.io.FileWriter;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;

class CLLVMArgs {
    String scope = null, resReg = null, resType = null;
    FileWriter writer = null;
    Map<String, classInfo> symbolTable = null;
    Map<String, OTEntry> offsetTable = null;
    Map<String, VTEntry> vTableSizes = null;
    int tabs = 0, regCount = 0, ifCount = 0, loopCount = 0, andCount = 0, oobCount = 0, arrayCount = 0;

    public void writeLine(String str) throws Exception {
        this.writer.write("\t".repeat(this.tabs) + str + "\n");
        return;
    }

    public void writeLabel(String str) throws Exception {
        this.writer.write(str + ":\n");
        return;
    }
}

class paramInfoNode {
    String identifier, type;

    paramInfoNode(String identifier, String type) {
        this.identifier = identifier;
        this.type = type;
        return;
    }
}

class compileLLVMVisitor extends GJDepthFirst<String, CLLVMArgs> {

    public String getTypeLLVM(String type) throws Exception {
        if (type.compareTo("boolean[]") == 0) return "%_BooleanArray";
        else if (type.compareTo("int[]") == 0) return "%_IntegerArray";
        else if (type.compareTo("boolean") == 0) return "i1";
        else if (type.compareTo("int") == 0) return "i32";
        else return "i8*";
    }

    public void getIdentifier(String identifier, CLLVMArgs argu) throws Exception {
        if (!argu.scope.contains("->")) throw new Exception();
        String[] scope = argu.scope.split("->");
        classInfo classI;
        if ((classI = argu.symbolTable.get(scope[0])) == null) throw new Exception();
        methodInfo methodI;
        if ((methodI = classI.methods.get(scope[1])) == null) throw new Exception();
        argu.resType = getTypeLLVM(resolveIdentifier(identifier, argu)) + "*";
        if (methodI.localVars.containsKey(identifier)) argu.resReg = "%" + identifier;
        else {
            String reg1 = "%_" + argu.regCount++, reg2 = "%_" + argu.regCount++;
            argu.writeLine(reg1 + " = getelementptr i8, i8* %this, i32 " + getOffsetVar(identifier, argu));
            argu.writeLine(reg2 + " = bitcast i8* " + reg1 + " to " + argu.resType);
            argu.resReg = reg2;
        }
        return;
    }

    public String resolveIdentifier(String ID, CLLVMArgs argu) throws Exception {
        if (!argu.scope.contains("->")) throw new Exception();
        String[] scope = argu.scope.split("->");
        classInfo classI;
        if ((classI = argu.symbolTable.get(scope[0])) == null) throw new Exception();
        if (ID.compareTo("this") == 0) return classI.name;
        methodInfo methodI;
        if ((methodI = classI.methods.get(scope[1])) == null) throw new Exception();
        fieldInfo fieldI;
        if ((fieldI = methodI.localVars.get(ID)) != null) return fieldI.type;
        while (classI != null) {
            if ((fieldI = classI.fields.get(ID)) != null) return fieldI.type;
            classI = classI.superclass;
        }
        throw new Exception();
    }

    public int getOffsetVar(String identifier, CLLVMArgs argu) throws Exception {
        return 0;
    }

    public int getOffsetMeth(String identifier, CLLVMArgs argu) throws Exception {
        String[] scope = argu.scope.split("->");
        if (scope.length != 2) throw new Exception();
        classInfo thisClass = argu.symbolTable.get(scope[0]);
        while (thisClass != null) {
            OTEntry entry = argu.offsetTable.get(scope[0]);
            for (OTData data : entry.methods)
                if (data.identifier.compareTo(scope[1]) == 0) {
                    methodInfo methodI;
                    if ((methodI = thisClass.methods.get(scope[1])) == null) throw new Exception();
                    argu.resReg = getTypeLLVM(methodI.returnValue);
                    argu.resType = "i8*";
                    if (methodI.argNum > 0) for (String type : methodI.argTypes.split(", ")) argu.resType += ", " + getTypeLLVM(type);
                    return data.offset / 8;
                }
            thisClass = thisClass.superclass;
        }
        throw new Exception();
    }
    
    public int getSizeClass(String identifier, CLLVMArgs argu) throws Exception {
        String oldArgu = argu.scope;
        int size = 8;
        classInfo thisClass = argu.symbolTable.get(identifier);
        while (thisClass != null) {
            OTEntry entry = argu.offsetTable.get(thisClass.name);
            for (OTData data : entry.variables) {
                argu.scope = data.scope;
                String type = resolveIdentifier(data.identifier, argu);
                if (type.compareTo("boolean") == 0) size += 1;
                else if (type.compareTo("int") == 0) size += 4;
                else size += 8;
            }
            thisClass = thisClass.superclass;
        }
        argu.scope = oldArgu;
        return size;
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
        if (argu.vTableSizes == null) throw new Exception();
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
        argu.tabs++;
        argu.regCount = 0;
        n.f14.accept(this, argu);
        n.f15.accept(this, argu);
        argu.writeLine("ret i32 0");
        argu.tabs--;
        argu.writeLine("}\n");
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
        argu.writeLine("%" + identifier + " = alloca " + getTypeLLVM(type));
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
        argu.regCount = 0;
        classInfo classI;
        if ((classI = argu.symbolTable.get(oldArgu)) == null) throw new Exception();
        methodInfo methodI;
        if ((methodI = classI.methods.get(methName)) == null) throw new Exception();
        argu.writeLine("define " + getTypeLLVM(methodI.returnValue) + " @" + oldArgu + "." + methName + "(i8* %this");
        List<paramInfoNode> listInfo = new ArrayList<paramInfoNode>();
        if (n.f4.present())
            for (String param : n.f4.accept(this, argu).split(", ")) {
                String[] temp = param.split(" ");
                if (temp.length != 2) throw new Exception();
                listInfo.add(new paramInfoNode(temp[1], temp[0]));
                argu.writer.write(", " + getTypeLLVM(temp[0]) + " %." + temp[1]);
            }
        argu.writer.write(") {\n");
        argu.tabs++;
        for (paramInfoNode node : listInfo) {
            String typeLLVM = getTypeLLVM(node.type);
            argu.writeLine("%" + node.identifier + " = alloca " + typeLLVM);
            argu.writeLine("store " + typeLLVM + " %." + node.identifier + ", " + typeLLVM + "* %" + node.identifier);
        }
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f10.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        argu.writeLine("ret " + argu.resType + " " + argu.resReg);
        argu.tabs--;
        argu.writeLine("}\n");
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
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        String exprReg = argu.resReg, exprType = argu.resType;
        getIdentifier(n.f0.accept(this, argu), argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        argu.writeLine("store " + exprType + " " + exprReg + ", " + argu.resType + " " + argu.resReg);
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
        getIdentifier(n.f0.accept(this, argu), argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (!argu.resType.endsWith("Array*")) throw new Exception();
        String idReg = argu.resReg, idTypeNoPtr = argu.resType.substring(0, argu.resType.length() - 1), arrayType = idTypeNoPtr.compareTo("%_BooleanArray") == 0 ? "i1" : "i32";
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String exprReg = argu.resReg, reg1 = "%_" + argu.regCount++, reg2 = "%_" + argu.regCount++, reg3 = "%_" + argu.regCount++;
        String label1 = "oob" + argu.oobCount++, label2 = "oob" + argu.oobCount++, label3 = "oob" + argu.oobCount++;
        argu.writeLine(reg1 + " = getelementptr " + idTypeNoPtr + ", " + idTypeNoPtr + "* " + idReg + ", i32 0, i32 0");
        argu.writeLine(reg2 + " = load i32, i32* " + reg1);
        argu.writeLine(reg3 + " = icmp ult i32 " + exprReg + ", " + reg2);
        argu.writeLine("br i1 " + reg3 + ", label %" + label1 + ", label %" + label2);
        argu.writeLabel(label1);
        String reg4 = "%_" + argu.regCount++, reg5 = "%_" + argu.regCount++;
        argu.writeLine(reg4 + " = getelementptr " + idTypeNoPtr + ", " + idTypeNoPtr + "* " + idReg + ", i32 0, i32 1");
        argu.writeLine(reg5 + " = getelementptr " + arrayType + ", " + arrayType + "* " + reg4 + ", i32 " + reg2);
        n.f5.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        argu.writeLine("store " + argu.resType + " " + argu.resReg + ", " + arrayType + "* " + reg5);
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label2);
        argu.writeLine("call void @throw_oob()");
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label3);
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
        if (argu.resType.compareTo("i1") != 0) throw new Exception();
        String label1 = "if" + argu.ifCount++, label2 = "if" + argu.ifCount++, label3 = "if" + argu.ifCount++;
        argu.writeLine("br i1 " + argu.resReg + ", label %" + label1 + ", label %" + label2);
        argu.tabs++;
        argu.writeLabel(label1);
        n.f4.accept(this, argu);
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label2);
        n.f5.accept(this, argu);
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label3);
        argu.tabs--;
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
        String label1 = "loop" + argu.loopCount++, label2 = "loop" + argu.loopCount++, label3 = "loop" + argu.loopCount++;
        argu.writeLine("br label %" + label1);
        argu.writeLabel(label1);
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i1") != 0) throw new Exception();
        argu.writeLine("br i1 " + argu.resReg + ", label %" + label2 + ", label %" + label3);
        argu.tabs++;
        argu.writeLabel(label2);
        n.f4.accept(this, argu);
        argu.writeLine("br label %" + label1);
        argu.writeLabel(label3);
        argu.tabs--;
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
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    @Override
    public String visit(AndExpression n, CLLVMArgs argu) throws Exception {
        String label1 = "andclause" + argu.andCount++, label2 = "andclause" + argu.andCount++, label3 = "andclause" + argu.andCount++, label4 = "andclause" + argu.andCount++;
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i1") != 0) throw new Exception();
        String cl1Reg = argu.resReg;
        argu.writeLabel("br label %" + label1);
        argu.writeLabel(label1);
        argu.writeLine("br i1 " + cl1Reg + ", label %" + label2 + ", label %" + label3);
        argu.writeLabel(label2);
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i1") != 0) throw new Exception();
        String cl2Reg = argu.resReg;
        argu.writeLine("br label %" + label4);
        argu.writeLabel(label4);
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label3);
        String resRes = "%_" + argu.regCount++;
        argu.writeLine(resRes + " = phi i1 [ 0, %" + label1 + " ], [ " + cl2Reg + ", %" + label4 + " ]");
        argu.resReg = resRes;
        argu.resType = "i1";
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(CompareExpression n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr1Reg = argu.resReg;
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr2Reg = argu.resReg, resReg = "%_" + argu.regCount++;
        argu.writeLine(resReg + " = icmp slt i32 " + expr1Reg + ", " + expr2Reg);
        argu.resReg = resReg;
        argu.resType = "i1";
        return null;
    }
    
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(PlusExpression n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr1Reg = argu.resReg;
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr2Reg = argu.resReg, resReg = "%_" + argu.regCount++;
        argu.writeLine(resReg + " = add i32 " + expr1Reg + ", " + expr2Reg);
        argu.resReg = resReg;
        argu.resType = "i32";
        return null;
    }
    
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(MinusExpression n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr1Reg = argu.resReg;
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr2Reg = argu.resReg, resReg = "%_" + argu.regCount++;
        argu.writeLine(resReg + " = sub i32 " + expr1Reg + ", " + expr2Reg);
        argu.resReg = resReg;
        argu.resType = "i32";
        return null;
    }
    
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(TimesExpression n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr1Reg = argu.resReg;
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr2Reg = argu.resReg, resReg = "%_" + argu.regCount++;
        argu.writeLine(resReg + " = mul i32 " + expr1Reg + ", " + expr2Reg);
        argu.resReg = resReg;
        argu.resType = "i32";
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    @Override
    public String visit(ArrayLookup n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (!argu.resType.endsWith("Array*")) throw new Exception();
        String expr1Reg = argu.resReg, expr1TypeNoPtr = argu.resType.substring(0, argu.resType.length() - 1), arrayType = expr1TypeNoPtr.compareTo("%_BooleanArray") == 0 ? "i1" : "i32";
        n.f2.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String expr2Reg = argu.resReg, reg1 = "%_" + argu.regCount++, reg2 = "%_" + argu.regCount++, reg3 = "%_" + argu.regCount++;
        String label1 = "oob" + argu.oobCount++, label2 = "oob" + argu.oobCount++, label3 = "oob" + argu.oobCount++;
        argu.writeLine(reg1 + " = getelementptr " + expr1TypeNoPtr + ", " + expr1TypeNoPtr + "* " + expr1Reg + ", i32 0, i32 0");
        argu.writeLine(reg2 + " = load i32, i32* " + reg1);
        argu.writeLine(reg3 + " = icmp ult i32 " + expr2Reg + ", " + reg2);
        argu.writeLine("br i1 " + reg3 + ", label %" + label1 + ", label %" + label2);
        argu.writeLabel(label1);
        String reg4 = "%_" + argu.regCount++, reg5 = "%_" + argu.regCount++, reg6 = "%_" + argu.regCount++;
        argu.writeLine(reg4 + " = getelementptr " + expr1TypeNoPtr + ", " + expr1TypeNoPtr + "* " + expr1Reg + ", i32 0, i32 1");
        argu.writeLine(reg5 + " = getelementptr " + arrayType + ", " + arrayType + "* " + reg4 + ", i32 " + reg2);
        argu.writeLine(reg6 + " = load " + arrayType + ", " + arrayType + "* " + reg5);
        String resReg = reg6;
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label2);
        argu.writeLine("call void @throw_oob()");
        argu.writeLine("br label %" + label3);
        argu.writeLabel(label3);
        argu.resReg = resReg;
        argu.resType = arrayType;
        return null;
    }
    
    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    @Override
    public String visit(ArrayLength n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (!argu.resType.endsWith("Array*")) throw new Exception();
        String expr1Reg = argu.resReg, expr1TypeNoPtr = argu.resType.substring(0, argu.resType.length() - 1);
        String reg1 = "%_" + argu.regCount++, reg2 = "%_" + argu.regCount++;
        argu.writeLine(reg1 + " = getelementptr " + expr1TypeNoPtr + ", " + expr1TypeNoPtr + "* " + expr1Reg + ", i32 0, i32 0");
        argu.writeLine(reg2 + " = load i32, i32* " + reg1);
        argu.resReg = reg2;
        argu.resType = "i32";
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    @Override
    public String visit(MessageSend n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i8*") != 0) throw new Exception();
        String exprReg = argu.resReg;
        String reg1 = "%_" + argu.regCount++, reg2 = "%_" + argu.regCount++, reg3 = "%_" + argu.regCount++;
        argu.writeLine(reg1 + " = bitcast i8* " + exprReg + " to i8***");
        argu.writeLine(reg2 + " = load i8**, i8*** " + reg1);
        argu.writeLine(reg3 + " = getelementptr i8*, i8** " + reg2 + ", i32 " + getOffsetMeth(n.f2.accept(this, argu), argu));
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        String retType = argu.resReg, args = argu.resType;
        String reg4 = "%_" + argu.regCount++, reg5 = "%_" + argu.regCount++, reg6 = "%_" + argu.regCount++;
        argu.writeLine(reg4 + " = load i8*, i8** " + reg3);
        argu.writeLine(reg5 + " = bitcast i8* " + reg4 + "to " + retType + " (" + args + ")*");
        argu.writeLine(reg6 + " = call " + retType + " " + reg5 + "(i8* " + exprReg + (n.f4.present() ? "" : ", " + n.f4.accept(this, argu)) + ")");
        argu.resReg = reg6;
        argu.resType = retType;
        return null;
    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    @Override
    public String visit(ExpressionList n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        return argu.resType + " " + argu.resReg + n.f1.accept(this, argu);
    }

    /**
     * f0 -> ( ExpressionTerm() )*
     */
    @Override
    public String visit(ExpressionTail n, CLLVMArgs argu) throws Exception {
        return n.f0.present() ? n.f0.accept(this, argu) : "";
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    @Override
    public String visit(ExpressionTerm n, CLLVMArgs argu) throws Exception {
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        return ", " + argu.resType + " " + argu.resReg;
    }

    /**
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | BracketExpression()
     */
    @Override
    public String visit(PrimaryExpression n, CLLVMArgs argu) throws Exception {
        String identifier = n.f0.accept(this, argu);
        if (identifier != null) {
            getIdentifier(identifier, argu);
            if (argu.resReg == null || argu.resType == null) throw new Exception();
            String typeNoPtr = argu.resType.substring(0, argu.resType.length() - 1), reg = "%_" + argu.regCount++;
            argu.writeLine(reg + " = load " + typeNoPtr + ", " + typeNoPtr + "* " + argu.resReg);
            argu.resReg = reg;
            argu.resType = typeNoPtr;
        }
        return null;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    @Override
    public String visit(IntegerLiteral n, CLLVMArgs argu) throws Exception {
        argu.resReg = n.f0.toString();
        argu.resType = "i32";
        return null;
    }
    
    /**
     * f0 -> "true"
     */
    @Override
    public String visit(TrueLiteral n, CLLVMArgs argu) throws Exception {
        argu.resReg = "1";
        argu.resType = "i1";
        return null;
    }
    
    /**
     * f0 -> "false"
     */
    @Override
    public String visit(FalseLiteral n, CLLVMArgs argu) throws Exception {
        argu.resReg = "0";
        argu.resType = "i1";
        return null;
    }
    
    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Identifier n, CLLVMArgs argu) throws Exception {
        return n.f0.toString();
    }

    /**
     * f0 -> "this"
     */
    @Override
    public String visit(ThisExpression n, CLLVMArgs argu) throws Exception {
        argu.resReg = "%this";
        argu.resType = "i8*";
        return null;
    }

    /**
     * f0 -> "new"
     * f1 -> "boolean"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    @Override
    public String visit(BooleanArrayAllocationExpression n, CLLVMArgs argu) throws Exception {
        n.f3.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String exprReg = argu.resReg, reg1 = "%_" + argu.regCount++;
        String label1 = "%arr_alloc" + argu.arrayCount++, label2 = "%arr_alloc" + argu.arrayCount++;
        argu.writeLine(reg1 + " = icmp slt i32 " + exprReg + ", 0");
        argu.writeLine("br i1 " + reg1 + ", label %" + label1 + ", label %" + label2);
        argu.writeLabel(label1);
        argu.writeLine("call void @throw_oob()");
        argu.writeLine("br label %" + label2);
        argu.writeLabel(label2);
        String reg2 = "%_" + argu.regCount++, reg3 = "%_" + argu.regCount++, reg4 = "%_" + argu.regCount++, reg5 = "%_" + argu.regCount++, reg6 = "%_" + argu.regCount++, reg7 = "%_" + argu.regCount++, reg8 = "%_" + argu.regCount++, reg9 = "%_" + argu.regCount++;
        argu.writeLine(reg2 + " = call i8* @calloc(i32 1, i32 12)"); // i32 = 4b, i1* = 8b
        argu.writeLine(reg3 + " = bitcast i8* " + reg2 + " to %_BooleanArray*");
        String resReg = reg3;
        argu.writeLine(reg4 + " = getelementptr %_BooleanArray, %_BooleanArray* " + reg3 + ", i32 0, i32 0");
        argu.writeLine(reg5 + " = store i32 " + exprReg + ", i32 *" + reg4);
        argu.writeLine(reg6 + " = call i8* @calloc(i32 1, i32 " + exprReg + ")"); // i1 = 1b
        argu.writeLine(reg7 + " = bitcast i8* " + reg6 + " to i1*");
        argu.writeLine(reg8 + " = getelementptr %_BooleanArray, %_BooleanArray* " + reg3 + ", i32 0, i32 1");
        argu.writeLine(reg9 + " = store i1* " + reg7 + ", i1** " + reg8);
        argu.resReg = resReg;
        argu.resType = "%_BooleanArray*";
        return null;
    }
    
    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(IntegerArrayAllocationExpression n, CLLVMArgs argu) throws Exception {
        n.f3.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i32") != 0) throw new Exception();
        String exprReg = argu.resReg, reg1 = "%_" + argu.regCount++;
        String label1 = "%arr_alloc" + argu.arrayCount++, label2 = "%arr_alloc" + argu.arrayCount++;
        argu.writeLine(reg1 + " = icmp slt i32 " + exprReg + ", 0");
        argu.writeLine("br i1 " + reg1 + ", label %" + label1 + ", label %" + label2);
        argu.writeLabel(label1);
        argu.writeLine("call void @throw_oob()");
        argu.writeLine("br label %" + label2);
        argu.writeLabel(label2);
        String reg2 = "%_" + argu.regCount++, reg3 = "%_" + argu.regCount++, reg4 = "%_" + argu.regCount++, reg5 = "%_" + argu.regCount++, reg6 = "%_" + argu.regCount++, reg7 = "%_" + argu.regCount++, reg8 = "%_" + argu.regCount++, reg9 = "%_" + argu.regCount++;
        argu.writeLine(reg2 + " = call i8* @calloc(i32 1, i32 12)"); // i32 = 4b, i32* = 8b
        argu.writeLine(reg3 + " = bitcast i8* " + reg2 + " to %_IntegerArray*");
        String resReg = reg3;
        argu.writeLine(reg4 + " = getelementptr %_IntegerArray, %_IntegerArray* " + reg3 + ", i32 0, i32 0");
        argu.writeLine(reg5 + " = store i32 " + exprReg + ", i32 *" + reg4);
        argu.writeLine(reg6 + " = call i8* @calloc(i32 4, i32 " + exprReg + ")"); // i32 = 4b
        argu.writeLine(reg7 + " = bitcast i8* " + reg6 + " to i32*");
        argu.writeLine(reg8 + " = getelementptr %_IntegerArray, %_IntegerArray* " + reg3 + ", i32 0, i32 1");
        argu.writeLine(reg9 + " = store i32* " + reg7 + ", i32** " + reg8);
        argu.resReg = resReg;
        argu.resType = "%_IntegerArray*";
        return null;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    @Override
    public String visit(AllocationExpression n, CLLVMArgs argu) throws Exception {
        String identifier = n.f1.accept(this, argu);
        String reg1 = "%_" + argu.regCount++, reg2 = "%_" + argu.regCount++, reg3 = "%_" + argu.regCount++;
        argu.writeLine(reg1 + " = call i8* @calloc(i32 1, i32 " + getSizeClass(identifier, argu) + ")");
        String resReg = reg1;
        argu.writeLine(reg2 + " = bitcast i8* " + reg1 + " to i8***");
        VTEntry entry;
        if ((entry = argu.vTableSizes.get(identifier)) == null) throw new Exception();
        argu.writeLine(reg3 + " = getelementptr [" + entry.size + " x i8*], [" + entry.size + " x 18*]* @." + identifier + "_vtable, i32 0, i32 0");
        argu.writeLine("store i8** " + reg3 + ", i8*** " + reg2);
        argu.resReg = resReg;
        argu.resType = "i8*";
        return null;
    }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    @Override
    public String visit(NotExpression n, CLLVMArgs argu) throws Exception {
        n.f1.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        if (argu.resType.compareTo("i1") != 0) throw new Exception();
        String reg = "%_" + argu.regCount++;
        argu.writeLine(reg + " = xor i1 1, " + argu.resReg);
        argu.resReg = reg;
        argu.resType = "i1";
        return null;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    @Override
    public String visit(BracketExpression n, CLLVMArgs argu) throws Exception {
        argu.tabs++;
        n.f0.accept(this, argu);
        if (argu.resReg == null || argu.resType == null) throw new Exception();
        argu.tabs--;
        return null;
    }

}
