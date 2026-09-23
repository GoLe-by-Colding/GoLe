const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('../../../apps/mobile/node_modules/typescript');
const source = fs.readFileSync(path.join(__dirname, '../../../apps/mobile/src/views/web/model/navigation.ts'), 'utf8');
const exportsObject = {};
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { exports: exportsObject, URL });
const { resolveWebOrigin, navigationTarget } = exportsObject;
assert.equal(resolveWebOrigin(undefined, true, false), 'http://localhost:3000');
assert.equal(resolveWebOrigin(undefined, true, true), 'http://10.0.2.2:3000');
assert.equal(resolveWebOrigin(undefined, false, false), 'https://gole.co.kr');
assert.equal(resolveWebOrigin('https://gole.co.kr/path', false, false), 'https://gole.co.kr');
for (const url of ['http://gole.co.kr', 'javascript:alert(1)', 'file:///tmp/a', 'https://user:secret@gole.co.kr']) {
  assert.throws(() => resolveWebOrigin(url, false, false));
}
const origin = 'https://gole.co.kr';
for (const url of [origin, origin + '/search?q=brick', origin + '/login?returnTo=%2Fprofile']) {
  assert.equal(navigationTarget(url, origin), 'internal');
}
for (const url of ['https://gole.co.kr.evil.test', 'https://evil.test/?x=https://gole.co.kr', 'https://gole.co.kr:444', 'mailto:support@gole.co.kr', 'tel:01012345678']) {
  assert.equal(navigationTarget(url, origin), 'external');
}
for (const url of ['javascript:alert(1)', 'file:///tmp/a', 'data:text/html,a', 'intent://x', '//evil.test', 'https://gole.co.kr@evil.test', 'not a url']) {
  assert.equal(navigationTarget(url, origin), 'blocked');
}
console.log('PASS: 23 mobile WebView origin/navigation assertions');
