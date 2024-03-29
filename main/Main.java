import syntaxtree.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws Exception {
        if(args.length < 1){
            System.err.println("Usage: java Main <inputFile1.java> <inputFile2.java> ... <inputFileN.java>");
            System.exit(1);
        }
        FileInputStream fis = null;
        for (int i = 0; i < args.length; i++) {
            try{
                fis = new FileInputStream(args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);
                Goal root = parser.Goal();
                System.err.println("Program in inputFile \"" + args[i] + "\" parsed successfully.");
                symbolTableVisitor STVisitor = new symbolTableVisitor();
                root.accept(STVisitor, null);
                System.out.println("Symbol Table for program in inputFile \"" + args[i] + "\" created succesfully.");
                TCArgs arguTCA = new TCArgs();
                arguTCA.globalST = STVisitor.globalST;
                typeCheckVisitor TCVisitor = new typeCheckVisitor();
                root.accept(TCVisitor, arguTCA);
                System.out.println("Type Check for program in inputFile \"" + args[i] + "\" finished succesfully.");
                OTArgs arguOTA = new OTArgs();
                arguOTA.symbolTable = STVisitor.globalST;
                offsetTableVisitor OTVisitor = new offsetTableVisitor();
                root.accept(OTVisitor, arguOTA);
                System.out.println("Offset Table for program in inputFile \"" + args[i] + "\" created succesfully.");
                // OTVisitor.printResult();
                String[] split = args[i].split("\\.");
                String fileName = "";
                for (int j = 0; j < split.length - 1; j++) fileName += split[j] + ".";
                fileName += "ll";
                try {
                    File file = new File(fileName);
                    if (!file.createNewFile()) System.err.println("File already axists.");
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                    throw new Exception();
                }
                FileWriter writer = new FileWriter(fileName);
                VTArgs arguVTA = new VTArgs();
                arguVTA.writer = writer;
                arguVTA.symbolTable = STVisitor.globalST;
                arguVTA.offsetTable = OTVisitor.stack;
                vTableVisitor VTVisitor = new vTableVisitor();
                root.accept(VTVisitor, arguVTA);
                CLLVMArgs arguCLLVMA = new CLLVMArgs();
                arguCLLVMA.writer = writer;
                arguCLLVMA.symbolTable = STVisitor.globalST;
                arguCLLVMA.offsetTable = OTVisitor.stack;
                arguCLLVMA.vTableSizes = VTVisitor.vTableEntries;
                compileLLVMVisitor CLLVMVisitor = new compileLLVMVisitor();
                root.accept(CLLVMVisitor, arguCLLVMA);
                writer.close();
                System.out.println("Compilation of program in inputFile \"" + args[i] + "\" to LLVM IR succesful (file " + fileName + ").");
            }
            catch(ParseException ex){
                System.out.println(ex.getMessage() + " inputFile \"" + args[i] + "\"");
            }
            catch(FileNotFoundException ex){
                System.err.println(ex.getMessage() + " inputFile \"" + args[i] + "\"");
            }
            catch (Exception ex) {
                System.err.println("Error in inputFile \"" + args[i] + "\" (" + ex.getMessage() + ").");
            }
            finally{
                try{
                    if(fis != null) fis.close();
                    System.out.println();
                }
                catch(IOException ex){
                    System.err.println(ex.getMessage() + " inputFile \"" + args[i] + "\"");
                }
            }
        }
    }
}
