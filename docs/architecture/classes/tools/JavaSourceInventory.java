import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.Modifier;
import javax.tools.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Parses Java 17 source declarations without executing application classes. */
public class JavaSourceInventory {
    static Document doc;
    static Element add(Element parent, String tag, String... attrs) {
        Element e=doc.createElement(tag); parent.appendChild(e);
        for(int i=0;i<attrs.length;i+=2)e.setAttribute(attrs[i],attrs[i+1]); return e;
    }
    static String bool(boolean value){return String.valueOf(value);}
    static String visibility(Set<Modifier> flags, boolean itf, boolean ctor, boolean enm) {
        for(String v:List.of("public","protected","private"))
            if(flags.stream().anyMatch(m->m.toString().equals(v)))return v;
        return ctor&&enm?"private":itf?"public":"package";
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]); List<Path> paths;
        try(Stream<Path> files=Files.walk(root)){paths=files.filter(p->p.toString().endsWith(".java")).sorted().collect(Collectors.toList());}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(errors,null,java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task=(JavacTask)compiler.getTask(null,fm,errors,List.of("--release","17","-proc:none"),null,fm.getJavaFileObjectsFromPaths(paths));
            doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument(); Element all=doc.createElement("inventory");doc.appendChild(all);
            Trees trees=Trees.instance(task);
            for(CompilationUnitTree unit:task.parse()) {
                String pkg=unit.getPackageName()==null?"":unit.getPackageName().toString();
                String source=root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\','/');
                String imports=unit.getImports().stream().map(i->i.getQualifiedIdentifier().toString()).collect(Collectors.joining(";"));
                new TreeScanner<Void,String>() {
                    final Set<String> interfaceOwners=new HashSet<>();
                    @Override public Void visitClass(ClassTree c,String owner) {
                        if(c.getSimpleName().length()==0)return null; // Anonymous implementations are implicit expressions.
                        String name=c.getSimpleName().toString(),id=(owner==null?pkg:owner)+"."+name;
                        String kind=c.getKind().name().toLowerCase(Locale.ROOT);
                        boolean itf=kind.equals("interface")||kind.equals("annotation_type"),enm=kind.equals("enum");
                        if(itf)interfaceOwners.add(id);
                        Element ce=add(all,"classifier","id",id,"name",owner==null?name:id.substring(pkg.length()+1),"package",pkg,
                            "source",source,"owner",owner==null?"":owner,"kind",kind,"imports",imports,
                            "visibility",visibility(c.getModifiers().getFlags(),interfaceOwners.contains(owner),false,false),
                            "abstract",bool(itf||c.getModifiers().getFlags().contains(Modifier.ABSTRACT)),
                            "final",bool(kind.equals("record")||enm||c.getModifiers().getFlags().contains(Modifier.FINAL)),
                            "line",String.valueOf(unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit,c))),
                            "typeParameters",c.getTypeParameters().toString(),"declaration",c.toString());
                        if(c.getExtendsClause()!=null)add(ce,"extends","type",c.getExtendsClause().toString());
                        for(Tree t:c.getImplementsClause())add(ce,itf?"extends":"implements","type",t.toString());
                        for(Tree t:c.getMembers()) {
                            if(t instanceof VariableTree) {
                                VariableTree v=(VariableTree)t;Set<Modifier> flags=v.getModifiers().getFlags();
                                if(enm&&v.getType()!=null&&v.getType().toString().equals(name)&&v.getInitializer() instanceof NewClassTree) {
                                    add(ce,"literal","name",v.getName().toString());continue;
                                }
                                add(ce,"attribute","name",v.getName().toString(),"type",v.getType().toString(),
                                    "visibility",visibility(flags,itf,false,enm),"static",bool(itf||flags.contains(Modifier.STATIC)),
                                    "readonly",bool(itf||flags.contains(Modifier.FINAL)),"initial",v.getInitializer()==null?"":v.getInitializer().toString());
                            } else if(t instanceof MethodTree) {
                                MethodTree m=(MethodTree)t;Set<Modifier> flags=m.getModifiers().getFlags();boolean ctor=m.getReturnType()==null;
                                Element op=add(ce,"operation","name",ctor?name:m.getName().toString(),"returns",ctor?"":m.getReturnType().toString(),
                                    "constructor",bool(ctor),"visibility",visibility(flags,itf,ctor,enm),"static",bool(flags.contains(Modifier.STATIC)),
                                    "abstract",bool(flags.contains(Modifier.ABSTRACT)||(itf&&m.getBody()==null)),
                                    "typeParameters",m.getTypeParameters().toString(),"throws",m.getThrows().toString());
                                for(VariableTree p:m.getParameters())add(op,"parameter","name",p.getName().toString(),"type",p.getType().toString());
                            }
                        }
                        // Retain named nested declarations; method-local anonymous/lambda bodies are not classifiers.
                        for(Tree t:c.getMembers())if(t instanceof ClassTree)scan(t,id);
                        return null;
                    }
                }.scan(unit,null);
            }
            for(Diagnostic<?> d:errors.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException(d.toString());
            Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
            tr.transform(new DOMSource(doc),new StreamResult(System.out));
        }
    }
}
