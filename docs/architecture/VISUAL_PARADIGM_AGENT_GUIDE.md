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

## 11. UML 2 activity diagrams: UC-2 findings

The [UC-2 activity delivery](uc-2/README.md) adds a reusable [Open API plugin and PowerShell runner](uc-2/tools), source-model regression tests and a saved/reopened model validator. The earlier class-diagram abstraction table does **not** define activity-diagram detail: follow the requested business / package-interaction / method-and-object levels instead.

### Correct native model families

References: [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1), Visual Paradigm's [activity Open API example](https://knowhow.visual-paradigm.com/openapi/activity-diagram/) and [swimlane Open API example](https://knowhow.visual-paradigm.com/openapi/activity-diagram-swimlane/). Confirm API signatures against the installed `openapi.jar`; the examples are not a substitute for save/reopen checks.

Use `IActivity`, `IActivityAction`, `IInitialNode`, `IActivityFinalNode`, `IDecisionNode`, `IMergeNode`, `IForkNode`, `IJoinNode`, `IObjectNode` and `IControlFlow`. Do not substitute legacy `IActionState`, `IInitialState` or `ITransition` models. In this API, the UML 2 object-flow interface is **`IActivityObjectFlow`**; `IObjectFlow` exposes the older trigger/effect/guard representation.

A method invocation is an `IActivityAction` whose `setActionType(...)` receives an `ICallOperationAction`; the latter references a real `IOperation` with typed `IParameter` models. An object result should originate from a typed `IOutputPin`, not an untyped arrow from a method name. Keep the pin/ObjectNode type consistent with the operation's return type: `Optional<T>` is not `T`. Displaying a projected, unwrapped result requires a separate operation/opaque composite that actually returns that type.

Do not confuse lifecycle status data with UML State nodes. Decision guards should be mutually exclusive; use MergeNode for alternatives and JoinNode only for synchronization. Custom styling must preserve **black initial/fork/join nodes and the final-node bullseye**: setting every non-action fill white removes their standard notation.

### Real partitions and package correspondence

The tested swimlane authoring pattern is:

1. Create `IActivitySwimlane2` under the activity and an `IActivitySwimlane2NewUIModel` view.
2. Create `IActivityPartition` models; add them with both `addVerticalPartition(...)` and `addChild(...)` on the swimlane.
3. Create `IActivityPartitionHeaderUIModel` shapes. Set `setHorizontal(false)`, `setSwimlane(swimlaneShape)` and add each header to the swimlane shape.
4. Create `IActivitySwimlane2CompartmentUIModel` shapes using `SHAPE_TYPE_ACTIVITY_SWIMLANE2_COMPARTMENT`. Their `setVerticalPartitionId(...)` takes the **header shape ID**, not the partition model ID.
5. Register header IDs through `setVerticalPartitionIds(...)` and compartment IDs through `setCompartmentIds(...)` on the swimlane shape. For a single dimension, empty horizontal IDs worked.
6. Add each node model to the swimlane, register membership with `partition.addContainedElement(node)`, and add its shape to the matching compartment. Geometric containment alone does not establish semantic membership.
7. `partition.setRepresents(packageModel)` provides a real native link to a package reference. Locate actions according to the owner of the operation, not merely the type of data it manipulates.

For structured exception-handler bodies, move internal node/edge ownership to the structured node and move their shapes into its view; partition membership may still identify their responsibility.

### Transactions, failures and asynchronous work

A normal control-flow chain `audit → save → outbox → success?` does **not** model write exceptions: a throwing action never emits its normal completion token. Include real exception semantics, not only a note saying rollback.

UC-2 uses native `IExceptionHandler` relationships from protected executable nodes to a shared, Exception-typed `IInputPin` owned by an `IStructuredActivityNode` handler body. Add the catch type through `addExceptionType(...)`. The handler body has **no external normal control edges**; internally it rolls back and reaches ActivityFinal, preventing the failed write sequence from resuming. There is an explicit successful COMMIT action. Also protect the deliberate missing-resource exception: a normal guard-rejection path must not silently exit an open transaction.

Native handler relationships must remain ExceptionHandler models, not named ControlFlows. The saved routes include a small lightning zigzag and use oblique connector presentation for that segment; normal control/object flows remain orthogonal. Geometry validation must account for this intentional exception to the orthogonal-only rule.

Do not bind rollback cleanup to `CommandTransaction.execute`: that inaccurately depicts starting another command. Show cleanup as an opaque action or link a clearly proposed cleanup operation.

Separate HTTP response presentation from background notification delivery. A fork after COMMIT can return the API response independently of the delivery branch; a later join belongs to the complete business activity, not the HTTP-request lifetime. Delivery failures never undo a committed transition.

### Additional VP 18.1 persistence/layout traps

- `setDescription(...)` and `setDocumentation(...)` aliased the same persisted field in this workflow. Writing identity metadata to the former and explanatory prose to the latter **lost the identity metadata after saving**. UC-2 puts a short ID prefix and prose in one documentation value; do not assume those fields are independent.
- `com.vp.cmd.Plugin -pluginargs` consumed one argument value, not all following shell tokens. The runner passes one `mode|source-path|output-path` token and splits it explicitly in `invoke(...)`. Validate the resulting argument count; paths with spaces still need normal shell quoting.
- `IConnectorUIModel` exposes `clearPoints()` / `addPoint(...)`, not `setPoints(...)` in this installation.
- Hide autogenerated control-node names such as `DecisionNode7` after shape creation, while preserving action/object text. Otherwise correct UML diamonds acquire distracting default captions.
- Guard labels need padding for brackets and the native caption renderer. Too-small boxes split `CONFIRMED` mid-word. Place the most constrained short-route labels first; keep each label beside **its own route**, not merely near the source diamond. Avoid node/label overlaps and inspect native exports.
- View-specific preconditions are real model properties. Changing only a visible note does not correct a stale native `IActivity.precondition`.

### Target design versus current code

Before linking calls, compare application orchestration and entity mutations with the authoritative transition matrix. In UC-2, the enum matrix forbade direct SUBMITTED → APPROVED while the existing command and its tests still used that transition. A correct enum diagram was not proof that the command complied.

It is valid to deliver a target design when the requested behavior is not implemented, but mark proposed methods, request fields, condition models, transaction wiring and adapters explicitly. Do not fabricate an existing method to make the activity appear complete. Keep real method signatures separate from proposed overloads and document the differences in the delivery README.

## 12. System package diagrams: source hierarchy and note metadata

The [package-diagram delivery](packages/README.md) includes a reusable Open API
plugin, source-import inventory, Graphviz geometry, regression tests and a
PowerShell save/reopen runner. It uses native `IPackage`, `IDependency` and
`IPackageDiagramUIModel`, not classes styled to resemble packages.

- Preserve semantic package hierarchy with `parentPackage.addChild(childPackage)`.
  Flat placement in a diagram need not change ownership. In one overview,
  dependencies may be aggregated over descendant packages; say so explicitly.
  A detailed view of the same parent can instead mean its direct source contents.
- `IPackageUIModel.setShowOwnerOption(SHOW_OWNER_NONE)` avoids unexpectedly long
  qualified-owner captions. Keep source paths in human-readable package notes.
  Do not invent domain subpackages merely because requirements name domain concepts.
- UML Dependency is not UML PackageImport. Java imports justify compile-time
  package dependencies; frontend HTTP contracts are a different, explicitly named
  dependency basis. Neither establishes runtime call order or provider readiness.
- Graphviz `dot -Tjson`, fixed node dimensions, `splines=ortho` and disabled
  Graphviz arrowheads supplied usable perimeter-reaching routes. Remove repeated
  and collinear spline control points; check the transformed routes before VP
  authoring and again after reopening. Graphviz is documentation tooling only.

### NOTE documentation is visible text

A registered native NOTE renders its **documentation**, not necessarily just its
name. Adding an identity prefix to documentation made that prefix visible in
native PNG/SVG exports, even though a validator checking only `getName()` passed.
For notes, store only explanatory prose in **both** name and documentation.
Use a real tagged value for identity instead:

```java
noteModel.setName(text);
noteModel.setDocumentation(text);
ITaggedValueContainer values = factory.createTaggedValueContainer();
noteModel.setTaggedValues(values);
ITaggedValue id = values.createTaggedValue();
id.setName("sourceId");
id.setType(ITaggedValue.TYPE_STRING);
id.setValue(stableIdentity);
```

The `sourceId` value survived native save/reopen in VP 18.1 without appearing in
the note body. Do not confuse `getName()` with the rendered prose: validate note
name **and documentation**, and inspect reopened exports. The package tool hides
qualified package owners and leaves ordinary tagged-value display disabled.
Its normal caption mode is checked via Open API; it does not claim a desktop
mouse-drag regression. Existing UC-2/UC-4 projects were not altered by this work.

## 13. UML 2 sequence diagrams: UC-3 findings

The [UC-3 delivery](uc-3/README.md) supplies three sequence abstraction levels,
linked implementation details and a map of the existing system packages. Its
[generator and native validator](uc-3/tools) are reusable. References:
[VP sequence Open API example](https://knowhow.visual-paradigm.com/openapi/sequence-diagram/)
and [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1).
The class-diagram member policy in section 1 does not prescribe sequence detail.

### Native model families and ownership

- Use `IInteractionDiagramUIModel` with
  `DIAGRAM_TYPE_INTERACTION_DIAGRAM`; there is no `ISequenceDiagramUIModel` in
  the tested jar. `getRootFrame(true)` returns the native `IFrame`. UC-3 also
  creates an `IInteraction`, associates its diagram ID and owns the root frame.
- Use `IInteractionLifeLine` with `setBaseClassifier(...)`; runtime participants
  are instances of classifiers, **not instances of UML Package**. For a
  package-level view, own representative subsystem classifiers in real packages.
  When the user references an existing package project, reuse its package
  identities and aggregation level, not invented service-responsibility packages.
  UC-3 now reads `packages/packages.xml`'s `01-system` selection directly:
  `frontend/src` is displayed as `frontend`, `application.*` stays aggregated,
  and `fake`/`config` are excluded. PostgreSQL and the provider are explicitly
  additional runtime participants, not source Java packages. Logical initiation
  is `frontend → api`; no actors belong in this logical view.
- `IInteractionActor` with a referenced `IActor` is a native actor participant,
  but giving that shape full interaction height produced an unwanted continuous
  actor bar. The repaired UC-3 uses an `IInteractionLifeLine` classified by an
  actual `IActor`, plus a compact native Actor-symbol view above its header.
  The symbol is a child of the lifeline view and shares its classifier model;
  its 40×50 bounds do not grow with the dashed tail. Merely classifying a
  lifeline by Actor did **not** render a stick figure, even with the robustness
  icon flag enabled: inspect exports, not just model types. Keep all requested
  actors to the left of the system; omit their execution bars.
- `IMessage` model `setFrom(...)`/`setTo(...)` and connector shape endpoints
  must both refer to the intended participants. Create real `IActivation`
  models with `lifeline.addActivation(...)` and native `IActivationUIModel`
  views (`BODY_WIDTH` is 8 in this jar); link message `setFromActivation(...)`
  and `setToActivation(...)`. UC-3 shows bounded local processing segments on
  every system participant, including covered `ref` participants. These are
  not elapsed method durations or one continuous execution across HTTP
  requests. Set `setShowActivations(true)` and disable auto-extension; verify
  saved activation ownership, bounds and message links, not only visibility.
- Calls use `IActionTypeCall.setOperation(realOperation)`, with typed
  `IOperation`/`IParameter` models. Replies use `IActionTypeReturn`, and the
  originating call links the reply through `setReturnMessage(reply)`.
  `IMessage.TYPE_SELF_MESSAGE` provides native self-call presentation.
  In this rendering, the linked action can append a short operation label even
  with operation-signature display hidden: size captions for the rendered text,
  not only `IMessage.getName()`.
- Use `IInteractionOccurrence.setRefersTo(targetFrame)` for genuine `ref`
  InteractionUse, not a note that resembles a ref. Create all target frames
  before linking. Its covered participants must include all participants shared
  with the enclosing interaction; UC-3 readiness also uses the enclosing pollen
  object. Detail-only participants need not exist in the overview.

### Combined fragments and persisted geometry

- Use `ICombinedFragment`, `IInteractionOperand` and `IInteractionConstraint`.
  Set the operator, add covered lifelines and operands, and register messages
  using `operand.addMessage(message)`. Geometric containment alone is not
  semantic membership.
- `CombinedFragment.setName("")` throws **Not allow no name** in VP 18.1.
  Keep a stable nonempty model name; the visible tab still shows the operator.
- Create operand shapes as children of their fragment shape. VP anchors the
  first operand directly below the operator tab (21 diagram units in this
  workflow), then lays out subsequent operands using their heights. Arbitrary
  independent operand Y coordinates are not retained. Compute contiguous heights
  from the fragment top, allowing a guard band before each operand's messages.
  Otherwise a saved separator can cross a message even when all counts pass.
- A nested fragment belongs to its parent operand through
  `addCombinedFragment(...)`/`addChild(...)`, with the shape also inside the
  operand shape. Validate semantic parent and message/guard clearance after
  reopening; check parent bounds include the nested fragment.
- Messages can remain owned by VP's relationship container even after
  `operand.addChild(message)`. Their persisted `getParent()` need not be the
  operand. Validate `operand.toMessageArray()` membership instead. An operand
  containing only a nested fragment can return **null**, not an empty array;
  handle this when inspecting outer `opt`/`alt` wrappers.
- For filled synchronous-call arrowheads, use an ink-colored connector
  **background** as well as foreground. The white background recommended for
  hollow realization arrows produces an unwanted hollow call triangle here.
  Keep native reply line/arrow notation; inspect reopened exports.
- Pin message ends to the lifeline center at the intended Y, not the participant
  header rectangle's perimeter. Activation edges can offset the endpoint by
  four diagram units in this workflow; use a bounded activation-aware tolerance.
- `project.toAllLevelModelElementArray()` does not enumerate embedded class
  operations. Iterate each `IClass.toOperationArray()` to inspect persisted
  signatures and parameters. Native call links still resolve those operations.

### Trace correctness is separate from layout correctness

- Manual checklist completion and `/mark-ritual-ready` are distinct commands.
  Show the explicit transition request; a completion ref must not implicitly
  trigger a lifecycle transition.
- Validation errors must bypass mutation. A guarded `break` can end the
  enclosing interaction before save. A conflict check must branch or break
  before a proposed confirmation saves `CONFIRMED`.
- An error operand containing only rollback is not a failed-write trace when
  every attempted write occurs exclusively in another operand. Show attempted
  persistence in the error trace, identify the throwing call, and ensure success
  is returned only in the successful-commit branch. Do not append an optional
  error branch after an already returned success.
- Define pre/postconditions for **each interaction**. Resource/task/assignment
  details keep the plan APPROVED; they do not independently establish
  RITUAL_READY. Distinguish target contracts/transaction wiring from existing
  operations, especially when a current checklist requires READY but normative
  rules require completed mandatory tasks.

The UC-3 verification is native model/geometry/export validation plus independent
semantic review, not full desktop interaction automation. Evaluation watermarks
remain present. Existing source package and UC-2/UC-4 projects were preserved.

## 14. UML 2 communication / cooperation diagrams: UC-5 findings

The [UC-5 delivery](uc-5/README.md) contains six genuine Communication diagrams
across the abstract, package and method/object levels. Modern UML 2 calls these
**Communication diagrams**; do not silently deliver sequence diagrams or use the
legacy Collaboration diagram family when cooperation is requested. Reference:
[VP Communication Open API example](https://knowhow.visual-paradigm.com/openapi/create-communication-diagram/).
The example's `setTo(from_lifeline_model)` is a typo; select the intended target.

### Native participants, links and message views

- Create `ICommunicationDiagramUIModel` with
  `DIAGRAM_TYPE_COMMUNICATION_DIAGRAM`. Use `IInteractionLifeLine` typed by a
  real classifier, and `IInteractionActor.setReferencedActor(realActor)` for
  compact native communication actors. There are no sequence-style dashed tails
  or execution bars in this notation. Keep interaction pre/postconditions on a
  real `IInteraction` associated with the diagram/root frame.
- Communication messages are **shapes owned by a link connector**, not sequence
  message connectors between lifeline tails. Create `IInteractionLifeLineLink`,
  its from/to models and explicitly routed connector. Register every real
  `IMessage` with `link.addMessage(message)` and add its view as a child of that
  connector. Validate both model endpoints and shape/parent-link membership.
- The tested explicit-direction authoring path creates a
  `SHAPE_TYPE_COMMUNICATION_MESSAGE` or
  `SHAPE_TYPE_COMMUNICATION_REVERSE_MESSAGE` view, obtains its already registered
  `IMessage`, sets its properties, then registers it with the link and parents
  the view. Reassert intended from/to after parenting. Do not replace the
  registered message with an unrelated model.
- Compare endpoints by `getId()` (or reliable model equality), **not Java `==`**:
  Open API can return different proxy instances for the same saved endpoint.
  An identity comparison classified every forward message as reverse in a failed
  prototype. Validate the persisted `getShapeType()` against the expected
  forward/reverse view; native marker-interface `instanceof` is not that check.
- For single-message views, leave `IBaseCommunicationMessageUIModel.messageIds`
  alone. Setting it to the message model ID in the prototype produced repeated
  **Cannot find grouped communication message shape when reassociate** warnings
  after reopening. UC-5 disables per-direction grouping and uses independent
  native message views; it does not claim a tested grouped-message recipe.
- A self-call uses the same participant at both link endpoints. A simple
  four-point rectangular self-link persisted reliably; longer out-and-back
  routes were normalized and could leave a diagonal last segment. Keep both
  endpoints on the participant perimeter. UC-5 uses ordinary communication
  message presentation with the self-link, not sequence-only self-message styling.

### UML semantics and readable presentation

- Enable native sequence numbers. Numbering supplies order, not Y position.
  Use nested sequence expressions for nested calls: calls made inside the
  `execute` callback are `1.1.*` when `execute` is `1.1`; the successful caller
  response is outside that scope. Include every audit append before COMMIT.
- Use real `IActionTypeCall` with an `IOperation`, typed parameters and visibility;
  use `IActionTypeReturn` for replies. Guard/iteration properties are
  `setRecurrenceCondition(...)` / `setIteration(...)` on these action types.
  VP 18.1 did not visibly print the Return recurrence condition in this view;
  UC-5 also includes that guard explicitly in the reply name while retaining
  the native property. Conditional writes must not execute on a failed trace.
- A message view's actual fill color, as well as its foreground/background,
  controls filled synchronous arrowheads. Ink fill worked; a white fill left
  hollow triangles. Native message captions can append a short linked operation
  name even with signature display disabled: reserve space for it, guards and
  sequence numbers, not only `IMessage.name`.
- Keep message shapes and caption bounds sufficiently wide. A prototype with
  tiny arrow shapes and wide independent captions clipped/obscured leading text
  in reopened exports despite passing model-bound checks; it was rejected.
  Inspect actual exports, including multiline labels and both arrow directions.
  The native renderer also centered multiline names across the message arrow,
  overlaying their lower row. The accepted UC-5 generator therefore keeps
  message names single-line and enlarges the reserved caption width instead;
  this visual regression has its own automated source-model test.
- Graphviz `splines=ortho` does not reliably avoid other edges' label rectangles.
  UC-5 reserves explicit label blocks, simplifies paths, repairs two constrained
  package-view corridors, and tests that routes avoid unrelated nodes **and
  unrelated message text**. Native save/reopen endpoint checks remain necessary.
- A package-level participant is a subsystem representative classifier owned by
  an existing package, not an instance of `IPackage`. UC-5 retains the native
  `object : classifier` captions and documents the representatives. Setting
  generic `customText` did not hide classifier text in the tested renderer;
  do not claim otherwise from an in-memory property assignment alone.

### Detail navigation and bounded runtime claims

`IOperation.addSubDiagram(...)` was rejected with **Composite model ... does not
allow containing sub diagram**. A native subdiagram link on the owning
`CompletionService` classifier worked and survived reopening. Name that actual
navigation mechanism in the README; do not call it an InteractionUse/ref.

Separate incompatible port objects even when their method names match:
`Metrics.forPlan(UUID)` and `Incidents.forPlan(UUID)` cannot be implemented as
one Java method with unrelated return types. A dedicated source-snapshot detail
is clearer than inventing a universal implemented source service.

The authoring entry point drains pending EDT work before the final native save.
Without that drain, queued actor/model updates in an early prototype ran during
command-line shutdown and reported **database connection closed**. The accepted
run saved after the deferred updates, reopened in another VP process and passed
all checks. This is not a substitute for full desktop interaction testing.

UC-5 distinguishes existing core services/ports from target REST/UI, source and
report JDBC adapters, composition and PDF/HTML rendering. Local locking cannot
provide an atomic snapshot across the remote Pollen Tree and PostgreSQL. Native
verification and independent review do not certify those missing runtime slices.

## 15. UML 2 deployment diagrams: native FreeBSD findings

The [deployment delivery](deployment/README.md) supplies one detailed native
DeploymentDiagram, machine characteristics, an interface register, regression
checks and a separate-process save/reopen runner. References:
[VP deployment Open API](https://knowhow.visual-paradigm.com/openapi/deployment-diagram/)
and [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1).

### Native types and relationships

- Use `IDeploymentDiagramUIModel` / `DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM`.
  The tested `openapi.jar` exposes **neither `IDevice` nor
  `IExecutionEnvironment`**. Use real `INode` models with standard `device` /
  `executionEnvironment` stereotypes. Generic external Nodes need not assert an
  unknown hardware platform. Use `IArtifact` and `IDeploymentSpecification` for
  real files and runtime configuration, not classes styled as deployed files.
- Nest Nodes with `parentNode.addNode(childNode)` and artifacts with
  `parentNode.addArtifact(artifact)`; deployment specs have their own
  `addDeploymentSpecification(...)`. Separately call `parentShape.addChild(...)`.
  Check **both** semantic owner and parent shape after reopening. UML allows
  nested artifact notation instead of drawing a redundant deploy-arrow.
- **VP 18.1 Deployment endpoint convention:** the accepted native API ordering
  is `deployment.setFrom(targetNode); deployment.setTo(artifact)` (supplier →
  client). `setTo(node)` failed with **Invalid connection. Node is not allowed
  with Deployment**. Do not interpret API ordering as the visual direction of
  the UML dependency arrow. The delivery uses nested notation and stores the
  actual Deployment models without duplicate arrows; its validator checks this
  API convention explicitly.
- A UML CommunicationPath is an Association between deployment Nodes: use
  `IAssociation`, including both native ends and shape endpoints. Ordinary
  dependencies between artifact names are not substitutes for network paths.
  Connections are not sequence messages; describe the connection initiator and
  data direction in an interface register/documentation when they differ (a
  standby connects to primary, but receives primary WAL).
- `IDeploymentDiagramUIModel` did **not** expose `setShowDiagramFrame(...)` in
  this jar, unlike some other diagram families. Do not copy methods solely
  because another family supports them. Use `javap` and compile for VP Java 11.

### Deployment correctness and scope

- User constraint for Helios: **native FreeBSD deployment, no Docker runtime**.
  Old Docker references in Vision/SRS and dev-compose files are not authority
  to place containers on that server. Artifacts are manually published; Node.js
  and Vite are build tools, not required production execution environments.
- Host inventory is not an application allocation. Preserve documented physical
  CPU/RAM/storage and mark quotas, proxy availability, :443 privileges, external
  machines and replica sizing as unverified/proposed where appropriate.
- Preserve real route boundaries. This frontend adds `/api`; backend mappings
  are root-relative. A proposed proxy must strip `/api/`, including auth/admin
  routes, and must not return SPA HTML for failed API requests.
- An unprivileged non-owner runtime role cannot also own schema migrations.
  Explicitly separate a privileged one-shot migration release step from runtime
  startup (proposed `SPRING_LIQUIBASE_ENABLED=false` override), or define a
  separate migration connection. Do not imply that this repository already
  supplies that production release wiring. Likewise, a management loopback bind
  does not prove health access: the current security chain denies actuator URLs.
- Four-hour backups alone cannot guarantee zero audit loss. A synchronous
  off-host replica can protect acknowledged **local** commits against one-host
  loss; label the failure model, degraded availability and lack of distributed
  atomicity with Pollen Tree. Do not claim a replica solves malicious DBA access,
  simultaneous loss of both hosts, restore automation or immutable retention.

### Validation and presentation

The deployment validator checks native model families/stereotypes, Deployment
models, semantic/shape containment, persisted note text, normal relation captions,
exact saved bounds and perimeter-attached routes. Hierarchical geometry checks
must permit intentional containment while rejecting sibling overlaps and routes
through unrelated shapes. Keep explanatory notes below the full node header:
long headers can wrap under the stereotype and overlap an otherwise nonoverlapping
child note. Inspect reopened exports, not only rectangle counts. This delivery
checks Open API caption mode; it does **not** claim a desktop drag test or live
production/backup/failover certification.
