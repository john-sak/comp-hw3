import syntaxtree.*;
import visitor.*;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;

class CLLVMArgs {
    Map<String, OTEntry> offsetTable = null;
    String scope;
}

class compileLLVMVisitor extends GJDepthFirst<String, CLLVMArgs> {

}