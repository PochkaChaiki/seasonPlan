import com.vp.plugin.*;
import com.vp.plugin.diagram.*;
import com.vp.plugin.diagram.shape.*;
import com.vp.plugin.model.*;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
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

/** Native VP 18.1 UML authoring; documentation tooling, Java 11 runtime. */
public class DeploymentProjectPlugin implements VPPlugin, VPPluginCommandLineSupport {
    private final ApplicationManager app=ApplicationManager.instance();
    private final DiagramManager dm=app.getDiagramManager();
    private final IModelElementFactory factory=IModelElementFactory.instance();
    private final Map<String,IModelElement> models=new LinkedHashMap<>();
    private final Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();
    private static final String PREFIX="seasonplan-deployment:";
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
                edt(()->generate(spec));
                edt(()->{}); // Drain queued native model/view updates before saving.
                edt(()->{if(!app.getProjectManager().saveProjectAs(new File(out,"SeasonPlan-deployment.vpp")))throw new IllegalStateException("Native save failed");});
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
    private void tag(IModelElement m,String id,String documentation) {
        m.setDocumentation(documentation);
        ITaggedValueContainer values=factory.createTaggedValueContainer();m.setTaggedValues(values);
        ITaggedValue identity=values.createTaggedValue();identity.setName("sourceId");
        identity.setType(ITaggedValue.TYPE_STRING);identity.setValue(id);
    }
    private static String ident(IModelElement m) {
        if(m==null)return "";
        ITaggedValueContainer values=m.getTaggedValues();
        if(values==null)return "";
        ITaggedValue value=values.getTaggedValueByName("sourceId");
        return value==null?"":value.getValueAsString();
    }
    private void style(IShapeUIModel s,Color fill,int size,boolean bold) {
        s.setRequestDefaultSize(false);s.setRequestFitSize(false);
        s.getFillColor().setColor1(fill);s.getFillColor().setColor2(fill);s.setBackground(fill);
        s.setForeground(new Color(60,78,95));
        s.getElementFont().setValues("Arial",bold?Font.BOLD:Font.PLAIN,size,new Color(25,43,58));
        s.setDisplayStereotypeIcon(false);s.setRequestResetCaption(true);
        s.setModelElementNameAlignment(IShapeUIModel.MODEL_ELEMENT_NAME_ALIGNMENT_ALIGN_TOP_MIDDLE);
    }
    private void bounds(IShapeUIModel s,Element e) {
        s.setRequestDefaultSize(false);s.setRequestFitSize(false);
        s.setBounds(number(e,"x"),number(e,"y"),number(e,"w"),number(e,"h"));
    }
    private void generate(Document source) throws Exception {
        ProjectManager pm=app.getProjectManager();
        if(!pm.saveProject()||!pm.newProject())throw new IllegalStateException("Cannot initialize clean project");
        pm.getProject().setName("SeasonPlan — native FreeBSD deployment");
        IPackage root=factory.createPackage();root.setName("Deployment view");tag(root,"root","Target design; native FreeBSD, manually deployed artifacts");
        Element spec=source.getDocumentElement();
        IDeploymentDiagramUIModel d=(IDeploymentDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM);
        d.setName(spec.getAttribute("name"));d.setDocumentation(PREFIX+spec.getAttribute("id"));d.setDefaultPackage(root);
        d.setZoomRatio(1.0);d.setAutoFitShapesSize(false);
        d.setAlignToGrid(false);d.setGridVisible(false);d.setRequestValidateSnapToGrid(false);d.setDiagramBackground(Color.WHITE);
        d.setShowNodeAttributes(false);d.setShowNodeOperations(false);d.setShowStereotypes(true);
        d.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
        for(Element n:children(spec,"element")) {
            String id=n.getAttribute("id"),type=n.getAttribute("type"),parent=n.getAttribute("parent");
            IModelElement m;IShapeUIModel s;
            if(type.equals("NOTE")) {
                s=(IShapeUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_NOTE);
                m=s.getModelElement();if(m==null)throw new IllegalStateException("Native note model missing");
            } else {
                if(type.equals("Node")) {
                    INode node=factory.createNode();m=node;
                    if(!n.getAttribute("stereotype").isEmpty())node.addStereotype(n.getAttribute("stereotype"));
                } else if(type.equals("Artifact")) {
                    IArtifact artifact=factory.createArtifact();m=artifact;artifact.setFileName(n.getAttribute("name").split("\n")[0]);
                } else if(type.equals("DeploymentSpecification"))m=factory.createDeploymentSpecification();
                else throw new IllegalArgumentException("Unknown native family "+type);
                s=(IShapeUIModel)dm.createDiagramElement(d,m);
            }
            m.setName(n.getAttribute("name"));
            tag(m,id,type.equals("NOTE")?n.getAttribute("text"):n.getAttribute("status")+"\n"+n.getAttribute("name"));
            if(parent.isEmpty())root.addChild(m);
            else {
                IModelElement p=models.get(parent);
                if(p==null)throw new IllegalArgumentException("Parent must precede child: "+parent);
                if(m instanceof INode)((INode)p).addNode((INode)m);
                else if(m instanceof IArtifact)((INode)p).addArtifact((IArtifact)m);
                else if(m instanceof IDeploymentSpecification)((INode)p).addDeploymentSpecification((IDeploymentSpecification)m);
                else ((IHasChildrenBaseModelElement)p).addChild(m);
                shapes.get(parent).addChild(s);
                if(m instanceof IArtifact || m instanceof IDeploymentSpecification) {
                    // VP 18.1 Deployment stores target/supplier -> deployed artifact/client.
                    IDeployment deployment=factory.createDeployment();deployment.setFrom(p);deployment.setTo(m);
                    tag(deployment,"deploy:"+id,"Native Deployment; target -> artifact in VP API; nested notation in diagram");root.addChild(deployment);
                }
            }
            Color fill=Color.WHITE;
            if(type.equals("Node"))fill=Color.decode(n.getAttribute("stereotype").equals("device")?"#e5edf5":"#eef5fa");
            if(type.equals("DeploymentSpecification"))fill=Color.decode("#fff3d4");
            if(type.equals("NOTE"))fill=Color.decode(parent.isEmpty()?"#fff9e8":"#ffffff");
            style(s,fill,id.equals("title")?26:(type.equals("Node")?20:17),type.equals("Node")||id.equals("title"));
            bounds(s,n);s.resetCaption();bounds(s,n);
            models.put(id,m);shapes.put(id,s);
        }
        dm.openDiagram(d);
        for(Element n:children(spec,"element"))bounds(shapes.get(n.getAttribute("id")),n);
        for(Element e:children(spec,"path")) {
            String id=e.getAttribute("id"),a=e.getAttribute("from"),b=e.getAttribute("to");
            IAssociation path=factory.createAssociation();path.setFrom(models.get(a));path.setTo(models.get(b));
            ((IAssociationEnd)path.getFromEnd()).setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);
            ((IAssociationEnd)path.getToEnd()).setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);
            path.setName(e.getAttribute("name"));tag(path,id,e.getAttribute("protocol")+"\nEvidence: "+e.getAttribute("evidence"));root.addChild(path);
            Point[] ps=points(e.getAttribute("points"));
            IConnectorUIModel c=(IConnectorUIModel)dm.createConnector(d,path,shapes.get(a),shapes.get(b),ps);
            c.setFromShape(shapes.get(a));c.setToShape(shapes.get(b));c.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
            c.setForeground(new Color(56,80,103));c.setBackground(Color.WHITE);
            c.getElementFont().setValues("Arial",Font.PLAIN,18,new Color(25,43,58));
            pin(c,shapes.get(a),ps[0],true);pin(c,shapes.get(b),ps[ps.length-1],false);
            c.clearPoints();for(Point p:ps)c.addPoint(p);
            c.setRequestResetCaption(true);c.resetCaption();ICaptionUIModel cap=c.getCaptionUIModel();
            cap.setVisible(true);cap.setSide(ICaptionUIModel.SIDE_NONE);
            String[] r=e.getAttribute("caption").split(",");
            cap.setBounds(Integer.parseInt(r[0]),Integer.parseInt(r[1]),Integer.parseInt(r[2]),Integer.parseInt(r[3]));
        }
        d.modified();
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
        for(IModelElement m:project.toAllLevelModelElementArray())if(!ident(m).isEmpty()) {
            Element e=append(report,r,"model","id",ident(m),"name",m.getName(),"parent",ident(m.getParent()),"type",m.getModelType());
            if(m instanceof INode)e.setAttribute("stereotype",String.join(",",((INode)m).toStereotypeArray()));
            if(m instanceof IDeployment) {
                e.setAttribute("from",ident(((IDeployment)m).getFrom()));e.setAttribute("to",ident(((IDeployment)m).getTo()));
            }
        }
        for(IDiagramUIModel d:project.toDiagramArray()) {
            d.setZoomRatio(1.0);dm.openDiagram(d);
            String id=d.getDocumentation().substring(PREFIX.length());
            Element de=append(report,r,"diagram","id",id,"name",d.getName(),"type",d.getType());
            for(IDiagramElement item:d.toDiagramElementArray()) {
                IModelElement m=item.getModelElement();if(m==null)continue;
                if(item instanceof IConnectorUIModel) {
                    IConnectorUIModel c=(IConnectorUIModel)item;IAssociation a=(IAssociation)m;
                    Element e=append(report,de,"edge","id",ident(m),"type",m.getModelType(),"name",m.getName(),
                        "from",ident(a.getFrom()),"to",ident(a.getTo()),"fromShape",ident(c.getFromShape().getModelElement()),"toShape",ident(c.getToShape().getModelElement()),
                        "captionSide",String.valueOf(c.getCaptionUIModel().getSide()),"documentation",m.getDocumentation(),
                        "fromNavigable",String.valueOf(((IAssociationEnd)a.getFromEnd()).getNavigable()),"toNavigable",String.valueOf(((IAssociationEnd)a.getToEnd()).getNavigable()));
                    StringJoiner route=new StringJoiner(";");for(Point p:c.getPoints())route.add(p.x+","+p.y);e.setAttribute("points",route.toString());
                    ICaptionUIModel cap=c.getCaptionUIModel();e.setAttribute("caption",cap.getX()+","+cap.getY()+","+cap.getWidth()+","+cap.getHeight());
                } else if(item instanceof IShapeUIModel) {
                    IShapeUIModel s=(IShapeUIModel)item;
                    IDiagramElement p=s.getParent();
                    append(report,de,"shape","id",ident(m),"type",m.getModelType(),"modelId",m.getId(),"name",m.getName(),"parent",ident(m.getParent()),
                        "shapeParent",p==null?"":ident(p.getModelElement()),"bounds",s.getX()+","+s.getY()+","+s.getWidth()+","+s.getHeight(),"documentation",m.getDocumentation());
                }
            }
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,id+".svg"),ModelConvertionManager.IMAGE_TYPE_SVG);
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,id+".png"),ModelConvertionManager.IMAGE_TYPE_PNG);
        }
        Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.INDENT,"yes");tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        tr.transform(new DOMSource(report),new StreamResult(new File(out,"native-reopened.xml")));
    }
}
