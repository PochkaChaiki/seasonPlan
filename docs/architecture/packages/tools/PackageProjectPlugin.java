import com.vp.plugin.*;
import com.vp.plugin.diagram.*;
import com.vp.plugin.diagram.shape.*;
import com.vp.plugin.model.*;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
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

/** Documentation tooling; Java 11 bytecode for VP 18.1, not application code. */
public class PackageProjectPlugin implements VPPlugin, VPPluginCommandLineSupport {
    private final ApplicationManager app=ApplicationManager.instance();
    private final DiagramManager dm=app.getDiagramManager();
    private final IModelElementFactory factory=IModelElementFactory.instance();
    private final Map<String,IPackage> packages=new LinkedHashMap<>();
    private IPackage root;
    private static final String PREFIX="seasonplan-package:";
    public void loaded(VPPluginInfo info) {}
    public void unloaded() {}
    interface Work {void run() throws Exception;}
    private static void edt(Work work) throws Exception {
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
                edt(()->generate(spec,out));
            } else if(args[0].equals("inspect"))edt(()->inspect(out));
            else throw new IllegalArgumentException("Unknown mode");
            Files.writeString(new File(out,args[0]+"-success.txt").toPath(),"OK\n");
        } catch(Throwable e) {
            e.printStackTrace();
            if(args.length==3)try{Files.writeString(new File(args[2],"failure.txt").toPath(),e.toString());}catch(Exception ignored){}
            throw new RuntimeException(e);
        }
    }
    private static java.util.List<Element> children(Element parent,String name) {
        java.util.List<Element> result=new ArrayList<>();
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element && ((Element)n).getTagName().equals(name))result.add((Element)n);
        return result;
    }
    private static int number(Element e,String key){return Integer.parseInt(e.getAttribute(key));}
    private static void tag(IModelElement m,String id,String text){m.setDocumentation(PREFIX+id+"\n"+text);}
    private static String ident(IModelElement m){
        if(m==null)return "";
        ITaggedValueContainer values=m.getTaggedValues();
        if(values!=null) {
            ITaggedValue id=values.getTaggedValueByName("sourceId");
            if(id!=null)return id.getValueAsString();
        }
        String text=m.getDocumentation();
        return text!=null&&text.startsWith(PREFIX)?text.substring(PREFIX.length()).split("\n",2)[0]:"";
    }
    private IPackage packageModel(String id) {
        IPackage p=packages.get(id);if(p!=null)return p;
        IPackage parent=root;String name=id;
        if(id.contains(".")){int split=id.lastIndexOf('.');parent=packageModel(id.substring(0,split));name=id.substring(split+1);}
        p=factory.createPackage();p.setName(name);parent.addChild(p);tag(p,id,"Source package "+id);packages.put(id,p);return p;
    }
    private void style(IShapeUIModel shape,Color fill,int size,boolean bold) {
        shape.setRequestDefaultSize(false);shape.setRequestFitSize(false);
        shape.getFillColor().setColor1(fill);shape.getFillColor().setColor2(fill);shape.setBackground(fill);
        shape.setForeground(new Color(68,87,104));
        shape.getElementFont().setValues("Arial",bold?Font.BOLD:Font.PLAIN,size,new Color(27,45,61));
        shape.setDisplayStereotypeIcon(false);shape.setRequestResetCaption(true);
    }
    private IShapeUIModel note(IDiagramUIModel d,String id,String text,Rectangle bounds,Color color,int font) {
        IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_NOTE);
        IModelElement m=s.getModelElement();if(m==null)throw new IllegalStateException("Native note model missing");
        m.setName(text);m.setDocumentation(text);
        // NOTE renders documentation, not just name: keep identity out of visible prose.
        ITaggedValueContainer values=factory.createTaggedValueContainer();m.setTaggedValues(values);
        ITaggedValue identity=values.createTaggedValue();identity.setName("sourceId");
        identity.setType(ITaggedValue.TYPE_STRING);identity.setValue(id);
        style(s,color,font,false);
        s.setBounds(bounds.x,bounds.y,bounds.width,bounds.height);s.resetCaption();
        s.setBounds(bounds.x,bounds.y,bounds.width,bounds.height);return s;
    }
    private void bounds(IShapeUIModel s,Element e){s.setRequestDefaultSize(false);s.setRequestFitSize(false);s.setBounds(number(e,"x"),number(e,"y"),number(e,"w"),number(e,"h"));}
    private void generate(Document source,File out) throws Exception {
        ProjectManager pm=app.getProjectManager();
        if(!pm.saveProject()||!pm.newProject())throw new IllegalStateException("Cannot initialize clean native project");
        pm.getProject().setName("SeasonPlan — Package architecture");
        root=factory.createPackage();root.setName("SeasonPlan — source-backed package architecture");tag(root,"root","Architectural documentation, not an extra code module");
        for(Element d:children(source.getDocumentElement(),"diagram"))
            for(Element p:children(d,"package"))packageModel(p.getAttribute("id")).setName(p.getAttribute("name"));
        for(Element d:children(source.getDocumentElement(),"diagram"))diagram(d);
        if(!pm.saveProjectAs(new File(out,"SeasonPlan-packages.vpp")))throw new IllegalStateException("Native save failed");
    }
    private void diagram(Element spec) {
        IPackageDiagramUIModel d=(IPackageDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM);
        d.setName(spec.getAttribute("name"));d.setDocumentation(PREFIX+spec.getAttribute("id"));
        d.setDefaultPackage(root);d.setZoomRatio(1.0);d.setAutoFitShapesSize(false);d.setShowDiagramFrame(false);
        d.setAlignToGrid(false);d.setGridVisible(false);d.setRequestValidateSnapToGrid(false);d.setDiagramBackground(Color.WHITE);
        d.setShowStereotypes(false);d.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
        Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();Map<String,IShapeUIModel> bodies=new LinkedHashMap<>();
        for(Element p:children(spec,"package")) {
            String id=p.getAttribute("id");IPackage m=packages.get(id);
            if(!m.getName().equals(p.getAttribute("name")))throw new IllegalArgumentException("Source name mismatch "+id);
            IPackageUIModel s=(IPackageUIModel)dm.createDiagramElement(d,m);
            s.setShowOwnerOption(IPackageUIModel.SHOW_OWNER_NONE);
            style(s,Color.decode("#"+p.getAttribute("color")),20,true);bounds(s,p);s.resetCaption();bounds(s,p);
            shapes.put(id,s);
            IShapeUIModel body=note(d,"body:"+id,p.getAttribute("body"),
                new Rectangle(number(p,"x")+20,number(p,"y")+60,number(p,"w")-40,number(p,"h")-80),
                Color.decode("#"+p.getAttribute("color")),16);
            m.addChild(body.getModelElement());s.addChild(body);bodies.put(id,body);
        }
        dm.openDiagram(d);
        // Native caption materialization may resize/recenter newly created packages.
        for(Element p:children(spec,"package")) {
            String id=p.getAttribute("id");bounds(shapes.get(id),p);
            bodies.get(id).setBounds(number(p,"x")+20,number(p,"y")+60,number(p,"w")-40,number(p,"h")-80);
        }
        java.util.List<Rectangle> captionBounds=new ArrayList<>();
        for(Element e:children(spec,"dependency")) {
            String a=e.getAttribute("client"),b=e.getAttribute("supplier");
            IDependency dep=factory.createDependency();dep.setFrom(packages.get(a));dep.setTo(packages.get(b));
            dep.setName(e.getAttribute("name"));tag(dep,e.getAttribute("id"),e.getAttribute("evidence"));root.addChild(dep);
            Point[] ps=points(e.getAttribute("points"));
            IConnectorUIModel c=(IConnectorUIModel)dm.createConnector(d,dep,shapes.get(a),shapes.get(b),ps);
            c.setFromShape(shapes.get(a));c.setToShape(shapes.get(b));c.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
            c.setForeground(new Color(76,93,109));c.setBackground(Color.WHITE);
            pin(c,shapes.get(a),ps[0],true);pin(c,shapes.get(b),ps[ps.length-1],false);
            c.clearPoints();for(Point p:ps)c.addPoint(p);
            ICaptionUIModel cap=c.getCaptionUIModel();
            if(dep.getName().isEmpty())cap.setVisible(false);
            else {
                c.getElementFont().setValues("Arial",Font.PLAIN,16,new Color(27,45,61));
                c.setRequestResetCaption(true);c.resetCaption();cap=c.getCaptionUIModel();
                cap.setVisible(true);cap.setSide(ICaptionUIModel.SIDE_NONE);
                Rectangle r=captionPosition(dep.getName(),ps,shapes.values(),captionBounds);
                cap.setBounds(r.x,r.y,r.width,r.height);captionBounds.add(r);
            }
        }
        int width=number(spec,"width");int maxY=0;
        for(IShapeUIModel s:shapes.values())maxY=Math.max(maxY,s.getY()+s.getHeight());
        note(d,"title:"+spec.getAttribute("id"),spec.getAttribute("name")+"\n"+spec.getAttribute("intro"),
            new Rectangle(40,20,width-80,100),new Color(246,249,252),22);
        note(d,"legend:"+spec.getAttribute("id"),spec.getAttribute("notes"),
            new Rectangle(40,maxY+80,width-80,200),new Color(255,249,229),17);
        d.modified();
    }
    private Rectangle captionPosition(String text,Point[] ps,Collection<IShapeUIModel> shapes,java.util.List<Rectangle> previous) {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();g.setFont(new Font("Arial",Font.PLAIN,16));
        int w=g.getFontMetrics().stringWidth(text)+30;g.dispose();int h=32;
        for(int i=0;i<ps.length-1;i++) {
            Point a=ps[i],b=ps[i+1];
            int x=(a.x+b.x)/2,y=(a.y+b.y)/2;
            Rectangle[] candidates=a.x==b.x?new Rectangle[]{new Rectangle(x+12,y-h/2,w,h),new Rectangle(x-w-12,y-h/2,w,h)}:
                new Rectangle[]{new Rectangle(x-w/2,y-h-12,w,h),new Rectangle(x-w/2,y+12,w,h)};
            for(Rectangle r:candidates) {
                boolean clear=true;
                for(IShapeUIModel s:shapes)if(r.intersects(new Rectangle(s.getX()-5,s.getY()-5,s.getWidth()+10,s.getHeight()+10)))clear=false;
                for(Rectangle old:previous)if(r.intersects(old))clear=false;
                if(clear)return r;
            }
        }
        throw new IllegalStateException("No clear caption placement: "+text);
    }
    private static Point[] points(String text) {
        String[] entries=text.split(";");Point[] result=new Point[entries.length];
        for(int i=0;i<entries.length;i++){String[] xy=entries[i].split(",");result[i]=new Point(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));}
        return result;
    }
    private void pin(IConnectorUIModel c,IShapeUIModel s,Point p,boolean from) {
        int dx=p.x-s.getX(),dy=p.y-s.getY();Point2D ratio=new Point2D.Double((double)dx/s.getWidth(),(double)dy/s.getHeight());
        if(from){c.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);c.setFromPinRatio(ratio);c.setFromShapeXDiff(dx);c.setFromShapeYDiff(dy);c.setUseFromShapeCenter(false);}
        else{c.setToPinType(IConnectorUIModel.PINTYPE_RATIO);c.setToPinRatio(ratio);c.setToShapeXDiff(dx);c.setToShapeYDiff(dy);c.setUseToShapeCenter(false);}
    }
    private static Element append(Document doc,Element parent,String name,String...attrs) {
        Element e=doc.createElement(name);parent.appendChild(e);
        for(int i=0;i<attrs.length;i+=2)e.setAttribute(attrs[i],attrs[i+1]);return e;
    }
    private void inspect(File out) throws Exception {
        IProject project=app.getProjectManager().getProject();
        Document report=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element r=report.createElement("native-project");report.appendChild(r);r.setAttribute("name",project.getName());
        for(IModelElement m:project.toAllLevelModelElementArray())if(m instanceof IPackage && !ident(m).isEmpty())
            append(report,r,"model","id",ident(m),"name",m.getName(),"parent",ident(m.getParent()),"type",m.getModelType());
        for(IDiagramUIModel d:project.toDiagramArray()) {
            d.setZoomRatio(1.0);dm.openDiagram(d);
            String id=d.getDocumentation().substring(PREFIX.length());
            Element de=append(report,r,"diagram","id",id,"name",d.getName(),"type",d.getType());
            for(IDiagramElement item:d.toDiagramElementArray()) {
                IModelElement m=item.getModelElement();if(m==null)continue;
                if(item instanceof IConnectorUIModel) {
                    IConnectorUIModel c=(IConnectorUIModel)item;ISimpleRelationship dep=(ISimpleRelationship)m;
                    Element e=append(report,de,"edge","id",ident(m),"type",m.getModelType(),"name",m.getName(),
                        "from",ident(dep.getFrom()),"to",ident(dep.getTo()),"fromShape",ident(c.getFromShape().getModelElement()),"toShape",ident(c.getToShape().getModelElement()),
                        "captionSide",String.valueOf(c.getCaptionUIModel().getSide()));
                    StringJoiner points=new StringJoiner(";");for(Point p:c.getPoints())points.add(p.x+","+p.y);e.setAttribute("points",points.toString());
                    ICaptionUIModel cap=c.getCaptionUIModel();e.setAttribute("caption",cap.getX()+","+cap.getY()+","+cap.getWidth()+","+cap.getHeight());
                } else if(item instanceof IShapeUIModel) {
                    IShapeUIModel s=(IShapeUIModel)item;
                    append(report,de,"shape","id",ident(m),"type",m.getModelType(),"modelId",m.getId(),"name",m.getName(),"parent",ident(m.getParent()),
                        "bounds",s.getX()+","+s.getY()+","+s.getWidth()+","+s.getHeight(),"documentation",m.getDocumentation());
                }
            }
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,id+".svg"),ModelConvertionManager.IMAGE_TYPE_SVG);
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,id+".png"),ModelConvertionManager.IMAGE_TYPE_PNG);
        }
        Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.INDENT,"yes");tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        tr.transform(new DOMSource(report),new StreamResult(new File(out,"native-reopened.xml")));
    }
}
