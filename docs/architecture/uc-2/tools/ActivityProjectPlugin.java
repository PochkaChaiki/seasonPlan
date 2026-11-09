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
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Native documentation authoring only. Compile for VP's bundled Java 11. */
public class ActivityProjectPlugin implements VPPlugin, VPPluginCommandLineSupport {
    private final ApplicationManager app = ApplicationManager.instance();
    private final DiagramManager dm = app.getDiagramManager();
    private final IModelElementFactory factory = IModelElementFactory.instance();
    private final Map<String, IPackage> packages = new LinkedHashMap<>();
    private final Map<String, IClass> classes = new LinkedHashMap<>();
    private IPackage root, types;
    public void loaded(VPPluginInfo info) {}
    public void unloaded() {}
    public void invoke(String[] rawArgs) {
        String[] args = rawArgs.length == 1 ? rawArgs[0].split("\\|", -1) : rawArgs;
        try {
            if (args.length != 3) throw new IllegalArgumentException("mode, source XML, output directory required");
            File out = new File(args[2]); out.mkdirs();
            if (args[0].equals("generate")) {
                Document source = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(args[1]));
                edt(() -> generate(source, out));
            } else if (args[0].equals("inspect")) {
                edt(() -> inspect(out));
            } else throw new IllegalArgumentException("unknown mode");
            Files.writeString(new File(out,args[0]+"-success.txt").toPath(), "OK\n");
        } catch (Throwable error) {
            error.printStackTrace();
            try { Files.writeString(new File(args[2],"failure.txt").toPath(), error.toString()); } catch (Exception ignored) {}
            throw new RuntimeException(error);
        }
    }
    interface Work { void run() throws Exception; }
    private static void edt(Work work) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) { work.run(); return; }
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> { try { work.run(); } catch(Throwable e) { failure[0]=e; } });
        if (failure[0]!=null) throw new Exception(failure[0]);
    }
    private static java.util.List<Element> children(Element parent, String tag) {
        java.util.List<Element> result = new ArrayList<>();
        for (Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element && ((Element)n).getTagName().equals(tag)) result.add((Element)n);
        return result;
    }
    private static int number(Element e,String key) { return Integer.parseInt(e.getAttribute(key)); }
    private static void bounds(IShapeUIModel s, Element e) {
        s.setRequestDefaultSize(false); s.setRequestFitSize(false);
        s.setBounds(number(e,"x"),number(e,"y"),number(e,"w"),number(e,"h"));
    }
    private void style(IShapeUIModel shape, Color color, int font) {
        shape.getFillColor().setColor1(color);
        shape.getFillColor().setColor2(color);
        shape.getElementFont().setValues("Arial",Font.PLAIN,font,new Color(25,40,56));
        shape.setBackground(color); shape.setForeground(new Color(50,65,80));
        shape.setDisplayStereotypeIcon(false);
        shape.setRequestResetCaption(true);
    }
    private void setup(IDiagramUIModel d,String name) {
        d.setName(name); d.setZoomRatio(1.0); d.setAutoFitShapesSize(false);
        d.setAlignToGrid(false); d.setGridVisible(false); d.setRequestValidateSnapToGrid(false);
        d.setDiagramBackground(Color.WHITE); d.setShowStereotypes(true);
        d.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
    }
    private void tag(IModelElement model,String id,String text) {
        // Description and documentation alias the same persisted property in VP 18.1.
        model.setDocumentation("uc2:"+id+"\n"+text);
    }
    private IShapeUIModel note(IDiagramUIModel d,String text,int x,int y,int w,int h,int font) {
        IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_NOTE);
        IModelElement m=s.getModelElement();
        if(m==null) throw new IllegalStateException("unregistered note");
        m.setName(text); m.setDocumentation(text);
        style(s,new Color(255,251,231),font); s.setBounds(x,y,w,h); s.resetCaption();
        s.setBounds(x,y,w,h); return s;
    }
    private void generate(Document source,File out) throws Exception {
        ProjectManager pm=app.getProjectManager();
        // Only a disposable copy is loaded. VP may also rewrite the loaded input on save-as.
        if(!pm.saveProject() || !pm.newProject()) throw new IllegalStateException("new project failed");
        pm.getProject().setName("SeasonPlan UC-2 — UML 2 activity views");
        root=factory.createPackage(); root.setName("UC-2 — Согласовать сезонный план");
        types=factory.createPackage(); types.setName("Implementation type projections — design, not runtime"); root.addChild(types);
        Element project=source.getDocumentElement();
        Element packageRoot=children(project,"packages").get(0);
        for(Element e:children(packageRoot,"package")) {
            IPackage p=factory.createPackage();p.setName(e.getAttribute("name"));
            tag(p,"package-"+e.getAttribute("id"),e.getTextContent());root.addChild(p);packages.put(e.getAttribute("id"),p);
        }
        for(Element spec:children(project,"diagram")) activity(spec);
        packageDiagram(packageRoot);
        if(!pm.saveProjectAs(new File(out,"SeasonPlan-UC2-activities.vpp"))) throw new IllegalStateException("save failed");
    }
    private void activity(Element spec) {
        String key=spec.getAttribute("id");
        IActivityDiagramUIModel d=(IActivityDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ACTIVITY_DIAGRAM);
        setup(d,spec.getAttribute("name")); d.setShowDiagramFrame(false);
        d.setShowActionCallBehaviorOption(IActivityDiagramUIModel.ALWAYS_HIDE);
        d.setObjectNodeShowType(false);
        d.setControlFlowDisplayOption(IActivityDiagramUIModel.CONTROL_FLOW_DISPLAY_OPTION_SOLID_LINE);
        d.setDecisionMergeNodeConnectionPointStyle(IActivityDiagramUIModel.DECISION_MERGE_NODE_CONNECTION_POINT_STYLE_CONNECT_TO_VERTEX);
        IActivity activity=factory.createActivity(); activity.setName("UC-2 — "+key);
        activity.setPrecondition("Главный хранитель авторизован; план SUBMITTED; корректность проверена."+(key.equals("01-use-case")?"":" Повторный обзор VERIFIED допустим."));
        activity.setPostcondition("APPROVED с условиями либо DRAFT с замечаниями; решение и аудит атомарны; уведомление после commit. Ошибка не меняет статус.");
        activity.setSingleExecution(true); activity.setActivityDiagramId(d.getId()); root.addChild(activity);
        d.setDefaultPackage(activity);
        IActivitySwimlane2 swim=factory.createActivitySwimlane2(); activity.addChild(swim);
        swim.setName("Ответственность участников и пакетов");
        IActivitySwimlane2NewUIModel swimShape=(IActivitySwimlane2NewUIModel)dm.createDiagramElement(d,swim);
        int height=number(spec,"height"),width=number(spec,"width")-60;
        swimShape.setBounds(60,140,width,height-140);
        Map<String,IActivityPartition> partitions=new LinkedHashMap<>();
        Map<String,IActivitySwimlane2CompartmentUIModel> cells=new LinkedHashMap<>();
        java.util.List<String> headers=new ArrayList<>(), compartments=new ArrayList<>();
        Color[] fills={new Color(241,247,255),new Color(242,248,248),new Color(242,249,241),new Color(250,247,239),new Color(248,243,251)};
        int index=0;
        for(Element lane:children(spec,"lane")) {
            String id=lane.getAttribute("id");
            IActivityPartition p=factory.createActivityPartition(); p.setName(lane.getAttribute("name")); tag(p,id,"UML 2 ActivityPartition");
            swim.addVerticalPartition(p); swim.addChild(p);
            if(packages.containsKey(id))p.setRepresents(packages.get(id));
            IActivityPartitionHeaderUIModel header=(IActivityPartitionHeaderUIModel)dm.createDiagramElement(d,p);
            header.setHorizontal(false);header.setSwimlane(swimShape);swimShape.addChild(header);
            header.setBounds(number(lane,"x"),140,number(lane,"width"),60);style(header,fills[index],18);
            IActivitySwimlane2CompartmentUIModel cell=(IActivitySwimlane2CompartmentUIModel)dm.createDiagramElement(d,IShapeTypeConstants.SHAPE_TYPE_ACTIVITY_SWIMLANE2_COMPARTMENT);
            cell.setVerticalPartitionId(header.getId());swimShape.addChild(cell);
            cell.setBounds(number(lane,"x"),200,number(lane,"width"),height-200);style(cell,Color.WHITE,14);
            headers.add(header.getId());compartments.add(cell.getId());partitions.put(id,p);cells.put(id,cell);index++;
        }
        swimShape.setVerticalPartitionIds(headers.toArray(new String[0]));
        swimShape.setHorizontalPartitionIds(new String[0]);
        swimShape.setCompartmentIds(compartments.toArray(new String[0]));
        Map<String,IModelElement> models=new LinkedHashMap<>();Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();
        for(Element n:children(spec,"node")) {
            String kind=n.getAttribute("kind"),id=n.getAttribute("id"),text=n.getTextContent();
            IModelElement m;
            switch(kind) {
                case "initial":m=factory.createInitialNode();break;
                case "final":m=factory.createActivityFinalNode();break;
                case "decision":m=factory.createDecisionNode();break;
                case "merge":m=factory.createMergeNode();break;
                case "action":m=factory.createActivityAction();break;
                case "fork":m=factory.createForkNode();break;
                case "join":m=factory.createJoinNode();break;
                case "handler":m=factory.createStructuredActivityNode();break;
                case "object":
                    IObjectNode object=factory.createObjectNode();
                    object.setType(classifier(n.getAttribute("type")));m=object;break;
                default:throw new IllegalArgumentException(kind);
            }
            if(!text.isEmpty())m.setName(text);
            tag(m,id,text);swim.addChild(m);partitions.get(n.getAttribute("lane")).addContainedElement(m);
            if(key.equals("03-implementation") && m instanceof IActivityAction) bindCall(id,(IActivityAction)m);
            IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,m);
            cells.get(n.getAttribute("lane")).addChild(s);
            Color color=kind.equals("action")?(text.contains("«проект»")||text.contains("«изменить»")?new Color(255,242,213):new Color(230,241,252)):Color.WHITE;
            if(kind.equals("initial") || kind.equals("final") || kind.equals("fork") || kind.equals("join"))color=Color.BLACK;
            style(s,color, key.equals("03-implementation")?14:15);bounds(s,n);
            if(!kind.equals("action") && !kind.equals("object") && !kind.equals("handler"))m.setName("");
            if(kind.equals("handler"))s.setModelElementNameAlignment(IShapeUIModel.MODEL_ELEMENT_NAME_ALIGNMENT_ALIGN_TOP_LEFT);
            s.resetCaption();bounds(s,n);
            if(!kind.equals("action") && !kind.equals("object") && !kind.equals("handler"))s.getCaptionUIModel().setVisible(false);
            models.put(id,m);shapes.put(id,s);
        }
        for(Element n:children(spec,"node"))if(n.hasAttribute("parent")) {
            models.get(n.getAttribute("parent")).addChild(models.get(n.getAttribute("id")));
            shapes.get(n.getAttribute("parent")).addChild(shapes.get(n.getAttribute("id")));
        }
        dm.openDiagram(d);
        // Materialization may resize shapes: restore all final bounds before adding connectors.
        for(Element n:children(spec,"node"))bounds(shapes.get(n.getAttribute("id")),n);
        java.util.List<Rectangle> labelBounds=new ArrayList<>();
        Map<String,IShapeUIModel> exceptionInputs=new HashMap<>();
        java.util.List<Element> edgeSpecs=children(spec,"edge");
        edgeSpecs.sort(Comparator.comparingDouble(e -> {
            if(e.getAttribute("guard").isEmpty())return Double.MAX_VALUE;
            Point[] ps=parsePoints(e.getAttribute("points"));double length=0;
            for(int j=0;j<ps.length-1;j++)length+=ps[j].distance(ps[j+1]);return length;
        }));
        for(Element edge:edgeSpecs) {
            String from=edge.getAttribute("from"),to=edge.getAttribute("to");
            Point[] points=parsePoints(edge.getAttribute("points"));
            IModelElement sourceModel=models.get(from);IShapeUIModel sourceShape=shapes.get(from);
            IModelElement targetModel=models.get(to);IShapeUIModel targetShape=shapes.get(to);
            IHasRelationshipBaseSimpleRelationship f;
            if(edge.getAttribute("kind").equals("object")) {
                IOutputPin output=((IActivityAction)sourceModel).createOutputPin();
                output.setName("");output.setType(((IObjectNode)models.get(to)).getTypeAsModel());
                tag(output,"pin-"+from,"UML 2 typed output pin");
                IShapeUIModel pinShape=(IShapeUIModel)dm.createDiagramElement(d,output);sourceShape.addChild(pinShape);
                Point p=points[0];int px=p.x-5,py=p.y-5;
                if(p.x==sourceShape.getX())px=p.x;
                if(p.x==sourceShape.getX()+sourceShape.getWidth())px=p.x-10;
                if(p.y==sourceShape.getY())py=p.y;
                if(p.y==sourceShape.getY()+sourceShape.getHeight())py=p.y-10;
                style(pinShape,Color.WHITE,12);pinShape.setBounds(px,py,10,10);
                pinShape.getCaptionUIModel().setVisible(false);
                sourceModel=output;sourceShape=pinShape;
                f=factory.createActivityObjectFlow();
            } else if(edge.getAttribute("kind").equals("exception")) {
                IShapeUIModel inputShape=exceptionInputs.get(to);
                if(inputShape==null) {
                    IInputPin input=factory.createInputPin();input.setName("");input.setType(classifier("Exception"));
                    ((IStructuredActivityNode)targetModel).addInputPin(input);targetModel.addChild(input);
                    tag(input,"pin-"+to,"ExceptionHandler.exceptionInput; handler body owns this typed input");
                    inputShape=(IShapeUIModel)dm.createDiagramElement(d,input);targetShape.addChild(inputShape);
                    Point p=points[points.length-1];style(inputShape,Color.WHITE,12);inputShape.setBounds(p.x,p.y-5,10,10);
                    inputShape.getCaptionUIModel().setVisible(false);exceptionInputs.put(to,inputShape);
                }
                targetShape=inputShape;targetModel=inputShape.getModelElement();
                IExceptionHandler handler=factory.createExceptionHandler();handler.addExceptionType(classifier("Exception"));f=handler;
            } else {
                IControlFlow control=factory.createControlFlow();control.setGuard(edge.getAttribute("guard"));f=control;
            }
            IModelElement owner=models.get(from).getParent();
            if(owner instanceof IStructuredActivityNode && owner==models.get(to).getParent())owner.addChild(f);
            else swim.addChild(f);
            f.setFrom(sourceModel);f.setTo(targetModel);
            tag(f,edge.getAttribute("id"),"UML 2 "+edge.getAttribute("kind")+" flow; "+from+" → "+to);
            IConnectorUIModel line=(IConnectorUIModel)dm.createConnector(d,f,sourceShape,targetShape,points);
            line.setFromShape(sourceShape);line.setToShape(targetShape);
            line.setConnectorStyle(edge.getAttribute("kind").equals("exception")?IConnectorUIModel.CS_OBLIQUE:IConnectorUIModel.CS_RECTI_LINEAR);line.setBackground(Color.WHITE);
            line.getElementFont().setValues("Arial",Font.PLAIN,13,new Color(30,40,55));
            pin(line,sourceShape,points[0],true);pin(line,targetShape,points[points.length-1],false);
            line.clearPoints();for(Point point:points)line.addPoint(point);line.resetCaption();
            ICaptionUIModel cap=line.getCaptionUIModel();
            cap.setSide(ICaptionUIModel.SIDE_NONE);
            if(!edge.getAttribute("guard").isEmpty()) {
                Rectangle rect=captionBounds(edge.getAttribute("guard"),points,shapes.values(),labelBounds,number(spec,"width"),height);
                cap.setBounds(rect.x,rect.y,rect.width,rect.height);cap.setVisible(true);labelBounds.add(rect);
            } else cap.setVisible(false);
        }
        note(d,"UC-2 · Согласовать сезонный план\n"+spec.getAttribute("name"),60,20,Math.min(width,1800),100,20);
        for(Element n:children(spec,"note"))note(d,n.getTextContent(),number(n,"x"),number(n,"y"),number(n,"w"),number(n,"h"),15);
        d.modified();
    }
    private IClass classifier(String name) {
        IClass c=classes.get(name);
        if(c==null){c=factory.createClass();c.setName(name);types.addChild(c);classes.put(name,c);}return c;
    }
    private Rectangle captionBounds(String text,Point[]points,Collection<IShapeUIModel>shapes,java.util.List<Rectangle>labels,int width,int height) {
        int w=Math.min(460,Math.max(140,text.length()*7+48)),h=text.length()>48?48:36;
        Rectangle best=null;double bestScore=Double.MAX_VALUE;
        for(int j=0;j<points.length-1;j++) {
            Point a=points[j],b=points[j+1];int cx=(a.x+b.x)/2,cy=(a.y+b.y)/2;
            int[][] candidates={{cx-w/2,cy-h-8},{cx-w/2,cy+8},{cx+12,cy-h/2},{cx-w-12,cy-h/2},
                {a.x+20,a.y-h-12},{a.x-w-20,a.y+12},{b.x+12,b.y-h-12},{b.x-w-12,b.y+12}};
            java.util.List<int[]> candidatesList=new ArrayList<>(Arrays.asList(candidates));
            for(int dx=-280;dx<=280;dx+=40)for(int dy=-160;dy<=160;dy+=40)
                candidatesList.add(new int[]{cx-w/2+dx,cy-h/2+dy});
            for(int[]p:candidatesList) {
                Rectangle r=new Rectangle(Math.max(35,Math.min(width-w,p[0])),Math.max(215,Math.min(height-h,p[1])),w,h);
                double dx=Math.max(0,Math.max(Math.min(a.x,b.x)-r.getMaxX(),r.getMinX()-Math.max(a.x,b.x)));
                double dy=Math.max(0,Math.max(Math.min(a.y,b.y)-r.getMaxY(),r.getMinY()-Math.max(a.y,b.y)));
                if(Math.hypot(dx,dy)>22)continue; // A guard must visually belong to its own route.
                if(a.y==b.y && (r.getCenterX()<Math.min(a.x,b.x)-10 || r.getCenterX()>Math.max(a.x,b.x)+10))continue;
                if(a.x==b.x && (r.getCenterY()<Math.min(a.y,b.y)-10 || r.getCenterY()>Math.max(a.y,b.y)+10))continue;
                // The rejected-reservation route initially shares a stem with CONFIRMED.
                // Keep its entire caption on the later, distinguishing segment.
                if(text.startsWith("REJECTED") && a.y==b.y && (r.getMinX()<Math.min(a.x,b.x)+8 || r.getMaxX()>Math.max(a.x,b.x)-8))continue;
                if(text.startsWith("REJECTED") && a.x==b.x && (r.getMinY()<Math.min(a.y,b.y)+4 || r.getMaxY()>Math.max(a.y,b.y)-4))continue;
                int collisions=0;
                for(IShapeUIModel shape:shapes){Rectangle obstacle=new Rectangle(shape.getX()-6,shape.getY()-6,shape.getWidth()+12,shape.getHeight()+12);if(r.intersects(obstacle))collisions++;}
                for(Rectangle label:labels)if(r.intersects(label))collisions++;
                double score=collisions*100000+Math.hypot(r.getCenterX()-points[0].x,r.getCenterY()-points[0].y)+j*15;
                if(score<bestScore){best=r;bestScore=score;}
            }
        }
        if(bestScore>=100000)throw new IllegalStateException("No clear guard label placement: "+text);
        return best;
    }
    private IOperation operation(String owner,String name,String result,String... params) {
        IClass c=classes.get(owner);
        if(c==null) {c=factory.createClass();c.setName(owner);types.addChild(c);classes.put(owner,c);}
        for(IOperation o:c.toOperationArray())if(o.getName().equals(name))return o;
        IOperation op=c.createOperation();op.setName(name);op.setReturnType(result);op.setVisibility(IOperation.VISIBILITY_PUBLIC);
        for(String declaration:params) {
            int split=declaration.lastIndexOf(' ');IParameter p=op.createParameter();
            p.setName(declaration.substring(split+1));p.setType(declaration.substring(0,split));p.setDirection(IParameter.DIRECTION_IN);
        }
        return op;
    }
    private void bindCall(String id,IActivityAction action) {
        IOperation op=null;
        switch(id) {
            case "load":op=operation("SeasonPlanService","get","SeasonPlan","UUID id");break;
            case "history":op=operation("AuditLogService","entriesFor","List<AuditLogEntry>","UUID seasonPlanId");break;
            case "verify":op=operation("ApprovalService","verify","SeasonPlan","UUID id","ActorRole role","String actorId");op.setDocumentation("PROPOSED; absent in current source. Atomic SUBMITTED → VERIFIED, audit and save.");break;
            case "pollen-read":op=operation("RestPollenTreeClient","findLatestBySeasonPlanId","Optional<PollenReservation>","UUID seasonPlanId");break;
            case "pollen-service":op=operation("PollenService","findLatestBySeasonPlanId","Optional<PollenReservation>","UUID planId");break;
            case "pollen-port":op=operation("PollenTreeClient","findLatestBySeasonPlanId","Optional<PollenReservation>","UUID seasonPlanId");break;
            case "load-service":op=operation("PlanningService","get","SeasonPlan","UUID id");break;
            case "load-repo":op=operation("SeasonPlanRepository","findById","Optional<SeasonPlan>","UUID id");break;
            case "load-jdbc":op=operation("JdbcSeasonPlanRepository","findById","Optional<SeasonPlan>","UUID id");break;
            case "approval-tx":case "return-tx":op=operation("CommandTransaction","execute","T","UUID seasonPlanId","Supplier<T> command");break;
            case "approve":op=operation("ApprovalService","approve","SeasonPlan","UUID id","String comment","List<ApprovalCondition> conditions","ActorRole role","String actorId");op.setDocumentation("PROPOSED OVERLOAD. Current 4-argument method lacks conditions and requires SUBMITTED; target requires VERIFIED.");
                operation("SeasonPlan","setApprovalConditions","void","List<ApprovalCondition> conditions").setDocumentation("PROPOSED immutable approval-condition snapshot");break;
            case "return":op=operation("ApprovalService","returnForRevision","SeasonPlan","UUID id","String comment","ActorRole role","String actorId");op.setDocumentation("EXISTING SIGNATURE; target also accepts VERIFIED, unlike current source.");break;
            case "audit":op=operation("AuditLogService","append","AuditLogEntry","UUID seasonPlanId","AuditAction action","String actorId","String comment");break;
            case "save":op=operation("JdbcSeasonPlanRepository","save","SeasonPlan","SeasonPlan plan");break;
            case "outbox":op=operation("NotificationOutbox","enqueue","void","NotificationOutbox.Event event");break;
            // Rollback is TransactionTemplate's error cleanup, not another execute() command.
        }
        if(op!=null) {
            ICallOperationAction call=factory.createCallOperationAction();call.setOperation(op);call.setSynchronous(true);
            action.setActionType(call);
        }
        // Secondary calls remain explicitly visible in each action; projections are not a new runtime.
        action.setEffect(action.getName());
    }
    private static Point[] parsePoints(String encoded) {
        String[] tokens=encoded.split(";");Point[] points=new Point[tokens.length];
        for(int i=0;i<tokens.length;i++){String[] xy=tokens[i].split(",");points[i]=new Point(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));}
        return points;
    }
    private void pin(IConnectorUIModel line,IShapeUIModel shape,Point p,boolean from) {
        int dx=p.x-shape.getX(),dy=p.y-shape.getY();
        Point2D ratio=new Point2D.Double((double)dx/shape.getWidth(),(double)dy/shape.getHeight());
        if(from){line.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);line.setFromPinRatio(ratio);line.setFromShapeXDiff(dx);line.setFromShapeYDiff(dy);line.setUseFromShapeCenter(false);}
        else {line.setToPinType(IConnectorUIModel.PINTYPE_RATIO);line.setToPinRatio(ratio);line.setToShapeXDiff(dx);line.setToShapeYDiff(dy);line.setUseToShapeCenter(false);}
    }
    private void packageDiagram(Element source) {
        IPackageDiagramUIModel d=(IPackageDiagramUIModel)dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM);
        setup(d,"0. UC-2 — Пакеты и уровни логического представления");d.setDefaultPackage(root);d.setShowDiagramFrame(false);
        String[] ids={"client","boundary","application","core","adapters"};
        int[][] positions={{60,180},{500,180},{940,180},{940,650},{500,650}};
        Map<String,IShapeUIModel> shapes=new LinkedHashMap<>();
        for(int i=0;i<ids.length;i++){
            IPackage p=packages.get(ids[i]);IShapeUIModel s=(IShapeUIModel)dm.createDiagramElement(d,p);
            style(s,new Color(234,243,251),18);s.setBounds(positions[i][0],positions[i][1],360,330);s.resetCaption();
            shapes.put(ids[i],s);
            String path=p.getDocumentation().substring(p.getDocumentation().indexOf('\n')+1);
            note(d,path,positions[i][0]+20,positions[i][1]+75,315,210,16);
        }
        dm.openDiagram(d);
        for(int i=0;i<ids.length;i++)shapes.get(ids[i]).setBounds(positions[i][0],positions[i][1],360,330);
        dependency(d,shapes,"client","boundary",new Point[]{new Point(420,330),new Point(500,330)});
        dependency(d,shapes,"boundary","application",new Point[]{new Point(860,330),new Point(940,330)});
        dependency(d,shapes,"application","core",new Point[]{new Point(1120,510),new Point(1120,650)});
        dependency(d,shapes,"adapters","core",new Point[]{new Point(860,800),new Point(940,800)});
        dependency(d,shapes,"adapters","application",new Point[]{new Point(860,700),new Point(900,700),new Point(900,430),new Point(940,430)});
        note(d,"UC-2 · Диаграмма пакетов — опора логической activity\nПять пакетов представляют пять одноимённых ActivityPartition логического вида.",60,20,1250,110,20);
        note(d,"Пути Java сокращены относительно com.seasonplan.lifecycle, кроме security.\nГруппировка — архитектурные уровни, а не новые Maven-модули.\nЗависимости кода: адаптеры → приложение → домен; приложение использует порты, не concrete adapters.\nВо время выполнения порт вызывает реализацию через DI; это НЕ обратная зависимость исходного кода.\nPollenService — единственная прикладная граница Древа; внешнее Древо не является нашим пакетом.\nUI/API истории, verify, условия, UC-2 transaction/outbox delivery требуют реализации (см. README).",60,1060,1250,230,16);
        d.modified();
    }
    private void dependency(IDiagramUIModel d,Map<String,IShapeUIModel>s,String a,String b,Point[]points) {
        IDependency dep=factory.createDependency();dep.setFrom(packages.get(a));dep.setTo(packages.get(b));
        IConnectorUIModel c=(IConnectorUIModel)dm.createConnector(d,dep,s.get(a),s.get(b),points);
        c.setFromShape(s.get(a));c.setToShape(s.get(b));pin(c,s.get(a),points[0],true);pin(c,s.get(b),points[points.length-1],false);
        c.clearPoints();for(Point point:points)c.addPoint(point);c.getCaptionUIModel().setVisible(false);
    }
    private static Element append(Document doc,Element parent,String tag,String...attrs) {
        Element e=doc.createElement(tag);parent.appendChild(e);
        for(int i=0;i<attrs.length;i+=2)e.setAttribute(attrs[i],attrs[i+1]);return e;
    }
    private static String ident(IModelElement m) {
        if(m==null)return "";String desc=m.getDocumentation();
        if(desc==null||!desc.startsWith("uc2:"))return "";
        String id=desc.substring(4).split("\n",2)[0];
        return id.startsWith("pin-")?id.substring(4):id;
    }
    private void inspect(File out) throws Exception {
        Document report=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();Element r=report.createElement("native-project");report.appendChild(r);
        r.setAttribute("name",app.getProjectManager().getProject().getName());
        for(IDiagramUIModel d:app.getProjectManager().getProject().toDiagramArray()) {
            d.setZoomRatio(1.0);dm.openDiagram(d);
            Element de=append(report,r,"diagram","name",d.getName(),"type",d.getType());
            for(IDiagramElement item:d.toDiagramElementArray()) {
                IModelElement m=item.getModelElement();if(m==null)continue;
                if(item instanceof IConnectorUIModel) {
                    IConnectorUIModel c=(IConnectorUIModel)item;
                    ISimpleRelationship relation=(ISimpleRelationship)m;
                    String guard=m instanceof IControlFlow?((IControlFlow)m).getGuard():"";
                    Element e=append(report,de,"edge","id",ident(m),"type",m.getModelType(),"from",ident(relation.getFrom()),"to",ident(relation.getTo()),"guard",guard==null?"":guard,
                        "fromShape",ident(c.getFromShape().getModelElement()),"toShape",ident(c.getToShape().getModelElement()),"captionSide",String.valueOf(c.getCaptionUIModel().getSide()),"nativeFromType",relation.getFrom().getModelType(),"nativeToType",relation.getTo().getModelType());
                    if(m instanceof IExceptionHandler)e.setAttribute("exceptionTypes",String.valueOf(((IExceptionHandler)m).exceptionTypeCount()));
                    StringJoiner points=new StringJoiner(";");for(Point p:c.getPoints())points.add(p.x+","+p.y);e.setAttribute("points",points.toString());
                    ICaptionUIModel cap=c.getCaptionUIModel();e.setAttribute("caption",cap.getX()+","+cap.getY()+","+cap.getWidth()+","+cap.getHeight());
                } else if(item instanceof IShapeUIModel) {
                    IShapeUIModel s=(IShapeUIModel)item;
                    Element e=append(report,de,"shape","id",m instanceof IOutputPin || m instanceof IInputPin?"pin-"+ident(m):ident(m),"type",m.getModelType(),"modelId",m.getId(),"name",m.getName()==null?"":m.getName(),"bounds",s.getX()+","+s.getY()+","+s.getWidth()+","+s.getHeight(),"fill",String.valueOf(s.getFillColor().getColor1().getRGB()));
                    e.setAttribute("parent",ident(m.getParent()));
                    if(m instanceof IObjectNode)e.setAttribute("typeRef",((IObjectNode)m).getTypeAsText());
                    if(m instanceof IActivityPartition) {
                        IActivityPartition p=(IActivityPartition)m;StringJoiner contained=new StringJoiner(",");
                        for(IModelElement n:p.toContainedElementArray())contained.add(ident(n));e.setAttribute("contains",contained.toString());
                        e.setAttribute("represents",p.getRepresents()==null?"":ident(p.getRepresents()));
                    }
                    if(m instanceof IActivityAction && ((IActivityAction)m).getActionType() instanceof ICallOperationAction) {
                        ICallOperationAction call=(ICallOperationAction)((IActivityAction)m).getActionType();
                        e.setAttribute("operation",call.getOperation().getParent().getName()+"."+call.getOperation().getName());
                        e.setAttribute("returnType",call.getOperation().getReturnTypeAsText());
                        StringJoiner parameters=new StringJoiner("|");
                        for(IParameter parameter:call.getOperation().toParameterArray())parameters.add(parameter.getTypeAsText()+" "+parameter.getName());
                        e.setAttribute("parameters",parameters.toString());
                    }
                }
            }
            String filename=d.getName().startsWith("0.")?"00-packages":d.getName().startsWith("1.")?"01-use-case":d.getName().startsWith("2.")?"02-logical":"03-implementation";
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,filename+".svg"),ModelConvertionManager.IMAGE_TYPE_SVG);
            app.getModelConvertionManager().exportDiagramAsImage(d,new File(out,filename+".png"),ModelConvertionManager.IMAGE_TYPE_PNG);
        }
        Transformer tr=TransformerFactory.newInstance().newTransformer();tr.setOutputProperty(OutputKeys.INDENT,"yes");tr.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        tr.transform(new DOMSource(report),new StreamResult(new File(out,"native-reopened.xml")));
    }
}
