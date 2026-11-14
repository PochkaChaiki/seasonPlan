/* Source AST tooling: uses the project's existing Babel parser; no app dependency added. */
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const projectRequire = createRequire(path.resolve(__dirname, '../../../../frontend/package.json'));
const parser = projectRequire('@babel/parser');
const root = path.resolve(process.argv[2]);
function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir, e.name)) : [path.join(dir, e.name)])
    .filter(p => /\.(jsx|js)$/.test(p) && !p.includes('.test.')).sort();
}
const result = files(root).map(file => {
  const code = fs.readFileSync(file, 'utf8');
  const ast = parser.parse(code, { sourceType: 'module', plugins: ['jsx'] });
  const source = path.relative(root, file).replaceAll('\\', '/');
  const m = { id: 'frontend/' + source, name: path.basename(source), package: path.posix.dirname(source) === '.' ? 'frontend/src' : 'frontend/src/' + path.posix.dirname(source),
    source, kind: source.endsWith('.jsx') ? 'React module' : 'JavaScript module', owner: '', visibility: 'public',
    abstract: false, final: false, attributes: [], operations: [], literals: [], imports: [], declaration: '' };
  const text = n => n ? code.slice(n.start, n.end) : '';
  const fn = n => n && ['ArrowFunctionExpression', 'FunctionExpression', 'FunctionDeclaration', 'ObjectMethod', 'ClassMethod'].includes(n.type);
  function operation(n, name, scope, exported) {
    m.operations.push({ name, returns: n.async ? 'Promise<dynamic>' : /^[A-Z]/.test(name.split('.').at(-1)) ? 'ReactNode' : 'dynamic',
      constructor: false, visibility: exported ? 'public' : 'private', static: scope === '', abstract: false,
      parameters: n.params.map((p, i) => ({ name: text(p), type: 'dynamic' })), line: n.loc.start.line });
    walk(n.body, name, false);
  }
  function walk(n, scope = '', exported = false) {
    if (!n || typeof n !== 'object') return;
    const qualified = name => scope ? scope + '.' + name : name;
    if (n.type === 'ImportDeclaration') { m.imports.push(n.source.value); return; }
    if (n.type === 'ExportNamedDeclaration' || n.type === 'ExportDefaultDeclaration') { walk(n.declaration, scope, true); return; }
    if (n.type === 'FunctionDeclaration') { operation(n, qualified(n.id.name), scope, exported); return; }
    if (n.type === 'VariableDeclarator') {
      if (n.id.type === 'Identifier' && fn(n.init)) { operation(n.init, qualified(n.id.name), scope, exported); return; }
      const hook = n.init?.type === 'CallExpression' && /^(useState|useReducer|useRef)$/.test(n.init.callee.name || '');
      if (!scope || hook) {
        const names = n.id.type === 'ArrayPattern' && hook ? n.id.elements.slice(0, 1).filter(Boolean).map(text) : [text(n.id)];
        for (const name of names) m.attributes.push({ name: qualified(name), type: 'dynamic', visibility: exported ? 'public' : 'private',
          static: !scope, readonly: !hook && n._constant === true, initial: text(n.init), state: hook });
      }
      // Name functions in object literals and useMemo return objects, retaining lexical scope.
      walk(n.init, qualified(text(n.id)), exported); return;
    }
    if ((n.type === 'ObjectProperty' && fn(n.value)) || n.type === 'ObjectMethod') {
      operation(n.type === 'ObjectMethod' ? n : n.value, qualified(n.key.name || text(n.key)), scope, exported); return;
    }
    if (n.type === 'VariableDeclaration') {
      for (const d of n.declarations) { d._constant = n.kind === 'const'; walk(d, scope, exported); } return;
    }
    // Anonymous callbacks have no invented UML operation; traverse them to find named declarations.
    for (const [key, value] of Object.entries(n)) {
      if (['loc', 'start', 'end', 'extra', '_constant'].includes(key)) continue;
      if (Array.isArray(value)) value.forEach(v => walk(v, scope, exported));
      else if (value && typeof value === 'object') walk(value, scope, exported);
    }
  }
  walk(ast.program);
  return m;
});
process.stdout.write(JSON.stringify(result));
