import com.vp.plugin.*;
import com.vp.plugin.diagram.*;
import com.vp.plugin.diagram.shape.*;
import com.vp.plugin.model.*;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Native UML authoring/inspection only; compiled for VP's Java 11 runtime. */
public class ClassProjectPlugin implements VPPlugin, VPPluginCommandLineSupport {
    final ApplicationManager app=ApplicationManager.instance();
    final DiagramManager dm=app.getDiagramManager();
    final IModelElementFactory factory=IModelElementFactory.instance();
    final Map<String,IClass> classes=new LinkedHashMap<>();
    final Map<String,IPackage> packages=new LinkedHashMap<>();
    final Map<String,IRelationship> relations=new LinkedHashMap<>();
    IPackage root;
    public void loaded(VPPluginInfo info) {}
    public void unloaded() {}
    interface Work { void run() throws Exception; }
    static void edt(Work work) throws Exception {
        if(SwingUtilities.isEventDispatchThread()){work.run();return;}
        Throwable[] error=new Throwable[1];
        SwingUtilities.invokeAndWait(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new Exception(error[0]);
    }
    public void invoke(String[] raw) {
        String[] args=raw.length==1?raw[0].split("\\|",-1):raw;
        try {
            if(args.length!=3)throw new IllegalArgumentException("Expected mode|source XML|output directory");
            File out=new File(args[2]);out.mkdirs();
            if(args[0].equals("generate")) {
                Document spec=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(args[1]));
                edt(()->generate(spec));edt(()->{});edt(()->{});
                edt(()->{if(!app.getProjectManager().saveProjectAs(new File(out,"SeasonPlan-all-classes.vpp")))throw new IllegalStateException("Save failed");});
            } else if(args[0].equals("inspect")) { edt(()->inspect(out));edt(()->{}); }
            else throw new IllegalArgumentException("Unknown mode");
            Files.writeString(new File(out,args[0]+"-success.txt").toPath(),"OK\n");
        } catch(Throwable e) {
            e.printStackTrace();
            if(args.length==3)try{Files.writeString(new File(args[2],"failure.txt").toPath(),e.toString());}catch(Exception ignored){}
            throw new RuntimeException(e);
        }
    }
    static java.util.List<Element> children(Element p,String tag) {
        java.util.List<Element> result=new ArrayList<>();
        for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&((Element)n).getTagName().equals(tag))result.add((Element)n);
        return result;
    }
    static boolean yes(Element e,String key){return e.getAttribute(key).equals("true");}
    static int num(Element e,String key){return Integer.parseInt(e.getAttribute(key));}
    void tag(IModelElement m,String key,String value) {
        ITaggedValueContainer container=m.getTaggedValues();
        if(container==null){container=factory.createTaggedValueContainer();m.setTaggedValues(container);}
        ITaggedValue v=container.getTaggedValueByName(key);
        if(v==null){v=container.createTaggedValue();v.setName(key);v.setType(ITaggedValue.TYPE_STRING);}v.setValue(value);
    }
    static String tag(IModelElement m,String key) {
        if(m==null||m.getTaggedValues()==null)return "";
        ITaggedValue v=m.getTaggedValues().getTaggedValueByName(key);return v==null?"":v.getValueAsString();
    }
    static String id(IModelElement m){return tag(m,"sourceId");}
    IPackage pkg(String path) {
        if(packages.containsKey(path))return packages.get(path);
        String separator=path.startsWith("frontend")?"/":".";
        int split=path.lastIndexOf(separator);IPackage owner=split<0?root:pkg(path.substring(0,split));
        IPackage p=factory.createPackage();p.setName(split<0?path:path.substring(split+1));owner.addChild(p);
        tag(p,"sourceId",path);packages.put(path,p);return p;
    }
    void generate(Document spec) {
        ProjectManager pm=app.getProjectManager();
        if(!pm.saveProject()||!pm.newProject())throw new IllegalStateException("Cannot initialize clean native project");
        pm.getProject().setName("SeasonPlan — Complete frontend and backend source model");
        root=factory.createPackage();root.setName("SeasonPlan source snapshot");
        Element source=spec.getDocumentElement();
        for(Element e:children(source,"classifier")) {
            IClass c=factory.createClass();c.setName(e.getAttribute("name"));classes.put(e.getAttribute("id"),c);
            tag(c,"sourceId",e.getAttribute("id"));tag(c,"kind",e.getAttribute("kind"));tag(c,"package",e.getAttribute("package"));
            c.setDocumentation(e.getAttribute("source")+":"+e.getAttribute("line")+"\nSource declaration model. Java generated boilerplate is implicit. JS attributes describe module variables and hook state, not class instances.");
            c.setVisibility(e.getAttribute("visibility"));c.setAbstract(yes(e,"abstract"));c.setFinalSpecialization(yes(e,"final"));
            String kind=e.getAttribute("kind");
            if(kind.equals("interface"))c.addStereotype("Interface");
            else if(kind.equals("enum"))c.addStereotype("Enumeration");
            else if(kind.equals("record"))c.addStereotype("record");
            else if(kind.contains("module"))c.addStereotype(kind);
            for(Element a:children(e,"attribute")) {
                IAttribute v=c.createAttribute();v.setName(a.getAttribute("name"));v.setType(a.getAttribute("type"));
                v.setVisibility(a.getAttribute("visibility"));v.setScope(yes(a,"static")?IAttribute.SCOPE_CLASSIFIER:IAttribute.SCOPE_INSTANCE);
                v.setReadOnly(yes(a,"readonly"));v.setInitialValue(a.getAttribute("initial"));
                if(yes(a,"state"))v.setDocumentation("React hook state projection; not a stored JavaScript class field.");
            }
            for(Element o:children(e,"operation")) {
                IOperation op=c.createOperation();op.setName(o.getAttribute("name"));op.setReturnType(o.getAttribute("returns"));
                op.setVisibility(o.getAttribute("visibility"));op.setStatic(yes(o,"static"));op.setAbstract(yes(o,"abstract"));
                tag(op,"constructor",o.getAttribute("constructor"));
                if(yes(o,"constructor"))op.addStereotype("create");
                op.setDocumentation("Type parameters: "+o.getAttribute("typeParameters")+"\nThrows: "+o.getAttribute("throws"));
                for(Element p:children(o,"parameter")) {
                    IParameter parameter=op.createParameter();parameter.setName(p.getAttribute("name"));parameter.setType(p.getAttribute("type"));parameter.setDirection(IParameter.DIRECTION_IN);
                }
            }
            for(Element l:children(e,"literal"))c.createEnumerationLiteral().setName(l.getAttribute("name"));
        }
        for(Element e:children(source,"classifier")) {
            IClass c=classes.get(e.getAttribute("id"));String owner=e.getAttribute("owner");
            if(owner.isEmpty())pkg(e.getAttribute("package")).addChild(c);else classes.get(owner).addChild(c);
        }
        for(Element e:children(source,"relationship")) {
            String kind=e.getAttribute("kind");IRelationship r;
            if(kind.equals("association"))r=factory.createAssociation();
            else if(kind.equals("realization"))r=factory.createRealization();
            else if(kind.equals("generalization"))r=factory.createGeneralization();
            else r=factory.createDependency();
            boolean reverse=kind.equals("realization")||kind.equals("generalization");
            r.setFrom(classes.get(e.getAttribute(reverse?"to":"from")));r.setTo(classes.get(e.getAttribute(reverse?"from":"to")));
            if(r instanceof IAssociation) {
                IAssociation a=(IAssociation)r;
                IAssociationEnd from=(IAssociationEnd)a.getFromEnd(),to=(IAssociationEnd)a.getToEnd();
                from.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_NONE);to.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_NONE);
                from.setMultiplicity(IAssociationEnd.MULTIPLICITY_UNSPECIFIED);to.setMultiplicity(IAssociationEnd.MULTIPLICITY_UNSPECIFIED);
                from.setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);to.setNavigable(IAssociationEnd.NAVIGABLE_NAVIGABLE);
            }
            r.setName(e.getAttribute("name"));r.setDocumentation(e.getAttribute("evidence"));tag(r,"sourceId",e.getAttribute("id"));tag(r,"kind",kind);
            relations.put(e.getAttribute("id"),r);root.addChild(r);
        }
        for(Element e:children(source,"diagram"))diagram(e);
    }
    void bounds(IShapeUIModel s,Element n){s.setRequestDefaultSize(false);s.setRequestFitSize(false);s.setBounds(num(n,"x"),num(n,"y"),num(n,"w"),num(n,"h"));}
    void diagram(Element spec) {
        IClassDiagramUIModel d=(IClassDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM);
        d.setName(spec.getAttribute("id")+" · "+spec.getAttribute("name"));d.setDocumentation(spec.getAttribute("id"));d.setDefaultPackage(root);
        d.setZoomRatio(1.0);d.setAutoFitShapesSize(false);d.setAlignToGrid(false);d.setGridVisible(false);
        d.setRequestValidateSnapToGrid(false);d.setDiagramBackground(Color.WHITE);d.setShowTaggedValues(false);d.setShowStereotypes(true);
        d.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);d.setSuppressImplied1MultiplicityForAttributeAndAssociationEnd(true);
        Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();
        for(Element n:children(spec,"node")) {
            IClass c=classes.get(n.getAttribute("id"));IClassUIModel s=(IClassUIModel)dm.createDiagramElement(d,c);
            boolean detail=yes(n,"detail");
            s.setDisplayAsRobustnessAnalysisIcon(false);s.setDisplayStereotypeIcon(false);s.setInterfaceBall(false);
            s.setShowAttributeType(detail?IClassUIModel.ATTR_SHOW_TYPE_ALL:IClassUIModel.ATTR_SHOW_TYPE_NONE);
            s.setShowOperationType(detail?IClassUIModel.OP_SHOW_TYPE_ALL:IClassUIModel.OP_SHOW_TYPE_NONE);
            s.setShowEnumerationLiteralType(detail?IClassUIModel.ENUMERATION_LITERAL_SHOW_TYPE_ALL:IClassUIModel.ENUMERATION_LITERAL_SHOW_TYPE_NONE);
            s.setShowParameterNameInOperationSignature(true);s.setShowOperationSignature(true);s.setShowInitialAttributeValue(false);
            s.setShowOwnerOption(IClassUIModel.SHOW_OWNER_NONE);s.setWrapMembers(true);s.setShowReferencedAttributes(IClassUIModel.SHOW_REFERENCED_ATTRIBUTES_NO);
            Color fill=n.getAttribute("id").startsWith("frontend/")?new Color(232,242,254):tag(c,"kind").equals("interface")?new Color(244,237,252):new Color(238,248,235);
            s.getFillColor().setColor1(fill);s.getFillColor().setColor2(fill);s.setBackground(fill);s.setForeground(new Color(50,67,82));
            s.getElementFont().setValues("Arial",Font.PLAIN,14,new Color(20,35,49));bounds(s,n);s.resetCaption();bounds(s,n);shapes.put(n.getAttribute("id"),s);
        }
        dm.openDiagram(d);for(Element n:children(spec,"node"))bounds(shapes.get(n.getAttribute("id")),n);
        java.util.List<Rectangle> labels=new ArrayList<>();
        for(Element e:children(spec,"edge")) {
            IRelationship r=relations.get(e.getAttribute("id"));
            IShapeUIModel from=shapes.get(id(r.getFrom())),to=shapes.get(id(r.getTo()));
            Point[] ps=points(e.getAttribute("points"));
            if(tag(r,"kind").equals("realization")||tag(r,"kind").equals("generalization"))Collections.reverse(Arrays.asList(ps));
            IConnectorUIModel c=(IConnectorUIModel)dm.createConnector(d,r,from,to,ps);
            c.setFromShape(from);c.setToShape(to);c.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
            c.setForeground(new Color(57,73,90));c.setBackground(Color.WHITE);c.getLineModel().setColor(new Color(57,73,90));c.getLineModel().setWeight(1.2f);c.getLineModel().setTransparency(0);c.getLineModel().applySetting();
            pin(c,from,ps[0],true);pin(c,to,ps[ps.length-1],false);c.clearPoints();for(Point p:ps)c.addPoint(p);
            c.setRequestRebuild(true);c.setRequestRebuildFromEnd(true);c.setRequestRebuildToEnd(true);
            c.getElementFont().setValues("Arial",Font.PLAIN,12,new Color(20,35,49));c.resetCaption();ICaptionUIModel cap=c.getCaptionUIModel();
            if(r.getName().isEmpty())cap.setVisible(false);
            else {
                Rectangle label=label(r.getName(),ps,shapes.values(),labels);labels.add(label);
                cap.setVisible(true);cap.setSide(ICaptionUIModel.SIDE_NONE);cap.setBounds(label.x,label.y,label.width,label.height);
            }
        }
        IShapeUIModel note=(IShapeUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_NOTE);
        String title=spec.getAttribute("name")+"\n"+spec.getAttribute("intro");
        note.getModelElement().setName(title);note.getModelElement().setDocumentation(title);tag(note.getModelElement(),"sourceId","note:"+spec.getAttribute("id"));
        note.getElementFont().setValues("Arial",Font.PLAIN,18,new Color(20,35,49));note.getFillColor().setColor1(new Color(248,249,251));
        note.setBounds(60,20,Math.max(1100,title.length()*6),95);note.resetCaption();note.setBounds(60,20,Math.max(1100,title.length()*6),95);d.modified();
    }
    Rectangle label(String text,Point[] ps,Collection<IShapeUIModel> shapes,java.util.List<Rectangle> labels) {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(1,1,2);Graphics2D g=image.createGraphics();g.setFont(new Font("Arial",0,12));
        int w=g.getFontMetrics().stringWidth(text)+28;g.dispose();
        for(int j=0;j<ps.length-1;j++) {
            int x=(ps[j].x+ps[j+1].x)/2,y=(ps[j].y+ps[j+1].y)/2;
            for(Rectangle r:new Rectangle[]{new Rectangle(x-w/2,y-30,w,25),new Rectangle(x+8,y+6,w,25),new Rectangle(x-w-8,y-30,w,25)}) {
                boolean clear=r.y>120;
                for(IShapeUIModel s:shapes)if(r.intersects(new Rectangle(s.getX()-4,s.getY()-4,s.getWidth()+8,s.getHeight()+8)))clear=false;
                for(Rectangle l:labels)if(r.intersects(l))clear=false;
                if(clear)return r;
            }
        }
        throw new IllegalStateException("No clear relation caption: "+text);
    }
    static Point[] points(String text) {
        String[] tokens=text.split(";");Point[] ps=new Point[tokens.length];
        for(int i=0;i<tokens.length;i++){String[] xy=tokens[i].split(",");ps[i]=new Point(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));}return ps;
    }
    void pin(IConnectorUIModel c,IShapeUIModel s,Point p,boolean from) {
        int dx=p.x-s.getX(),dy=p.y-s.getY();Point2D ratio=new Point2D.Double((double)dx/s.getWidth(),(double)dy/s.getHeight());
        if(from){c.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);c.setFromPinRatio(ratio);c.setFromShapeXDiff(dx);c.setFromShapeYDiff(dy);c.setUseFromShapeCenter(false);}
        else{c.setToPinType(IConnectorUIModel.PINTYPE_RATIO);c.setToPinRatio(ratio);c.setToShapeXDiff(dx);c.setToShapeYDiff(dy);c.setUseToShapeCenter(false);}
    }
    static Element append(Document doc,Element p,String tag,String... attrs) {
        Element e=doc.createElement(tag);p.appendChild(e);for(int i=0;i<attrs.length;i+=2)e.setAttribute(attrs[i],attrs[i+1]);return e;
    }
    void inspect(File out) throws Exception {
        Document report=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();Element all=report.createElement("native-project");report.appendChild(all);
        for(IModelElement m:app.getProjectManager().getProject().toAllLevelModelElementArray()) {
            if(m instanceof IClass&&!id(m).isEmpty()) {
                IClass c=(IClass)m;String owner=c.getParent() instanceof IClass?id(c.getParent()):"";
                Element ce=append(report,all,"classifier","id",id(c),"name",c.getName(),"owner",owner,"package",tag(c,"package"),"kind",tag(c,"kind"),
                    "visibility",c.getVisibility(),"abstract",String.valueOf(c.isAbstract()),"final",String.valueOf(c.isFinalSpecialization()),"stereotypes",String.join(";",c.toStereotypeArray()));
                for(IAttribute a:c.toAttributeArray())append(report,ce,"attribute","name",a.getName(),"type",a.getTypeAsString(),"visibility",a.getVisibility(),"static",String.valueOf(a.getScope().equals(IAttribute.SCOPE_CLASSIFIER)),"readonly",String.valueOf(a.isReadOnly()));
                for(IOperation o:c.toOperationArray()) {
                    Element oe=append(report,ce,"operation","name",o.getName(),"returns",o.getReturnTypeAsString(),"visibility",o.getVisibility(),"static",String.valueOf(o.isStatic()),"abstract",String.valueOf(o.isAbstract()),"constructor",tag(o,"constructor"));
                    for(IParameter p:o.toParameterArray())append(report,oe,"parameter","name",p.getName(),"type",p.getTypeAsString());
                }
                for(IEnumerationLiteral l:c.toEnumerationLiteralArray())append(report,ce,"literal","name",l.getName());
            } else if(m instanceof IRelationship&&!id(m).isEmpty()) {
                IRelationship r=(IRelationship)m;
                Element re=append(report,all,"relationship","id",id(m),"kind",tag(m,"kind"),"type",m.getModelType(),"from",id(r.getFrom()),"to",id(r.getTo()),"name",r.getName());
                if(r instanceof IAssociation) {
                    IAssociationEnd f=(IAssociationEnd)((IAssociation)r).getFromEnd(),t=(IAssociationEnd)((IAssociation)r).getToEnd();
                    re.setAttribute("fromAggregation",f.getAggregationKind());re.setAttribute("toAggregation",t.getAggregationKind());
                    re.setAttribute("fromMultiplicity",f.getMultiplicity());re.setAttribute("toMultiplicity",t.getMultiplicity());
                }
            }
        }
        File previews=new File(out,"previews");previews.mkdirs();
        for(IDiagramUIModel d:app.getProjectManager().getProject().toDiagramArray()) {
            d.setZoomRatio(1.0);dm.openDiagram(d);String key=d.getDocumentation();
            Element de=append(report,all,"diagram","id",key,"name",d.getName(),"type",d.getType());
            for(IDiagramElement item:d.toDiagramElementArray()) {
                if(item instanceof IConnectorUIModel) {
                    IConnectorUIModel c=(IConnectorUIModel)item;IRelationship r=(IRelationship)item.getModelElement();ICaptionUIModel cap=c.getCaptionUIModel();
                    Element e=append(report,de,"edge","id",id(r),"from",id(r.getFrom()),"to",id(r.getTo()),"fromShape",id(c.getFromShape().getModelElement()),"toShape",id(c.getToShape().getModelElement()),
                        "captionSide",String.valueOf(cap.getSide()),"captionVisible",String.valueOf(cap.isVisible()),"caption",cap.getX()+","+cap.getY()+","+cap.getWidth()+","+cap.getHeight(),"drawingBounds",c.getX()+","+c.getY()+","+c.getWidth()+","+c.getHeight());
                    StringJoiner ps=new StringJoiner(";");for(Point p:c.getPoints())ps.add(p.x+","+p.y);e.setAttribute("points",ps.toString());
                } else if(item instanceof IShapeUIModel) {
                    IShapeUIModel s=(IShapeUIModel)item;IModelElement m=s.getModelElement();
                    Element e=append(report,de,"shape","id",id(m),"name",m.getName(),"type",m.getModelType(),"bounds",s.getX()+","+s.getY()+","+s.getWidth()+","+s.getHeight(),"documentation",m.getDocumentation());
                    if(s instanceof IClassUIModel){IClassUIModel c=(IClassUIModel)s;e.setAttribute("attributes",String.valueOf(c.getShowAttributeType()));e.setAttribute("operations",String.valueOf(c.getShowOperationType()));}
                }
            }
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(previews,key+".svg"),ModelConvertionManager.IMAGE_TYPE_SVG);
            ExportDiagramAsImageOption option=new ExportDiagramAsImageOption(ExportDiagramAsImageOption.IMAGE_TYPE_PNG);
            option.setScale(0.6f);option.setMaxSize(new Dimension(5000,5000));option.setTextAntiAliasing(true);
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(previews,key+".png"),option);
            System.out.println("REOPENED_EXPORT "+key);
        }
        Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.INDENT,"yes");tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        tr.transform(new DOMSource(report),new StreamResult(new File(out,"native-reopened.xml")));
    }
}
