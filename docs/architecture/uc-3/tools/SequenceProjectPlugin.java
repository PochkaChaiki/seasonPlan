import com.vp.plugin.*;
import com.vp.plugin.diagram.*;
import com.vp.plugin.diagram.shape.*;
import com.vp.plugin.diagram.connector.*;
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

/** Documentation authoring, Java 11 for the bundled VP 18.1 JVM. */
public class SequenceProjectPlugin implements VPPlugin, VPPluginCommandLineSupport {
    private final ApplicationManager app=ApplicationManager.instance();
    private final DiagramManager dm=app.getDiagramManager();
    private final IModelElementFactory factory=IModelElementFactory.instance();
    private final Map<String,IPackage> packages=new LinkedHashMap<>();
    private final Map<String,IClass> classes=new LinkedHashMap<>();
    private final Map<String,IOperation> operations=new LinkedHashMap<>();
    private final Map<String,IInteractionDiagramUIModel> diagrams=new LinkedHashMap<>();
    private final Map<String,IFrame> frames=new LinkedHashMap<>();
    private IPackage root;
    public void loaded(VPPluginInfo info){}
    public void unloaded(){}
    interface Work{void run() throws Exception;}
    private static void edt(Work work)throws Exception{
        if(SwingUtilities.isEventDispatchThread()){work.run();return;}
        Throwable[] error=new Throwable[1];SwingUtilities.invokeAndWait(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new Exception(error[0]);
    }
    public void invoke(String[] raw){
        String[] a=raw.length==1?raw[0].split("\\|",-1):raw;
        try{
            if(a.length!=3)throw new IllegalArgumentException("Expected mode|source XML|output directory");
            File out=new File(a[2]);out.mkdirs();
            if(a[0].equals("generate")){
                Document spec=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(a[1]));
                edt(()->generate(spec,out));
            }else if(a[0].equals("inspect"))edt(()->inspect(out));
            else throw new IllegalArgumentException("Unknown mode");
            Files.writeString(new File(out,a[0]+"-success.txt").toPath(),"OK\n");
        }catch(Throwable e){e.printStackTrace();if(a.length==3)try{Files.writeString(new File(a[2],"failure.txt").toPath(),e.toString());}catch(Exception ignored){}throw new RuntimeException(e);}
    }
    private static java.util.List<Element> children(Element e,String tag){
        java.util.List<Element> result=new ArrayList<>();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&((Element)n).getTagName().equals(tag))result.add((Element)n);
        return result;
    }
    private static int n(Element e,String key){return Integer.parseInt(e.getAttribute(key));}
    private static void tag(IModelElement m,String id){
        ITaggedValueContainer values=m.getTaggedValues();
        if(values==null){values=IModelElementFactory.instance().createTaggedValueContainer();m.setTaggedValues(values);}
        ITaggedValue v=values.getTaggedValueByName("sourceId");if(v==null){v=values.createTaggedValue();v.setName("sourceId");v.setType(ITaggedValue.TYPE_STRING);}v.setValue(id);
    }
    private static String id(IModelElement m){if(m==null||m.getTaggedValues()==null)return "";ITaggedValue v=m.getTaggedValues().getTaggedValueByName("sourceId");return v==null?"":v.getValueAsString();}
    private void style(IShapeUIModel s,Color color,int size){
        s.setRequestDefaultSize(false);s.setRequestFitSize(false);s.setDisplayStereotypeIcon(false);
        s.getFillColor().setColor1(color);s.getFillColor().setColor2(color);s.setBackground(color);
        s.setForeground(new Color(55,72,90));s.getElementFont().setValues("Arial",Font.PLAIN,size,new Color(25,40,56));
    }
    private void bounds(IShapeUIModel s,Element e){s.setRequestDefaultSize(false);s.setRequestFitSize(false);s.setBounds(n(e,"x"),n(e,"y"),n(e,"w"),n(e,"h"));}
    private IShapeUIModel note(IDiagramUIModel d,String key,String text,int x,int y,int w,int h,int font){
        IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_NOTE);
        IModelElement m=s.getModelElement();if(m==null)throw new IllegalStateException("Unregistered native note");
        m.setName(text);m.setDocumentation(text);tag(m,key);style(s,new Color(255,250,229),font);
        s.setBounds(x,y,w,h);s.resetCaption();s.setBounds(x,y,w,h);return s;
    }
    private IClass type(String key,String name,String pkg){
        IClass c=classes.get(key);if(c==null){c=factory.createClass();c.setName(name);packages.get(pkg).addChild(c);tag(c,"type:"+key);classes.put(key,c);}return c;
    }
    private void setup(IDiagramUIModel d,String name){
        d.setName(name);d.setZoomRatio(1.0);d.setAutoFitShapesSize(false);d.setAlignToGrid(false);d.setGridVisible(false);
        d.setRequestValidateSnapToGrid(false);d.setDiagramBackground(Color.WHITE);
    }
    private void generate(Document source,File out)throws Exception{
        ProjectManager pm=app.getProjectManager();if(!pm.saveProject()||!pm.newProject())throw new IllegalStateException("Cannot create clean project");
        pm.getProject().setName("SeasonPlan UC-3 — UML 2 sequence views");
        root=factory.createPackage();root.setName("UC-3 — Подготовка сезона к запуску");tag(root,"uc3-root");
        Element doc=source.getDocumentElement();Element ps=children(doc,"packages").get(0);
        for(Element p:children(ps,"package")){
            IPackage m=factory.createPackage();m.setName(p.getAttribute("name"));m.setDocumentation(p.getAttribute("body"));tag(m,"package:"+p.getAttribute("id"));root.addChild(m);packages.put(p.getAttribute("id"),m);
        }
        for(Element o:children(children(doc,"operations").get(0),"operation")){
            IClass c=type(o.getAttribute("owner"),o.getAttribute("owner"),o.getAttribute("package"));
            IOperation op=c.createOperation();op.setName(o.getAttribute("name"));op.setReturnType(o.getAttribute("returns"));
            op.setVisibility(o.getAttribute("name").equals("validateTask")?IOperation.VISIBILITY_PRIVATE:IOperation.VISIBILITY_PUBLIC);
            tag(op,"op:"+o.getAttribute("id"));op.setDocumentation(o.getAttribute("status")+"\n"+o.getAttribute("source")+"\n"+o.getAttribute("evidence"));
            if(!o.getAttribute("parameters").isEmpty())for(String param:o.getAttribute("parameters").split(";")){
                int split=param.lastIndexOf(' ');IParameter p=op.createParameter();p.setName(param.substring(split+1));p.setType(param.substring(0,split));p.setDirection(IParameter.DIRECTION_IN);
            }
            operations.put(o.getAttribute("id"),op);
        }
        // Create every native root frame before linking InteractionUse references.
        for(Element spec:children(doc,"diagram")){
            String key=spec.getAttribute("id");IInteractionDiagramUIModel d=(IInteractionDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM);
            setup(d,spec.getAttribute("name"));d.setDocumentation("uc3:"+key);d.setDefaultPackage(root);
            d.setShowDiagramFrame(true);d.setShowActivations(true);d.setAutoExtendActivations(false);d.setShowSequenceNumbers(false);
            d.setShowMessageName(true);d.setShowMessagesOperationSignature(false);d.setSequenceNumberHandling(IInteractionDiagramUIModel.MANUAL);
            d.setDisplayLifeLinesAsRobustnessAnalysisIcon(IInteractionDiagramUIModel.DISPLAY_LIFELINE_AS_ROBUSTNESS_ANALYSIS_ICON_NO);
            d.setInteractionLifeLinePresentationOption(IFramedInteractionDiagramUIModel.ILLP_NO_UNDERLINES);
            IFrame f=d.getRootFrame(true);f.setName(spec.getAttribute("name"));f.setType("sd");tag(f,"frame:"+key);
            IInteraction interaction=factory.createInteraction();interaction.setName(spec.getAttribute("name"));interaction.setDiagramId(d.getId());
            interaction.setPrecondition(spec.getAttribute("precondition"));
            interaction.setPostcondition(spec.getAttribute("postcondition"));
            tag(interaction,"interaction:"+key);root.addChild(interaction);interaction.addChild(f);interaction.addSubDiagram(d);
            diagrams.put(key,d);frames.put(key,f);
        }
        for(Element spec:children(doc,"diagram"))sequence(spec);
        packageDiagram(ps);
        if(!pm.saveProjectAs(new File(out,"SeasonPlan-UC3-sequences.vpp")))throw new IllegalStateException("Native save failed");
    }
    private void sequence(Element spec){
        String key=spec.getAttribute("id");IInteractionDiagramUIModel d=diagrams.get(key);IFrame frame=frames.get(key);
        Map<String,IModelElement> models=new LinkedHashMap<>();Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();
        Map<String,IInteractionOperand> operands=new LinkedHashMap<>();Map<String,IShapeUIModel> operandShapes=new LinkedHashMap<>();
        Map<String,IActor> actorTypes=new LinkedHashMap<>();
        for(Element l:children(spec,"lifeline")){
            IInteractionLifeLine model=factory.createInteractionLifeLine();model.setName(l.getAttribute("name"));frame.addChild(model);
            if(l.getAttribute("kind").equals("actor")){
                IActor actor=factory.createActor();actor.setName(l.getAttribute("name"));packages.get("frontend").addChild(actor);tag(actor,key+":actor-type:"+l.getAttribute("id"));
                model.setBaseClassifier(actor);actorTypes.put(l.getAttribute("id"),actor);
            }else{
                // Classifiers are subsystem representatives in the existing package view.
                String typeKey=(key.equals("01-use-case")||key.equals("02-logical")?key+":":"")+l.getAttribute("type");
                model.setBaseClassifier(type(typeKey,l.getAttribute("type"),l.getAttribute("package")));
            }
            IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,model);
            ((IInteractionLifeLineUIModel)s).setShowClassifier(!key.equals("02-logical")&&!l.getAttribute("kind").equals("actor"));
            tag(model,key+":"+l.getAttribute("id"));model.setDocumentation("System package: "+l.getAttribute("package"));
            style(s,new Color(234,243,252),17);bounds(s,l);s.resetCaption();bounds(s,l);
            if(s instanceof IInteractionDiagramFrameShapeUIModel)((IInteractionDiagramFrameShapeUIModel)s).setParentFrame(frame);
            models.put(l.getAttribute("id"),model);shapes.put(l.getAttribute("id"),s);
        }
        dm.openDiagram(d);for(Element l:children(spec,"lifeline"))bounds(shapes.get(l.getAttribute("id")),l);
        // Native Actor symbols stay compact; their typed Lifelines supply dashed tails.
        for(Element symbol:children(spec,"actor-symbol")){
            IShapeUIModel icon=(IShapeUIModel)dm.createDiagramElement(d,actorTypes.get(symbol.getAttribute("lifeline")));
            shapes.get(symbol.getAttribute("lifeline")).addChild(icon);style(icon,Color.WHITE,15);bounds(icon,symbol);
            icon.resetCaption();bounds(icon,symbol);icon.getCaptionUIModel().setVisible(false);
        }
        for(Element fs:children(spec,"fragment")){
            ICombinedFragment f=factory.createCombinedFragment();f.setName(fs.getAttribute("id"));f.setInteractionOperator(fs.getAttribute("operator"));
            String parent=fs.getAttribute("parent");
            if(parent.isEmpty())frame.addChild(f);else{operands.get(parent).addCombinedFragment(f);operands.get(parent).addChild(f);}
            tag(f,fs.getAttribute("id"));
            for(IModelElement l:models.values())f.addCoveredLifeLine(l);
            ICombinedFragmentUIModel s=(ICombinedFragmentUIModel)dm.createDiagramElement(d,f);
            if(!parent.isEmpty())operandShapes.get(parent).addChild(s);
            style(s,Color.WHITE,15);bounds(s,fs);s.resetCaption();bounds(s,fs);
            for(Element os:children(fs,"operand")){
                IInteractionOperand o=factory.createInteractionOperand();o.setName(os.getAttribute("id"));f.addOperand(o);f.addChild(o);tag(o,os.getAttribute("id"));
                IInteractionConstraint guard=factory.createInteractionConstraint();guard.setName(os.getAttribute("guard"));guard.setConstraint(os.getAttribute("guard"));o.setGuard(guard);
                IShapeUIModel v=(IShapeUIModel)dm.createDiagramElement(d,o);s.addChild(v);style(v,Color.WHITE,15);
                v.setBounds(n(fs,"x"),n(os,"y"),n(fs,"w"),n(os,"h"));v.resetCaption();v.setBounds(n(fs,"x"),n(os,"y"),n(fs,"w"),n(os,"h"));
                operands.put(os.getAttribute("id"),o);operandShapes.put(os.getAttribute("id"),v);
            }
        }
        Map<String,IActivation> activations=new LinkedHashMap<>();
        for(Element as:children(spec,"activation")){
            IInteractionLifeLine owner=(IInteractionLifeLine)models.get(as.getAttribute("lifeline"));
            IActivation activation=factory.createActivation();activation.setName("execution");tag(activation,as.getAttribute("id"));
            owner.addActivation(activation);owner.addChild(activation);
            IShapeUIModel bar=(IShapeUIModel)dm.createDiagramElement(d,activation);
            shapes.get(as.getAttribute("lifeline")).addChild(bar);style(bar,new Color(219,237,253),12);bounds(bar,as);
            activations.put(as.getAttribute("id"),activation);
        }
        Map<String,IMessage> messages=new LinkedHashMap<>();
        for(Element ms:children(spec,"message")){
            String from=ms.getAttribute("from"),to=ms.getAttribute("to"),kind=ms.getAttribute("kind");
            IMessage m=factory.createMessage();m.setName(ms.getAttribute("text"));tag(m,ms.getAttribute("id"));
            m.setFrom(models.get(from));m.setTo(models.get(to));m.setSequenceNumber(String.valueOf(messages.size()+1));
            if(!ms.getAttribute("fromActivation").isEmpty())m.setFromActivation(activations.get(ms.getAttribute("fromActivation")));
            if(!ms.getAttribute("toActivation").isEmpty())m.setToActivation(activations.get(ms.getAttribute("toActivation")));
            m.setAsynchronous(kind.equals("async"));m.setType(from.equals(to)?IMessage.TYPE_SELF_MESSAGE:IMessage.TYPE_MESSAGE);
            if(kind.equals("reply")){
                IActionTypeReturn ret=factory.createActionTypeReturn();m.setActionType(ret);
                IMessage call=messages.get(ms.getAttribute("replyTo"));if(call==null)throw new IllegalStateException("Missing reply call");call.setReturnMessage(m);
            }else if(!ms.getAttribute("operation").isEmpty()){
                IActionTypeCall call=factory.createActionTypeCall();call.setOperation(operations.get(ms.getAttribute("operation")));call.setOperationType(IActionTypeCall.OPERATION_TYPE_OPERATION);call.setTextReference("");m.setActionType(call);
            }else m.setActionType(factory.createActionTypeCall());
            String operand=ms.getAttribute("operand");
            if(!operand.isEmpty()){operands.get(operand).addMessage(m);operands.get(operand).addChild(m);}else frame.addChild(m);
            int y=n(ms,"y"),a=shapes.get(from).getX()+shapes.get(from).getWidth()/2,b=shapes.get(to).getX()+shapes.get(to).getWidth()/2;
            Point[] points=from.equals(to)?new Point[]{new Point(a,y),new Point(a+80,y),new Point(a+80,y+24),new Point(a,y+24)}:new Point[]{new Point(a,y),new Point(b,y)};
            IMessageUIModel c=(IMessageUIModel)dm.createConnector(d,m,shapes.get(from),shapes.get(to),points);
            c.setFromShape(shapes.get(from));c.setToShape(shapes.get(to));c.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
            c.setShowMessageOperationSignature(IMessageUIModel.SHOW_MSG_OPERATION_SIGNATURE_HIDE);c.setShowMessageName(IMessageUIModel.SHOW_MESSAGE_NAME_YES);
            c.setForeground(new Color(43,62,81));c.setBackground(new Color(43,62,81));c.getElementFont().setValues("Arial",Font.PLAIN,15,new Color(25,40,56));
            pin(c,shapes.get(from),points[0],true);pin(c,shapes.get(to),points[points.length-1],false);
            c.clearPoints();for(Point p:points)c.addPoint(p);c.setRequestResetCaption(true);c.resetCaption();
            ICaptionUIModel cap=c.getCaptionUIModel();cap.setSide(ICaptionUIModel.SIDE_NONE);cap.setVisible(true);
            String text=ms.getAttribute("text");int longest=0;for(String row:text.split("\n"))longest=Math.max(longest,row.length());
            int w=Math.min(n(spec,"width")-130,Math.max(320,longest*9+50));int h=text.contains("\n")?48:30;
            int x=Math.max(70,Math.min(n(spec,"width")-w-30,from.equals(to)?a+95:(a+b-w)/2));
            cap.setBounds(x,y-h-6,w,h);messages.put(ms.getAttribute("id"),m);
        }
        for(Element rs:children(spec,"ref")){
            IInteractionOccurrence ref=factory.createInteractionOccurrence();ref.setName(rs.getAttribute("text"));ref.setRefersTo(frames.get(rs.getAttribute("target")));frame.addChild(ref);tag(ref,key+":ref:"+rs.getAttribute("target"));
            for(String l:rs.getAttribute("covered").split(","))ref.addCoveredLifeLine(models.get(l));
            IInteractionOccurrenceUIModel s=(IInteractionOccurrenceUIModel)dm.createDiagramElement(d,ref);s.setShowPreview(false);style(s,new Color(239,248,238),17);bounds(s,rs);s.resetCaption();bounds(s,rs);
        }
        note(d,key+":title",spec.getAttribute("name"),65,35,n(spec,"width")-100,65,24);
        note(d,key+":notes",spec.getAttribute("notes"),65,n(spec,"height")-190,n(spec,"width")-100,175,15);
        d.modified();
    }
    private void pin(IConnectorUIModel c,IShapeUIModel s,Point p,boolean from){
        int dx=p.x-s.getX(),dy=p.y-s.getY();Point2D ratio=new Point2D.Double((double)dx/s.getWidth(),(double)dy/s.getHeight());
        if(from){c.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);c.setFromPinRatio(ratio);c.setFromShapeXDiff(dx);c.setFromShapeYDiff(dy);c.setUseFromShapeCenter(false);}
        else{c.setToPinType(IConnectorUIModel.PINTYPE_RATIO);c.setToPinRatio(ratio);c.setToShapeXDiff(dx);c.setToShapeYDiff(dy);c.setUseToShapeCenter(false);}
    }
    private void packageDiagram(Element ps){
        IPackageDiagramUIModel d=(IPackageDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM);
        setup(d,"0. UC-3 — Пакеты существующей архитектуры");d.setDocumentation("uc3:00-packages");d.setDefaultPackage(root);d.setShowDiagramFrame(false);
        Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();int index=0;
        for(Element p:children(ps,"package")){
            int x=80+(index%4)*530,y=200+(index/4)*440;
            IPackageUIModel s=(IPackageUIModel)dm.createDiagramElement(d,packages.get(p.getAttribute("id")));s.setShowOwnerOption(IPackageUIModel.SHOW_OWNER_NONE);style(s,new Color(233,242,252),20);
            s.setBounds(x,y,480,330);s.resetCaption();s.setBounds(x,y,480,330);
            IShapeUIModel body=note(d,"body:"+p.getAttribute("id"),p.getAttribute("body"),x+15,y+65,450,245,16);s.addChild(body);packages.get(p.getAttribute("id")).addChild(body.getModelElement());
            shapes.put(p.getAttribute("id"),s);index++;
        }
        note(d,"00-packages:title","UC-3 — Пакеты существующей архитектуры\nВыборка из docs/architecture/packages/01-system; frontend/src показан как frontend.",80,35,2070,110,23);
        int legendY=200+((index+3)/4)*440;
        note(d,"00-packages:legend","Последовательность 2 использует именно эти участники. frontend, api, security, service, application, domain, persistence, pollentree взяты из существующей системной диаграммы пакетов.\napplication агрегирует дочерние preparation, pollen, shared и port так же, как 01-system. fake и config исключены из взаимодействия. Нет выдуманных пакетов отдельных сервисов и отдельного участника port.\nPostgreSQL — база данных, Pollen Tree — внешний сервис; это добавленные участники, не Java-пакеты. Сообщения исполнения находятся на диаграмме 2, зависимости исходных пакетов — в оригинальном проекте packages.\nЦелевые методы/обвязка помечены «проект». Код приложения и исходный проект пакетов не изменены.",80,legendY,2070,190,18);
        d.modified();
    }
    private static Element append(Document doc,Element parent,String name,String...attrs){Element e=doc.createElement(name);parent.appendChild(e);for(int i=0;i<attrs.length;i+=2)e.setAttribute(attrs[i],attrs[i+1]);return e;}
    private static String rect(IShapeUIModel s){return s.getX()+","+s.getY()+","+s.getWidth()+","+s.getHeight();}
    private void inspect(File out)throws Exception{
        Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();Element r=doc.createElement("native-project");doc.appendChild(r);
        IProject project=app.getProjectManager().getProject();r.setAttribute("name",project.getName());
        for(IModelElement m:project.toAllLevelModelElementArray()){
            // Class operations are embedded members, absent from toAllLevelModelElementArray().
            if(m instanceof IClass)for(IOperation op:((IClass)m).toOperationArray())if(!id(op).isEmpty()){
                Element e=append(doc,r,"operation","id",id(op),"name",op.getName(),"owner",id(m),"returns",op.getReturnTypeAsString(),"documentation",op.getDocumentation());
                for(IParameter p:op.toParameterArray())append(doc,e,"parameter","name",p.getName(),"type",p.getTypeAsString());
            }
            if(m instanceof IInteraction&&!id(m).isEmpty()){IInteraction i=(IInteraction)m;append(doc,r,"interaction","id",id(m),"precondition",i.getPrecondition(),"postcondition",i.getPostcondition());}
        }
        for(IDiagramUIModel d:project.toDiagramArray()){
            if(d.getDocumentation()==null||!d.getDocumentation().startsWith("uc3:"))continue;
            String key=d.getDocumentation().substring(4);d.setZoomRatio(1.0);dm.openDiagram(d);
            Element de=append(doc,r,"diagram","id",key,"name",d.getName(),"type",d.getType());
            for(IDiagramElement item:d.toDiagramElementArray()){
                IModelElement m=item.getModelElement();if(m==null)continue;
                if(m instanceof IMessage){
                    IMessage msg=(IMessage)m;IConnectorUIModel c=(IConnectorUIModel)item;
                    Element e=append(doc,de,"message","id",id(m),"name",m.getName(),"type",m.getModelType(),"from",id(msg.getFrom()),"to",id(msg.getTo()),"fromShape",id(c.getFromShape().getModelElement()),"toShape",id(c.getToShape().getModelElement()),"parent",id(m.getParent()),"messageType",String.valueOf(msg.getType()),"asynchronous",String.valueOf(msg.isAsynchronous()),"returnMessage",id(msg.getReturnMessage()),"fromActivation",id(msg.getFromActivation()),"toActivation",id(msg.getToActivation()));
                    IModelElement action=msg.getActionType();e.setAttribute("actionType",action==null?"":action.getModelType());
                    e.setAttribute("operation",action instanceof IActionTypeCall?id(((IActionTypeCall)action).getOperation()):"");
                    StringJoiner pts=new StringJoiner(";");for(Point p:c.getPoints())pts.add(p.x+","+p.y);e.setAttribute("points",pts.toString());
                    ICaptionUIModel cap=c.getCaptionUIModel();e.setAttribute("caption",cap.getX()+","+cap.getY()+","+cap.getWidth()+","+cap.getHeight());e.setAttribute("captionSide",String.valueOf(cap.getSide()));
                }else if(item instanceof IShapeUIModel){
                    IShapeUIModel s=(IShapeUIModel)item;Element e=append(doc,de,"shape","id",id(m),"name",m.getName(),"type",m.getModelType(),"bounds",rect(s),"parent",id(m.getParent()),"shapeParent",s.getParent()==null?"":id(s.getParent().getModelElement()),"documentation",m.getDocumentation()==null?"":m.getDocumentation());
                    if(m instanceof IInteractionLifeLine){IInteractionLifeLine life=(IInteractionLifeLine)m;IModelElement classifier=life.getBaseClassifierAsModel();e.setAttribute("classifier",id(classifier));e.setAttribute("classifierType",classifier.getModelType());e.setAttribute("classifierPackage",id(classifier.getParent()));e.setAttribute("showClassifier",String.valueOf(((IInteractionLifeLineUIModel)s).isShowClassifier()));}
                    if(m instanceof IActivation)e.setAttribute("lifeline",id(m.getParent()));
                    if(m instanceof ICombinedFragment){ICombinedFragment f=(ICombinedFragment)m;e.setAttribute("operator",f.getInteractionOperator());e.setAttribute("covered",String.valueOf(f.coveredLifeLineCount()));}
                    if(m instanceof IInteractionOperand){IInteractionOperand o=(IInteractionOperand)m;e.setAttribute("guard",o.getGuard()==null?"":o.getGuard().getConstraint());StringJoiner ms=new StringJoiner(",");IMessage[] owned=o.toMessageArray();if(owned!=null)for(IMessage message:owned)ms.add(id(message));e.setAttribute("messages",ms.toString());}
                    if(m instanceof IInteractionOccurrence){IInteractionOccurrence ref=(IInteractionOccurrence)m;e.setAttribute("refersTo",id(ref.getRefersTo()));e.setAttribute("covered",String.valueOf(ref.coveredLifeLineCount()));}
                }
            }
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,key+".svg"),ModelConvertionManager.IMAGE_TYPE_SVG);
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,key+".png"),ModelConvertionManager.IMAGE_TYPE_PNG);
        }
        Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.INDENT,"yes");tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");tr.transform(new DOMSource(doc),new StreamResult(new File(out,"native-reopened.xml")));
    }
}
