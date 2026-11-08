# Agent handoff: UML diagrams and editable Visual Paradigm projects

This guide records the working approach and failures discovered while building the SeasonPlan UC-4 diagrams. It is intended to be passed to agents doing later diagramming tasks **without the conversation history**.

**Validated environment:** Visual Paradigm **18.1**, Windows 10, its bundled **Temurin Java 11.0.16.1** runtime, Java Open API. Version-specific observations below must be rechecked for another VP version.

## 1. Non-negotiable acceptance criteria

A diagram is not finished just because its export looks plausible.

- Deliver a genuine native **`.vpp` project**, with UML model elements and diagram elements. A renamed XML file, imported picture or embedded SVG is not an editable UML project.
- Respect the requested use-case boundary. Read requirements, architecture decisions, source and relevant tests; do not invent implemented services or adapters.
- Keep abstractions distinct. For the three-view convention requested in this project:

  | View | Required detail |
  | --- | --- |
  | Use-case / «Взаимодействие сущностей» | Entity names, interactions, relationship kinds and cardinalities; **no attributes or methods**. |
  | Logical / «Основные классы и интерфейсы» | Main classes/interfaces and collaborations; **no attributes or methods**. |
  | Implementation / «Полное описание классов» | Relevant typed fields, visibility, constructors, methods, parameters, return types and relationship kinds. |

  This is the user's convention for these deliverables, **not a universal restriction of UML**. Follow a later user's explicit requirements if different.
- In the first two views, the requested models themselves have zero attributes/operations, not merely hidden compartments. Use separate abstraction packages/models rather than reusing populated implementation models.
- Relationships must connect the intended model elements **and** their diagram shapes, with valid saved endpoint geometry.
- Relation names must be selectable and draggable. Correct rendering does not prove interactive editability.
- Save, reopen in a **separate VP process**, validate again and inspect exports from that reopened native project.

## 2. Establish scope and source truth first

1. Read `AGENTS.md` and applicable user instructions.
2. Identify the exact use case, prerequisites and explicitly excluded neighboring workflows.
3. Resolve contradictions using the project's authoritative decisions, not a convenient older requirements paragraph.
4. Inventory actual classes, interfaces, records/enums and relationships from source.
5. Distinguish implemented core logic from missing REST/UI composition, persistence, schedulers or external integrations.
6. Mark shared classes as **projections** when only the current slice's operations are shown. Explain omissions.

Do not turn a UUID field into ownership/composition. Do not imply cascade deletion merely because records reference a plan. Use composition only where part ownership/lifetime is justified.

For Java detail extraction, compile the application declarations to the project's target release, and retain parameter names when using reflection. Do not confuse declared business methods with generated record accessors, enum helpers, bridge methods or synthetic fields. State which boilerplate is implicit. In particular, enum constructors have implicit private access and compiler-injected arguments that should not be presented as source parameters.

## 3. Native generation through the Open API

### Local tooling

Current installation paths:

```text
X:\apps\Visual Paradigm 18.1
X:\apps\Visual Paradigm 18.1\lib\openapi.jar
X:\apps\Visual Paradigm 18.1\jre\bin\java.exe
%APPDATA%\VisualParadigm\plugins\<your-unique-plugin-id>\
```

Use a temporary Java plugin implementing:

```java
com.vp.plugin.VPPlugin
com.vp.plugin.VPPluginCommandLineSupport
```

A working descriptor, with compiled classes placed directly in the plugin directory:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugin id="example.uml" name="UML authoring" version="1.0"
        provider-name="Project" class="DiagramPlugin">
  <runtime><library path="."/></runtime>
</plugin>
```

Example **PowerShell** compile/run commands; supply the plugin source and a disposable native input project first:

```powershell
$vp = 'X:\apps\Visual Paradigm 18.1'
$tools = Join-Path $env:TEMP 'vp-diagram-tools'
$pluginDir = Join-Path $env:APPDATA 'VisualParadigm\plugins\example.uml'
$inputProject = Join-Path $tools 'input-copy.vpp'
$outputProject = Join-Path $tools 'output.vpp'

New-Item -ItemType Directory -Force -Path $tools, $pluginDir | Out-Null

& javac --release 11 -encoding UTF-8 `
    -cp "$vp\lib\openapi.jar" `
    -d $pluginDir "$tools\DiagramPlugin.java"
if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed' }

Push-Location "$vp\bin"
try {
    & "$vp\jre\bin\java.exe" '-Djava.awt.headless=true' '-Xmx2048m' `
        -cp '.;..\lib\*;..\ormlib\*' com.vp.cmd.Plugin `
        -project $inputProject -pluginid example.uml `
        -pluginargs $outputProject
    if ($LASTEXITCODE -ne 0) { throw 'Native authoring command failed' }
}
finally { Pop-Location }
```

The output argument is a convention for your own `invoke(String[] args)` implementation, not automatic project saving. Validate argument handling, especially paths containing spaces. Compile the plugin for the **bundled VP runtime**, separately from Java 17 application declarations; do not attempt to load newer class files into VP's Java 11 JVM.

The viewing user does **not** need the authoring plugin after the project has been saved.

### Protect input projects

**Copy the original before loading it into a modifying VP process.** In this workflow, `saveProjectAs(...)` also updated the loaded scratch input database; it was not an immutable input/output transformation.

- Keep an untouched baseline that is never opened for mutation.
- Load a different scratch copy for generation/repair.
- Do not compare output against a supposedly original copy that VP has already rewritten.
- Before replacing a user artifact, check that it has not changed during your work.
- Prefer a fresh delivery filename when the user may still have an older project open. Tell them to close the old version **without saving over the repair**.

### Start with valid project infrastructure

Use `ProjectManager.newProject()` to obtain proper defaults. A successful headless sequence was: load a scratch project, save that scratch state to make it clean, then call `newProject()`. Check boolean results and stop on failure; unsaved-project dialogs can break headless execution.

Do not indiscriminately delete root model elements to create an empty project. Removing project formatting/style/font infrastructure caused broken or missing rendering; deleting `ProjectFormat` caused an exception.

### Model construction

- Create classes through `IModelElementFactory.createClass()` and their shapes through `DiagramManager.createDiagramElement(diagram, classModel)`.
- Create real `IAttribute`, `IOperation`, `IParameter`, enumeration literal and relationship models, not text imitating member compartments.
- In the tested project, interfaces/enums were represented by `IClass` with `Interface`/`Enumeration` stereotypes; records were explicitly stereotyped. Recheck alternatives in the installed API if needed.
- Keep code identifiers faithful to source. Nested names such as `ExecutionPorts.Rituals` should remain recognizable.

## 4. Layout: finalize boxes before routing lines

### Arrange for explanation, not just compactness

- Use-case view: a small, composed entity interaction diagram, not an infrastructure inventory.
- Logical view: useful lanes are **services/adapters → contracts/shared support → domain**. Leave exhaustive injected dependencies for implementation.
- Implementation view: group related class families; retain all required members and allow zooming. Avoid a single extremely wide row.
- Use enough space for names, method signatures, arrowheads and captions. Do not place notes over classifiers or relationship ends.
- Inspect at whole-diagram scale and at readable member scale. Counts cannot certify representative design.

### Materialization and coordinate rules

Perform Swing/diagram mutations on the **EDT**. Use a guarded `SwingUtilities.invokeAndWait` helper; never call it from the EDT itself.

A reliable order is:

1. Create/configure all class models and shapes.
2. Set fonts and standard class presentation; materialize/reset captions and open the diagram.
3. **Reapply final explicit class bounds after caption materialization**, which can otherwise resize/recenter new shapes.
4. Route/create connectors against those final bounds.
5. Add notes without moving the classifiers.
6. Export, save and verify bounds again after reopening.

Useful settings include disabling diagram auto-fit/grid alignment and shape default-size/fit requests when supplying fixed geometry. Avoid uncontrolled `fitSize()` calls: early calls produced zero-sized or excessively narrow shapes.

Use `diagram.setZoomRatio(1.0)` while generating, saving and checking geometry. **Do not use `SPECIAL_ZOOM_FIT_PAGE` in a zero-size command-line editor**: it caused coordinate/dimension quantization, including `360×70` boxes becoming `350×50`, breaking attachment. This does not prevent the user from zooming normally in the desktop editor.

For ordinary class boxes, disable robustness-analysis/stereotype-icon presentation where appropriate. The stereotype `entity` otherwise produced robustness icons instead of class rectangles. Set actual fill colors through `getFillColor()`, not just `setBackground(...)`.

### Optional Graphviz/PlantUML

Deterministic manually composed bounds or Graphviz-computed geometry worked better than repeatedly laying out an already-routed native diagram.

When transferring Graphviz geometry:

- Use fixed node dimensions and orthogonal routing; suppress Graphviz arrowheads/tails so path ends reach the node perimeter. VP supplies UML decorations.
- Account for Graphviz's bottom-up Y axis and dimensions in inches; the native diagram uses top-down diagram coordinates. Convert centers to rectangle origins consistently.
- Extract/simplify orthogonal path points; remove repeated and redundant collinear points.
- `xlabel`/`xlp` can supply less-crowded label positions.
- Check installed engine capabilities. The PlantUML-bundled Windows `dot.exe` used here supported **dot only**, not `neato`; `-Kneato` failed.

PlantUML's Smetana renderer worked for some views but failed on another. A renderer can emit a valid SVG containing an **error image** even when process/XML checks look successful. Check error text and actual diagram content, not just exit code or SVG syntax.

## 5. Relationships: semantics and visual attachment are separate

For an association, set native model endpoints, association-end cardinalities and navigability/aggregation. For a dependency, set client → used type. For composition, set the owning association end's aggregation to `AGGREGATION_KIND_COMPOSITED`.

**VP 18.1 realization convention observed here:** the Open API model runs from the supplier/interface to the implementing class. This is opposite the usual PlantUML `Implementation ..|> Interface` source ordering. Reverse both native endpoint shapes and point order when translating that relation. Verify the drawn hollow triangle points to the interface; do not blindly reverse every relationship.

Create each connector with explicit final points and retain the returned object:

```java
IRelationship relation = /* native relationship with from/to models */;
IShapeUIModel fromShape = /* matching native from model */;
IShapeUIModel toShape = /* matching native to model */;
java.awt.Point[] points = /* route in diagram coordinates */;

IConnectorUIModel connector = (IConnectorUIModel) dm.createConnector(
    diagram, relation, fromShape, toShape, points);
connector.setFromShape(fromShape);
connector.setToShape(toShape);
connector.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
```

Use perimeter connection points. For fixed explicit endpoints, ratio pins and offsets worked:

```java
int dx = endpoint.x - shape.getX();
int dy = endpoint.y - shape.getY();
Point2D ratio = new Point2D.Double(
    (double) dx / shape.getWidth(), (double) dy / shape.getHeight());

connector.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);
connector.setFromPinRatio(ratio);
connector.setFromShapeXDiff(dx);
connector.setFromShapeYDiff(dy);
connector.setUseFromShapeCenter(false);
// Apply corresponding To methods at the other end.
```

Keep point order consistent with native from/to shapes. Set a white connector background where required for **hollow** realization triangles; the default blue background filled them blue.

**Never accept `createConnector(..., null)` plus relationship counts as proof of attachment.** Moving/laying out nodes after line creation left persisted points at old positions. Caption-reset requests do not rebuild connector ends.

If nodes move, request appropriate endpoint rebuilding/routing and revalidate. A successful **move + explicit rebuild** test does not establish that a raw API `setLocation()` automatically updates all lines.

## 6. Critical fix: draggable relation captions

### Correct mode in VP 18.1

For a **relationship-name caption**, use:

```java
caption.setSide(ICaptionUIModel.SIDE_NONE); // value 1: normal drag-supported mode
```

**Do not force `SIDE_FREEMOVE` (12) on relationship names.** Despite its name, it rendered the labels but blocked ordinary caption interaction in the tested VP version.

The installed native caption press/drag implementation accepts side 1. A regression using the native Swing caption component and dispatched mouse events confirmed:

| Caption mode | Text hit-test | Mouse-drag displacement |
| --- | --- | --- |
| `SIDE_FREEMOVE` / 12 | false | `(0, 0)` |
| `SIDE_NONE` / 1 | true | `(60, 30)` |

All 62 repaired relation-name texts/dimensions passed the native-widget test. This was **not** full desktop GUI automation. Internal class names used to investigate this were `v.did.adk` and its superclass `v.ceq.px`; they are version-specific internals, not stable public APIs for production code.

### Repair already-loaded captions and persist the change

A bare `caption.setSide(...)` appeared to work in memory but **did not persist** in the first repair attempt. Synchronizing/resetting the native view, restoring bounds and marking the diagram modified worked:

```java
// Run on the EDT, on a scratch-loaded project, at zoom ratio 1.0.
ICaptionUIModel cap = connector.getCaptionUIModel();
if (cap.isVisible() && !connector.getMetaModelElement().getName().isEmpty()) {
    int x = cap.getX(), y = cap.getY();
    int w = cap.getWidth(), h = cap.getHeight();

    cap.setSide(ICaptionUIModel.SIDE_NONE);
    connector.setRequestResetCaption(true);
    connector.resetCaption();

    cap = connector.getCaptionUIModel();
    cap.setSide(ICaptionUIModel.SIDE_NONE);
    cap.setBounds(x, y, w, h);
    diagram.modified();
}
// Save through ProjectManager; then reopen and check the persisted caption mode/bounds.
```

Target **visible relationship names only**. Do not globally replace side 12: annotation captions use a different interaction path and were intentionally left alone. Leave unnamed/hidden captions alone too.

Native reset/save may also refresh endpoint-label rectangles or reset flags. Our repair preserved all model rows, routes and main-caption bounds, but slightly adjusted some multiplicity-label rectangles. Do not describe that as a byte-for-byte side-only patch.

For new labels, set real relationship names and give captions enough space for text **and internal padding**; `FontMetrics` plus padding is safer than character counts. We used explicit caption positions after reset, with normal side 1, and validated after reopening. Do not treat `setRequestResetCaptionSize(false)` as a documented lock/unlock switch; it is a layout request flag, not proof of editability.

Normal desktop gesture: click the connector line, then drag its caption text rather than an endpoint/bend handle.

## 7. Critical fix: notes must own real registered native models

Working pattern:

```java
IShapeUIModel note = (IShapeUIModel) dm.createDiagramElement(
    diagram, IShapeTypeConstants.SHAPE_TYPE_NOTE);
IModelElement model = note.getModelElement();
if (model == null) throw new IllegalStateException("Native note model missing");
model.setName(text);
model.setDocumentation(text);
note.setBounds(x, y, width, height);
note.resetCaption();
```

**Do not replace that model with `factory.createComment()` using `setMetaModelElement(...)`.** In our failed implementation, the note looked correct in memory, but its actual registered NOTE model remained empty and the replacement reference was not persisted. Titles, headings and explanatory notes disappeared after reopening.

Check persisted note text/model references and inspect reopened native exports. Native SVG exports can encode note text as embedded images rather than SVG `<text>` nodes; failure to find a literal heading in SVG text is not conclusive evidence that it is missing.

## 8. Verification that must precede delivery

### Structural checks

- Expected diagram names/count and view-specific member policy.
- Actual classifier/interface/enum identities and source-backed members.
- Field types/visibility/static/read-only properties, method parameters/return types, constructors and declared omission policy.
- Correct relationship kinds, model endpoints, shape endpoints, cardinalities and composition/realization direction.
- Notes point to persisted nonempty native models.
- Relationship-name captions retain normal drag-supported mode and useful dimensions.

### Geometry checks

For every connector:

1. From/to shapes belong to the correct diagram and correspond to native relationship endpoint models.
2. There are at least two distinct route points.
3. The first/last points touch the intended class perimeter; a tolerance of about two diagram units was used here.
4. Terminal segments exit/enter the correct sides, without cutting through their own classes.
5. Routes are orthogonal where requested and do not pass through unrelated classifier interiors.
6. Class rectangles are positive-sized, nonoverlapping and unchanged after reopening.

Crossings between **lines** are not the same as lines cutting through **classes**. Use clear routing/line jumps, and inspect both.

### Native save/reopen and interaction checks

- Save to a new native file and reopen it in another VP process.
- Recheck bounds, fields, signatures, routes, notes and caption modes.
- Export actual native SVG/PNG previews from that reopened project; inspect all views and detailed crops.
- Test caption hit-selection and drag movement, then position persistence where practical.
- Test class movement with explicit connector rebuilding in a disposable copy.
- Record precisely whether verification was model-level, native-widget-level or a full desktop GUI test. Never substitute counts or a screenshot for interaction testing.

`com.vp.cmd.Plugin` is a **command-line environment**. Setting `java.awt.headless=false` and showing `ViewManager.getRootFrame()` did not turn it into the full desktop editor: it produced a dummy JFrame, with editor size `0×0`. Do not claim desktop testing from it, or capture unrelated desktop content as evidence. A separate full desktop startup attempt was unavailable in our automation; native Swing input dispatch supplied the bounded interaction regression instead.

### Optional read-only native-file inspection

The tested `.vpp` files are SQLite databases, with tables such as `MODEL_ELEMENT`, `DIAGRAM` and `DIAGRAM_ELEMENT`. Read-only queries helped validate persisted state. **Do not edit the database directly** as a substitute for the Open API.

Do not mistake serialized relative coordinates for broken absolute geometry. In the observed ratio-pin representation, stored `_points` except the last are relative to the from-shape origin; the last is relative to the to-shape origin. API `getPoints()` supplied diagram-space geometry. Caption coordinates also have translation rules and may legitimately be negative. Reverify these details before writing another decoder.

## 9. Delivery, provenance and cleanup

Deliver:

- Native `.vpp` project, editable without the authoring plugin.
- A short README explaining views, scope, projection/boilerplate policy and known runtime gaps.
- Previews **from the reopened native project**, clearly identified as such.
- Validation results tied to the delivered project's SHA-256.
- Reusable generator/metadata/validation tools if future regeneration is required; do not rely on temporary files surviving.

PlantUML sources are useful supplemental text, but independently laid-out PlantUML SVGs **do not prove native VP layout or editability**. Label their provenance honestly.

The local VP installation was an evaluation edition. Its native exports have evaluation watermarks; do not remove them. Do not confuse a licensing/export watermark with the caption-mode defect.

Remove only your own temporary installed plugin IDs and generated scratch backups. Preserve user files and immutable baselines. Stop and report failed validations instead of silently delivering an unverified artifact.

## 10. Copyable brief for a future agent

> Read this guide and `AGENTS.md` before changing diagrams. Work only on the requested use case. Produce genuine native Visual Paradigm UML elements, with the requested abstraction/member policy. Finalize class geometry before routing and keep native relation-name captions in the standard draggable mode, not `SIDE_FREEMOVE`. Use registered native NOTE models. Protect source projects with a never-loaded baseline and operate on scratch copies. Verify native save/reopen, semantic and visual endpoints, member signatures, caption selection/dragging and reopened native previews; clearly state any untested desktop behavior. Preserve user edits, disclose projection/implementation gaps and attach validation to the final file hash.

## Reference deliverable from this work

See [UC-4 project and notes](uc-4/README.md) and [its validation record](uc-4/validation.json). The UC-4 counts and filenames are examples, not fixed requirements for later tasks. This guide is self-contained; authoring helpers previously kept under the Windows temporary directory are **not** guaranteed to exist in later sessions.
