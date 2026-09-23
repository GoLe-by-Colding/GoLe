const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('../../../apps/web/node_modules/typescript');
const React = require('../../../apps/web/node_modules/react');
const { renderToStaticMarkup } = require('../../../apps/web/node_modules/react-dom/server');
const jsx = require('../../../apps/web/node_modules/react/jsx-runtime');
function load(file, imports) {
  const exports = {};
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../../../apps/web/src/shared/ui/media-image/', file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  vm.runInNewContext(code, { exports, require: (name) => {
    if (name === 'react/jsx-runtime') return jsx;
    if (name === 'react') return React;
    if (name in imports) return imports[name];
    throw Error('Unexpected dependency: ' + name);
  } });
  return exports;
}
const brick = load('brick-placeholder.tsx', {});
const { MediaImage } = load('media-image.tsx', {
  './brick-placeholder': brick,
  '@shared/config': { env: { publicApiBaseUrl: 'https://api.example.test' } },
  '@shared/lib': { cn: (...values) => values.filter(Boolean).join(' ') },
});
const render = (props) => renderToStaticMarkup(React.createElement(MediaImage, props));
for (const src of [null, undefined, '']) {
  const html = render({ src, alt: '브릭 매물', className: 'h-20 w-20' });
  assert.match(html, /data-brick-placeholder/);
  assert.match(html, /aria-label="브릭 매물"/);
  assert.doesNotMatch(html, /<img/);
}
const real = render({ src: '/api/images/example.png', alt: '실제 사진' });
assert.match(real, /src="https:\/\/api.example.test\/api\/images\/example.png"/);
assert.doesNotMatch(real, /data-brick-placeholder/);
const custom = render({ src: null, alt: 'SET', fallback: React.createElement('span', null, '#10307') });
assert.match(custom, /class="sr-only"><span>#10307/);
assert.match(custom, /data-brick-placeholder/);
const malicious = render({ src: null, alt: '<script>alert(1)</script>' });
assert.doesNotMatch(malicious, /<script>/);
const variants = new Set();
for (const seed of ['a', 'b', 'c']) {
  const html = render({ src: null, alt: seed });
  assert.equal(html, render({ src: null, alt: seed }));
  variants.add(html.match(/data-brick-placeholder="(\d)"/)[1]);
}
assert.equal(variants.size, 3);
console.log('PASS: common placeholder SSR, original images, accessibility, escaping, 3 deterministic variants');
