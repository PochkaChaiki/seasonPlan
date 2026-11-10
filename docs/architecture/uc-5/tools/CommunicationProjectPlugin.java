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

/** Documentation authoring only; compile --release 11 for VP 18.1. */
public class CommunicationProjectPlugin implements VPPlugin, VPPluginCommandLineSupport {
    private final ApplicationManager app=ApplicationManager.instance();
    private final DiagramManager dm=app.getDiagramManager();
    private final IModelElementFactory factory=IModelElementFactory.instance();
    private final Map<String,IPackage> packages=new LinkedHashMap<>();
    private final Map<String,IClass> classes=new LinkedHashMap<>();
    private final Map<String,IOperation> operations=new LinkedHashMap<>();
    private final Map<String,ICommunicationDiagramUIModel> diagrams=new LinkedHashMap<>();
    private final Map<String,IFrame> frames=new LinkedHashMap<>();
    private IPackage root;
    public void loaded(VPPluginInfo info){}
    public void unloaded(){}
    interface Work {void run() throws Exception;}
    private static void edt(Work w)throws Exception {
        if(SwingUtilities.isEventDispatchThread()){w.run();return;}
        Throwable[] error=new Throwable[1];SwingUtilities.invokeAndWait(()->{try{w.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new Exception(error[0]);
    }
    public void invoke(String[] raw){
        String[] a=raw.length==1?raw[0].split("\\|",-1):raw;
        try {
            if(a.length!=3)throw new IllegalArgumentException("Expected mode|specification|output-directory");
            File out=new File(a[2]);out.mkdirs();
            if(a[0].equals("generate")){
                Document spec=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(a[1]));
                edt(()->generate(spec,out));
                // Drain native deferred model/view updates before the command-line process closes its DB.
                edt(()->{});edt(()->{});
                edt(()->{if(!app.getProjectManager().saveProjectAs(new File(out,"SeasonPlan-UC5-communications.vpp")))throw new IllegalStateException("Native save failed");});
            }else if(a[0].equals("inspect"))edt(()->inspect(out));
            else throw new IllegalArgumentException("Unknown mode "+a[0]);
            Files.writeString(new File(out,a[0]+"-success.txt").toPath(),"OK\n");
        }catch(Throwable e){e.printStackTrace();if(a.length==3)try{Files.writeString(new File(a[2],"failure.txt").toPath(),e.toString());}catch(Exception ignored){}throw new RuntimeException(e);}
    }
    private static List<Element> children(Element e,String tag){
        List<Element> result=new ArrayList<>();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&((Element)n).getTagName().equals(tag))result.add((Element)n);
        return result;
    }
    private static int n(Element e,String key){return Integer.parseInt(e.getAttribute(key));}
    private static void tag(IModelElement m,String key){
        ITaggedValueContainer c=m.getTaggedValues();if(c==null){c=IModelElementFactory.instance().createTaggedValueContainer();m.setTaggedValues(c);}
        ITaggedValue v=c.getTaggedValueByName("sourceId");if(v==null){v=c.createTaggedValue();v.setName("sourceId");v.setType(ITaggedValue.TYPE_STRING);}v.setValue(key);
    }
    private static String id(IModelElement m){if(m==null||m.getTaggedValues()==null)return "";ITaggedValue v=m.getTaggedValues().getTaggedValueByName("sourceId");return v==null?"":v.getValueAsString();}
    private void style(IShapeUIModel s,Color color,int size){
        s.setRequestDefaultSize(false);s.setRequestFitSize(false);s.setDisplayStereotypeIcon(false);
        s.getFillColor().setColor1(color);s.getFillColor().setColor2(color);s.setBackground(color);s.setForeground(new Color(43,62,81));
        s.getElementFont().setValues("Arial",Font.PLAIN,size,new Color(25,40,56));
    }
    private void bounds(IShapeUIModel s,Element e){s.setRequestDefaultSize(false);s.setRequestFitSize(false);s.setBounds(n(e,"x"),n(e,"y"),n(e,"w"),n(e,"h"));}
    private IShapeUIModel note(IDiagramUIModel d,String key,String text,int x,int y,int w,int h,int font){
        IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_NOTE);
        IModelElement m=s.getModelElement();if(m==null)throw new IllegalStateException("Unregistered note");
        m.setName(text);m.setDocumentation(text);tag(m,key);style(s,new Color(255,250,229),font);
        s.setBounds(x,y,w,h);s.resetCaption();s.setBounds(x,y,w,h);return s;
    }
    private IClass type(String key,String name,String pkg){
        IClass c=classes.get(key);if(c==null){c=factory.createClass();c.setName(name);tag(c,"type:"+key);packages.get(pkg).addChild(c);classes.put(key,c);}return c;
    }
    private void generate(Document source,File out)throws Exception {
        ProjectManager pm=app.getProjectManager();if(!pm.saveProject()||!pm.newProject())throw new IllegalStateException("Cannot create clean native project");
        pm.getProject().setName("SeasonPlan UC-5 — UML 2 Communication views");
        root=factory.createPackage();root.setName("UC-5 — Завершение сезона");tag(root,"uc5-root");
        Element doc=source.getDocumentElement();
        for(Element p:children(children(doc,"packages").get(0),"package")){
            IPackage pkg=factory.createPackage();pkg.setName(p.getAttribute("name"));pkg.setDocumentation(p.getAttribute("source"));
            tag(pkg,"package:"+p.getAttribute("id"));root.addChild(pkg);packages.put(p.getAttribute("id"),pkg);
        }
        for(Element o:children(children(doc,"operations").get(0),"operation")){
            IClass c=type(o.getAttribute("owner"),o.getAttribute("owner"),o.getAttribute("package"));
            if(o.getAttribute("interface").equals("true")&&!c.hasStereotype("Interface"))c.addStereotype("Interface");
            IOperation op=c.createOperation();op.setName(o.getAttribute("name"));op.setReturnType(o.getAttribute("returns"));op.setVisibility(o.getAttribute("visibility"));
            tag(op,"op:"+o.getAttribute("id"));op.setDocumentation(o.getAttribute("status")+"\n"+o.getAttribute("source")+"\n"+o.getAttribute("evidence"));
            if(!o.getAttribute("parameters").isEmpty())for(String value:o.getAttribute("parameters").split(";")){
                int split=value.lastIndexOf(' ');IParameter p=op.createParameter();p.setType(value.substring(0,split));p.setName(value.substring(split+1));p.setDirection(IParameter.DIRECTION_IN);
            }
            operations.put(o.getAttribute("id"),op);
        }
        for(Element spec:children(doc,"diagram")){
            String key=spec.getAttribute("id");ICommunicationDiagramUIModel d=(ICommunicationDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMMUNICATION_DIAGRAM);
            d.setName(spec.getAttribute("name"));d.setDocumentation("uc5:"+key);d.setDefaultPackage(root);d.setZoomRatio(1.0);
            d.setAutoFitShapesSize(false);d.setAlignToGrid(false);d.setGridVisible(false);d.setRequestValidateSnapToGrid(false);d.setDiagramBackground(Color.WHITE);
            d.setShowSequenceNumbers(true);d.setCreateOneMessagePerDirection(false);d.setShowMessagesOperationSignature(false);
            d.setInteractionLifeLinePresentationOption(IFramedInteractionDiagramUIModel.ILLP_UNDERLINES_WITH_CLASSIFIERS);
            IFrame f=d.getRootFrame(true);f.setName(spec.getAttribute("name"));f.setType("sd");tag(f,"frame:"+key);
            IInteraction interaction=factory.createInteraction();interaction.setName(spec.getAttribute("name"));interaction.setDiagramId(d.getId());
            interaction.setPrecondition(spec.getAttribute("precondition"));interaction.setPostcondition(spec.getAttribute("postcondition"));tag(interaction,"interaction:"+key);
            root.addChild(interaction);interaction.addChild(f);interaction.addSubDiagram(d);diagrams.put(key,d);frames.put(key,f);
        }
        for(Element spec:children(doc,"diagram"))diagram(spec);

    }
    private void diagram(Element spec){
        String key=spec.getAttribute("id");ICommunicationDiagramUIModel d=diagrams.get(key);IFrame frame=frames.get(key);
        Map<String,IModelElement> models=new LinkedHashMap<>();Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();
        for(Element p:children(spec,"participant")){
            IModelElement model;
            if(p.getAttribute("kind").equals("actor")){
                IActor actor=factory.createActor();actor.setName(p.getAttribute("name"));root.addChild(actor);tag(actor,key+":actor-type:"+p.getAttribute("id"));
                IInteractionActor participant=factory.createInteractionActor();participant.setReferencedActor(actor);model=participant;
            }else {
                IInteractionLifeLine line=factory.createInteractionLifeLine();String prefix=spec.getAttribute("view").equals("implementation")?"":key+":";
                line.setBaseClassifier(type(prefix+p.getAttribute("type"),p.getAttribute("type"),p.getAttribute("package")));model=line;
            }
            model.setName(p.getAttribute("name"));model.setDocumentation(p.getAttribute("status")+"; package="+p.getAttribute("package"));
            frame.addChild(model);tag(model,key+":"+p.getAttribute("id"));
            IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,model);style(s,p.getAttribute("status").equals("proposed")?new Color(255,239,218):new Color(232,243,253),17);
            if(!spec.getAttribute("view").equals("implementation")&&!p.getAttribute("kind").equals("actor"))s.setCustomText(p.getAttribute("name"));
            bounds(s,p);s.resetCaption();bounds(s,p);models.put(p.getAttribute("id"),model);shapes.put(p.getAttribute("id"),s);
        }
        dm.openDiagram(d);for(Element p:children(spec,"participant"))bounds(shapes.get(p.getAttribute("id")),p);
        Map<String,IInteractionLifeLineLink> links=new LinkedHashMap<>();Map<String,IConnectorUIModel> connectors=new LinkedHashMap<>();
        for(Element l:children(spec,"link")){
            IInteractionLifeLineLink link=factory.createInteractionLifeLineLink();link.setName("");link.setFrom(models.get(l.getAttribute("from")));link.setTo(models.get(l.getAttribute("to")));tag(link,l.getAttribute("id"));frame.addChild(link);
            List<Point> pts=new ArrayList<>();for(String value:l.getAttribute("points").split(";")){String[] xy=value.split(",");pts.add(new Point(Integer.parseInt(xy[0]),Integer.parseInt(xy[1])));}
            IShapeUIModel from=shapes.get(l.getAttribute("from")),to=shapes.get(l.getAttribute("to"));
            IConnectorUIModel c=(IConnectorUIModel)dm.createConnector(d,link,from,to,pts.toArray(new Point[0]));
            c.setFromShape(from);c.setToShape(to);c.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);c.setForeground(new Color(74,94,112));
            pin(c,from,pts.get(0),true);pin(c,to,pts.get(pts.size()-1),false);c.clearPoints();for(Point p:pts)c.addPoint(p);
            c.getCaptionUIModel().setVisible(false);links.put(l.getAttribute("id"),link);connectors.put(l.getAttribute("id"),c);
        }
        Map<String,IMessage> messages=new LinkedHashMap<>();
        for(Element ms:children(spec,"message")){
            IInteractionLifeLineLink link=links.get(ms.getAttribute("link"));
            boolean reverse=!link.getFrom().getId().equals(models.get(ms.getAttribute("from")).getId());
            IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,reverse?IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_REVERSE_MESSAGE:IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_MESSAGE);
            if(!(s.getModelElement() instanceof IMessage))throw new IllegalStateException("Missing native message model");
            IMessage m=(IMessage)s.getModelElement();m.setName(ms.getAttribute("text"));m.setSequenceNumber(ms.getAttribute("number"));tag(m,ms.getAttribute("id"));
            m.setFrom(models.get(ms.getAttribute("from")));m.setTo(models.get(ms.getAttribute("to")));m.setAsynchronous(false);
            m.setType(IMessage.TYPE_MESSAGE); // The self-link, not sequence-only self presentation, models self dispatch.
            if(ms.getAttribute("kind").equals("reply")){
                IActionTypeReturn action=factory.createActionTypeReturn();action.setRecurrenceCondition(ms.getAttribute("guard"));action.setIteration(false);m.setActionType(action);
                IMessage call=messages.get(ms.getAttribute("replyTo"));if(call!=null&&call.getReturnMessage()==null)call.setReturnMessage(m);
            }else {
                IActionTypeCall action=factory.createActionTypeCall();action.setRecurrenceCondition(ms.getAttribute("guard"));action.setIteration(ms.getAttribute("iteration").equals("true"));
                if(!ms.getAttribute("operation").isEmpty()){
                    IOperation op=operations.get(ms.getAttribute("operation"));action.setOperation(op);action.setOperationType(IActionTypeCall.OPERATION_TYPE_OPERATION);
                    if(!ms.getAttribute("detail").isEmpty()){
                        IClass owner=classes.get("CompletionService");
                        if(owner.indexOfSubDiagram(diagrams.get(ms.getAttribute("detail")))<0)owner.addSubDiagram(diagrams.get(ms.getAttribute("detail")));
                    }
                }
                m.setActionType(action);
            }
            link.addMessage(m);
            connectors.get(ms.getAttribute("link")).addChild(s);
            m.setFrom(models.get(ms.getAttribute("from")));m.setTo(models.get(ms.getAttribute("to")));
            // Single-message views do not need the VP grouped-message ID mechanism.
            Color ink=new Color(43,62,81);style(s,ink,15);s.setBackground(ink);
            bounds(s,ms);s.resetCaption();bounds(s,ms);
            ICaptionUIModel cap=s.getCaptionUIModel();cap.setSide(ICaptionUIModel.SIDE_NONE);cap.setVisible(true);cap.setBounds(n(ms,"x"),n(ms,"y"),n(ms,"w"),n(ms,"h")-12);
            messages.put(ms.getAttribute("id"),m);
        }
        note(d,key+":title",spec.getAttribute("name"),60,35,n(spec,"width")-120,65,24);
        note(d,key+":notes",spec.getAttribute("notes"),60,n(spec,"height")-390,n(spec,"width")-120,360,16);
        d.modified();
    }
    private void pin(IConnectorUIModel c,IShapeUIModel s,Point p,boolean from){
        int dx=p.x-s.getX(),dy=p.y-s.getY();Point2D ratio=new Point2D.Double((double)dx/s.getWidth(),(double)dy/s.getHeight());
        if(from){c.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);c.setFromPinRatio(ratio);c.setFromShapeXDiff(dx);c.setFromShapeYDiff(dy);c.setUseFromShapeCenter(false);}
        else{c.setToPinType(IConnectorUIModel.PINTYPE_RATIO);c.setToPinRatio(ratio);c.setToShapeXDiff(dx);c.setToShapeYDiff(dy);c.setUseToShapeCenter(false);}
    }
    private static Element append(Document doc,Element parent,String name,String... values){Element e=doc.createElement(name);parent.appendChild(e);for(int i=0;i<values.length;i+=2)e.setAttribute(values[i],values[i+1]);return e;}
    private static String rect(IShapeUIModel s){return s.getX()+","+s.getY()+","+s.getWidth()+","+s.getHeight();}
    private String detailDiagrams(IOperation op){StringJoiner names=new StringJoiner(",");IDiagramUIModel[] ds=op.getParent().toSubDiagramArray();if(ds!=null)for(IDiagramUIModel d:ds)if(d!=null&&d.getDocumentation()!=null&&d.getDocumentation().startsWith("uc5:"))names.add(d.getDocumentation().substring(4));return names.toString();}
    private void inspect(File out)throws Exception {
        Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();Element r=doc.createElement("native-project");doc.appendChild(r);
        IProject project=app.getProjectManager().getProject();r.setAttribute("name",project.getName());
        Map<String,IInteraction> interactions=new HashMap<>();
        for(IModelElement m:project.toAllLevelModelElementArray()){
            if(m instanceof IPackage&&!id(m).isEmpty())append(doc,r,"package","id",id(m),"name",m.getName());
            if(m instanceof IInteraction){IInteraction i=(IInteraction)m;interactions.put(i.getDiagramId(),i);}
            if(m instanceof IClass)for(IOperation op:((IClass)m).toOperationArray())if(!id(op).isEmpty()){
                Element e=append(doc,r,"operation","id",id(op),"owner",id(m),"name",op.getName(),"returns",op.getReturnTypeAsString(),"visibility",op.getVisibility(),"documentation",op.getDocumentation());
                for(IParameter p:op.toParameterArray())append(doc,e,"parameter","name",p.getName(),"type",p.getTypeAsString());
            }
        }
        for(IDiagramUIModel base:project.toDiagramArray()){
            if(base.getDocumentation()==null||!base.getDocumentation().startsWith("uc5:"))continue;
            ICommunicationDiagramUIModel d=(ICommunicationDiagramUIModel)base;String key=d.getDocumentation().substring(4);d.setZoomRatio(1.0);dm.openDiagram(d);
            IInteraction interaction=interactions.get(d.getId());
            Element de=append(doc,r,"diagram","id",key,"name",d.getName(),"type",d.getType(),"sequenceNumbers",String.valueOf(d.isShowSequenceNumbers()),"precondition",interaction.getPrecondition(),"postcondition",interaction.getPostcondition());
            for(IDiagramElement item:d.toDiagramElementArray()){
                IModelElement m=item.getModelElement();if(m==null)continue;
                if(m instanceof IInteractionLifeLineLink){
                    IInteractionLifeLineLink l=(IInteractionLifeLineLink)m;IConnectorUIModel c=(IConnectorUIModel)item;StringJoiner pts=new StringJoiner(";");for(Point p:c.getPoints())pts.add(p.x+","+p.y);
                    StringJoiner msgs=new StringJoiner(",");IMessage[] ma=l.toMessageArray();if(ma!=null)for(IMessage msg:ma)msgs.add(id(msg));
                    append(doc,de,"link","id",id(m),"from",id(l.getFrom()),"to",id(l.getTo()),"fromShape",id(c.getFromShape().getModelElement()),"toShape",id(c.getToShape().getModelElement()),"points",pts.toString(),"messages",msgs.toString());
                }else if(m instanceof IMessage){
                    IMessage msg=(IMessage)m;IShapeUIModel s=(IShapeUIModel)item;IModelElement action=msg.getActionType();String guard="",iteration="false",op="",detail="";
                    if(action instanceof IActionTypeCall){IActionTypeCall a=(IActionTypeCall)action;guard=a.getRecurrenceCondition();iteration=String.valueOf(a.isIteration());op=id(a.getOperation());if(a.getOperation() instanceof IOperation)detail=detailDiagrams((IOperation)a.getOperation());}
                    if(action instanceof IActionTypeReturn){guard=((IActionTypeReturn)action).getRecurrenceCondition();iteration=String.valueOf(((IActionTypeReturn)action).isIteration());}
                    IModelElement parent=s.getParent()==null?null:s.getParent().getModelElement();
                    append(doc,de,"message","id",id(m),"name",m.getName(),"from",id(msg.getFrom()),"to",id(msg.getTo()),"number",msg.getSequenceNumber(),"operation",op,"guard",guard,"iteration",iteration,"returnMessage",id(msg.getReturnMessage()),
                        "link",id(parent),"bounds",rect(s),"caption",s.getCaptionUIModel().getX()+","+s.getCaptionUIModel().getY()+","+s.getCaptionUIModel().getWidth()+","+s.getCaptionUIModel().getHeight(),"captionSide",String.valueOf(s.getCaptionUIModel().getSide()),"shapeType",s.getShapeType(),"actionType",action.getModelType(),"detailDiagrams",detail);
                }else if(item instanceof IShapeUIModel){
                    IShapeUIModel s=(IShapeUIModel)item;
                    if(m instanceof IInteractionLifeLine||m instanceof IInteractionActor){
                        IModelElement classifier=m instanceof IInteractionActor?((IInteractionActor)m).getReferencedActor():((IInteractionLifeLine)m).getBaseClassifierAsModel();
                        append(doc,de,"participant","id",id(m),"name",m.getName(),"type",m.getModelType(),"bounds",rect(s),"classifier",id(classifier),"classifierType",classifier.getModelType(),"package",id(classifier.getParent()));
                    }else if(m.getModelType().equals("NOTE"))append(doc,de,"note","id",id(m),"name",m.getName(),"documentation",m.getDocumentation(),"bounds",rect(s));
                }
            }
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,key+".svg"),ModelConvertionManager.IMAGE_TYPE_SVG);
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,key+".png"),ModelConvertionManager.IMAGE_TYPE_PNG);
        }
        Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.INDENT,"yes");tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");tr.transform(new DOMSource(doc),new StreamResult(new File(out,"native-reopened.xml")));
    }
}
