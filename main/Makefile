all: compile

compile:
	java -jar ../jtb132di.jar -te minijava.jj
	java -jar ../javacc5.jar minijava-jtb.jj
	javac Main.java symbolTableVisitor.java typeCheckVisitor.java offsetTableVisitor.java vTableVisitor.java

clean:
	rm -f *.class *~