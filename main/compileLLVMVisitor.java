import syntaxtree.*;
import visitor.*;

import java.io.FileWriter;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

class CLLVMArgs {
    String scope = null;
    FileWriter writer = null;
    Map<String, classInfo> symbolTable = null;
    Map<String, OTEntry> offsetTable = null;
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
        argu.writer.write("\ndeclare i8* @calloc(i32, i32)\ndeclare i32 @printf(i8*, ...)\ndeclare void @exit(i32)\n");
        argu.writer.write("\n@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n");
        argu.writer.write("\ndefine void @print_int(i32 %i) {\n\t%_str = bitcast [4 x i8]* @_cint to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n\tret void\n}\n");
        argu.writer.write("\ndefine voide @throw_oob() {\n\t%_str = bitcast [15 x i8]* @cOOB to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str)\n\tcall void @exit(i32 1)\n\tret void\n}\n");
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
        String oldArgu = argu.scope;
        argu.scope = n.f1.accept(this, argu) + "->main";
        argu.writer.write("\ndefine i32 @main() {\n");
        n.f14.accept(this, argu);
        // argu.writer.write("\tinside\n");
        n.f15.accept(this, argu);
        argu.writer.write("\tret i32 0\n}\n");
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
        argu.writer.write("\t%" + identifier + " = alloca " + getTypeLLVMA(type) + "\n");
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
        classInfo classI;
        if ((classI = argu.symbolTable.get(oldArgu)) == null) throw new Exception();
        methodInfo methodI;
        if ((methodI = classI.methods.get(methName)) == null) throw new Exception();
        argu.writer.write("\ndefine " + getTypeLLVMA(methodI.returnValue) + " @" + oldArgu + "." + methName + "(i8* %this");
        List<paramInfoNode> listInfo = new ArrayList<paramInfoNode>();
        if (n.f4.present())
        for (String param : n.f4.accept(this, argu).split(", ")) {
            String[] temp = param.split(" ");
                if (temp.length != 2) throw new Exception();
                listInfo.add(new paramInfoNode(temp[1], temp[0]));
                argu.writer.write(", " + getTypeLLVMA(temp[0]) + " %." + temp[1]);
            }
        argu.writer.write(") {\n");
        for (paramInfoNode node : listInfo) {
            String typeLLVM = getTypeLLVMA(node.type);
            argu.writer.write("\t%" + node.identifier + " = alloca " + typeLLVM + "\n\tstore " + typeLLVM + " %." + node.identifier + ", " + typeLLVM + "* %" + node.identifier + "\n");
        }
        n.f7.accept(this, argu);
        // argu.writer.write("\tinside\n");
        n.f8.accept(this, argu);
        // argu.writer.write("\tret " + n.f10.accept(this, argu) + ";\n}\n");
        argu.writer.write("}\n");
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
     * ***** STATEMENT *****
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */

    
    
    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Identifier n, CLLVMArgs argu) throws Exception {
        return n.f0.toString();
    }
}