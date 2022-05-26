import syntaxtree.*;
import visitor.*;

import java.util.Map;
import java.util.HashMap;

class classInfo {
    public String name;
    public Map<String, fieldInfo> fields = new HashMap<String, fieldInfo>();
    public Map<String, methodInfo> methods = new HashMap<String, methodInfo>();
    public classInfo superclass = null;

    classInfo(String name) {
        this.name = name;
    }
}

class fieldInfo {
    String type;

    fieldInfo(String type) {
        this.type = type;
    }
}

class methodInfo {
    String returnValue, argTypes;
    int argNum;
    Map<String, fieldInfo> localVars = new HashMap<String, fieldInfo>();
}

class symbolTableVisitor extends GJDepthFirst<String, String> {
    Map<String, classInfo> globalST = new HashMap<String, classInfo>();

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
    public String visit(MainClass n, String argu) throws Exception {
        String className = n.f1.accept(this, null);
        if (this.globalST.put(className, new classInfo(className)) != null) throw new Exception();
        if (this.globalST.get(className).methods.put("main", new methodInfo()) != null) throw new Exception();
        methodInfo methodI = this.globalST.get(className).methods.get("main");
        methodI.returnValue = "void";
        methodI.argNum = 1;
        methodI.argTypes = "String[]";
        methodI.localVars.put(n.f11.accept(this, null), new fieldInfo("String[]"));
        n.f14.accept(this, className + "->main");
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
    public String visit(ClassDeclaration n, String argu) throws Exception {
        String className = n.f1.accept(this, null);
        if (this.globalST.put(className, new classInfo(className)) != null) throw new Exception();
        n.f3.accept(this, className);
        n.f4.accept(this, className);
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
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String className = n.f1.accept(this, null), classExtends = n.f3.accept(this, null);
        if (!this.globalST.containsKey(classExtends)) throw new Exception();
        if (this.globalST.put(className, new classInfo(className)) != null) throw new Exception();
        n.f5.accept(this, className);
        n.f6.accept(this, className);
        classInfo thisClass = this.globalST.get(className), superClass = this.globalST.get(classExtends);
        thisClass.superclass = superClass;
        Map<String, methodInfo> newMethods = thisClass.methods;
        while ((superClass = thisClass.superclass) != null) {
            for (Map.Entry<String, methodInfo> currMethod : newMethods.entrySet()) {
                methodInfo method2;
                if ((method2 = superClass.methods.get(currMethod.getKey())) != null) {
                    methodInfo method1 = currMethod.getValue();
                    if (method1.returnValue.compareTo(method2.returnValue) != 0 || method1.argNum != method2.argNum || method1.argTypes.compareTo(method2.argTypes) != 0) throw new Exception();
                }
            }
            thisClass = superClass;
        }
        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public String visit(VarDeclaration n, String argu) throws Exception {
        String[] scopes = argu.split("->");
        classInfo classI;
        if ((classI = this.globalST.get(scopes[0])) == null) throw new Exception();
        if (argu.contains("->")) {
            methodInfo methodI;
            if ((methodI = classI.methods.get(scopes[1])) == null) throw new Exception();
            if (methodI.localVars.put(n.f1.accept(this, null), new fieldInfo(n.f0.accept(this, null))) != null) throw new Exception();
        } else if (classI.fields.put(n.f1.accept(this, null), new fieldInfo(n.f0.accept(this, null))) != null) throw new Exception();
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
    public String visit(MethodDeclaration n, String argu) throws Exception {
        if (argu.contains("->")) throw new Exception();
        String methodName = n.f2.accept(this, null);
        classInfo classI;
        if ((classI = this.globalST.get(argu)) == null) throw new Exception();
        if (classI.methods.put(methodName, new methodInfo()) != null) throw new Exception();
        methodInfo methodI = classI.methods.get(methodName);
        methodI.returnValue = n.f1.accept(this, null);
        String argList = (n.f4.present() ? n.f4.accept(this, null) : "");
        if (!argList.equals("")) {
            String[] args = argList.split(", ");
            methodI.argNum = args.length;
            methodI.argTypes = "";
            for (String arg : args) {
                String[] parts = arg.split(" ");
                methodI.argTypes += parts[0] + ", ";
                if (methodI.localVars.put(parts[1], new fieldInfo(parts[0])) != null) throw new Exception();
            }
            methodI.argTypes = methodI.argTypes.substring(0, methodI.argTypes.length() - 2);
        } else {
            methodI.argTypes = "";
            methodI.argNum = 0;
        }
        n.f7.accept(this, argu + "->" + methodName);
        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        return n.f0.accept(this, null) + n.f1.accept(this, null);
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, String argu) throws Exception {
        return n.f0.accept(this, null) + " " + n.f1.accept(this, null);
    }

    /**
     * f0 -> ( FormalParameterTerm() )*
     */
    @Override
    public String visit(FormalParameterTail n, String argu) throws Exception {
        return n.f0.present() ? n.f0.accept(this, argu) : "";
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return ", " + n.f1.accept(this, null);
    }

    /**
     * f0 -> "boolean"
     * f1 -> "["
     * f2 -> "]"
     */
    @Override
    public String visit(BooleanArrayType n, String argu) throws Exception {
        return "boolean[]";
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    @Override
    public String visit(IntegerArrayType n, String argu) throws Exception {
        return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    @Override
    public String visit(BooleanType n, String argu) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> "int"
     */
    @Override
    public String visit(IntegerType n, String argu) throws Exception {
        return "int";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Identifier n, String argu) throws Exception {
        return n.f0.toString();
    }
}