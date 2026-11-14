"""Derive a bounded VP repair plugin from the tested UC-2 authoring template."""
from pathlib import Path


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('VP template changed: ' + old[:90])
    return text.replace(old, new, 1)


def generate(template, destination):
    text = template.read_text(encoding='utf-8')
    text = replace_once(text, 'public class ActivityProjectPlugin', 'public class ImplementationRevisionPlugin')
    text = replace_once(text, 'edt(() -> generate(source, out));', 'edt(() -> generate(source, out));\n                edt(() -> { if(!app.getProjectManager().saveProject())throw new IllegalStateException("final save failed"); });')
    start = text.index('    private void generate(Document source,File out)')
    end = text.index('    private void activity(Element spec)', start)
    text = text[:start] + '''    private void generate(Document source,File out) throws Exception {
        ProjectManager pm=app.getProjectManager();
        // Only the loaded scratch copy is modified. Other diagram/model identities survive.
        IDiagramUIModel target=null;
        for(IDiagramUIModel old:pm.getProject().toDiagramArray())if(old.getName().startsWith("3.")) {
            if(target!=null)throw new IllegalStateException("ambiguous implementation diagram");target=old;
        }
        if(!(target instanceof IActivityDiagramUIModel))throw new IllegalStateException("implementation activity not found");
        IModelElement priorActivity=((IActivityDiagramUIModel)target).getDefaultPackage();target.delete();
        if(priorActivity instanceof IActivity)priorActivity.delete();
        root=factory.createPackage();root.setName("UC2Implementation");
        types=factory.createPackage();types.setName("RuntimeTypes");root.addChild(types);
        for(Element spec:children(source.getDocumentElement(),"diagram"))activity(spec);
        if(!pm.saveProjectAs(new File(out,"SeasonPlan-UC2-implementation.vpp")))throw new IllegalStateException("save failed");
    }
''' + text[end:]
    text = replace_once(text, 'if(key.equals("03-implementation") && m instanceof IActivityAction) bindCall(id,(IActivityAction)m);', 'if(key.equals("03-implementation") && m instanceof IActivityAction) bindRuntimeCall(n,(IActivityAction)m);')
    start = text.index('    private void bindCall(')
    end = text.index('    private static Point[] parsePoints', start)
    text = text[:start] + '''    private void bindRuntimeCall(Element node,IActivityAction action) {
        if(node.hasAttribute("callOwner")) {
            String encoded=node.getAttribute("callParameters");
            String[] parameters=encoded.isEmpty()?new String[0]:encoded.split("\\\\|",-1);
            IOperation op=operation(node.getAttribute("callOwner"),node.getAttribute("callName"),node.getAttribute("callReturn"),parameters);
            String evidence=node.getAttribute("callSource");
            if(evidence.isEmpty())evidence=node.getAttribute("callLibrary");
            op.setDocumentation(evidence);
            op.setAbstract(node.getAttribute("callInterface").equals("true"));
            op.setStatic(node.getAttribute("callStatic").equals("true"));
            if(node.getAttribute("callInterface").equals("true") && !op.getParent().hasStereotype("Interface"))op.getParent().addStereotype("Interface");
            if(node.getAttribute("callVisibility").equals("package"))op.setVisibility(IOperation.VISIBILITY_PACKAGE);
            if(node.getAttribute("callVisibility").equals("private"))op.setVisibility(IOperation.VISIBILITY_PRIVATE);
            ICallOperationAction call=factory.createCallOperationAction();call.setOperation(op);call.setSynchronous(true);
            action.setActionType(call);
        }
        action.setEffect(action.getName());
    }
''' + text[end:]
    start = text.index('        activity.setPrecondition(')
    end = text.index('        activity.setSingleExecution', start)
    text = text[:start] + '''        activity.setName("UC2ApprovalWorkflow");
        activity.setPrecondition("Authenticated CHIEF_GUARDIAN; plan.status() == SUBMITTED || plan.status() == VERIFIED");
        activity.setPostcondition("APPROVED or DRAFT; atomic plan, audit and outbox commit; delivery does not block HTTP response");
''' + text[end:]
    text = replace_once(text, 'swim.setName("Ответственность участников и пакетов");', 'swim.setName("RuntimeParticipants");')
    start = text.index('        note(d,"UC-2 · Согласовать сезонный план')
    end = text.index('        d.modified();', start)
    text = text[:start] + '''        for(IDiagramElement item:d.toDiagramElementArray())if(item instanceof IConnectorUIModel) {
            IConnectorUIModel c=(IConnectorUIModel)item;
            c.setRequestRebuild(true);c.setRequestRebuildFromEnd(true);c.setRequestRebuildToEnd(true);
        }
''' + text[end:]
    text = replace_once(text, 'Element de=append(report,r,"diagram","name",d.getName(),"type",d.getType());', 'Element de=append(report,r,"diagram","id",d.getId(),"name",d.getName(),"type",d.getType());')
    text = replace_once(text, 'e.setAttribute("parent",ident(m.getParent()));', 'e.setAttribute("shapeId",s.getId());e.setAttribute("parent",ident(m.getParent()));')
    text = replace_once(text, 'if(m instanceof IExceptionHandler)e.setAttribute("exceptionTypes",', 'e.setAttribute("modelId",m.getId());e.setAttribute("shapeId",c.getId());\n                    if(m instanceof IExceptionHandler)e.setAttribute("exceptionTypes",')
    text = replace_once(text, 'swimShape.setBounds(60,140,width,height-140);', 'swimShape.setBounds(60,140,width,height-140);swimShape.setCoverConnector(false);swimShape.sendToBack();')
    text = replace_once(text, 'cell.setVerticalPartitionId(header.getId());', 'cell.setCoverConnector(false);cell.setVerticalPartitionId(header.getId());')
    text = replace_once(text, 'if(kind.equals("handler"))s.setModelElementNameAlignment(IShapeUIModel.MODEL_ELEMENT_NAME_ALIGNMENT_ALIGN_TOP_LEFT);', 'if(kind.equals("handler")){s.setCoverConnector(false);s.setModelElementNameAlignment(IShapeUIModel.MODEL_ELEMENT_NAME_ALIGNMENT_ALIGN_TOP_LEFT);}')
    text = replace_once(text, 'line.setBackground(Color.WHITE);', 'Color ink=new Color(30,40,55);line.setForeground(ink);line.setBackground(ink);line.getLineModel().setColor(ink);line.getLineModel().setWeight(1.5f);line.getLineModel().setTransparency(0);line.getLineModel().setLineStyle(com.vp.plugin.diagram.format.LineStyle.Style1);line.getLineModel().applySetting();')
    text = replace_once(text, 'e.setAttribute("modelId",m.getId());e.setAttribute("shapeId",c.getId());', 'e.setAttribute("modelId",m.getId());e.setAttribute("shapeId",c.getId());e.setAttribute("lineColor",c.getLineModel().getColor()==null?"inherit":String.valueOf(c.getLineModel().getColor().getRGB()));e.setAttribute("lineWeight",String.valueOf(c.getLineModel().getWeight()));e.setAttribute("lineTransparency",String.valueOf(c.getLineModel().getTransparency()));e.setAttribute("lineStyle",String.valueOf(c.getLineModel().getLineStyle()));e.setAttribute("lineBounds",c.getX()+","+c.getY()+","+c.getWidth()+","+c.getHeight());')
    destination.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    import sys
    generate(Path(sys.argv[1]), Path(sys.argv[2]))
