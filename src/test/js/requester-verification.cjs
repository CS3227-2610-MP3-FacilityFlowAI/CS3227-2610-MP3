// Step 7: actual Chrome + loopback HTTP load, driven only against the disposable Java fixture.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { setTimeout: pause } = require('node:timers/promises');
const [base, foreignId, poolSize] = process.argv.slice(2);
assert.match(base || '', /^http:\/\/127\.0\.0\.1:\d+$/);
assert.match(foreignId || '', /^FF-\d{6}$/);
const output = path.resolve('build/verification/requester');
fs.mkdirSync(output, { recursive: true });
const report = { date: new Date().toISOString(), timezone: 'Asia/Singapore', checks: [], viewports: [] };
const check = (name, value) => { assert.ok(value, name); report.checks.push(name); };
const password = 'Synthetic123'; // Disposable synthetic accounts only, never real credentials.
let chrome;
let socket;
let sequence = 0;
const pending = new Map();
function command(method, params = {}) {
    const id = ++sequence;
    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
        pending.set(id, { resolve: value => { clearTimeout(timeout); resolve(value); }, reject });
        socket.send(JSON.stringify({ id, method, params }));
    });
}
async function evaluate(expression) {
    const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw new Error('Browser evaluation failed: ' + (result.exceptionDetails.exception?.description || result.exceptionDetails.text));
    return result.result.value;
}
async function waitFor(expression) {
    for (let i = 0; i < 100; i++) { if (await evaluate(`document.readyState==='complete'&&(${expression})`)) return; await pause(100); }
    throw new Error('Browser condition not reached: ' + expression);
}
async function navigate(route) {
    await command('Page.navigate', { url: base + route });
    const requestedPath = route.split('?')[0];
    const expectedPath = requestedPath === '/manager' ? '/manager/overview' : requestedPath;
    await waitFor(`location.pathname === ${JSON.stringify(expectedPath)} && document.readyState === 'complete'`);
}
async function fill(selector, value) {
    await evaluate(`(()=>{const input=document.querySelector(${JSON.stringify(selector)});input.focus();input.value=${JSON.stringify(value)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));})()`);
}
async function click(selector) { await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`); }
async function key(name, code) {
    await command('Input.dispatchKeyEvent', { type: 'keyDown', key: name, code: name, windowsVirtualKeyCode: code, nativeVirtualKeyCode: code,
        ...(name === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) });
    await command('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code: name, windowsVirtualKeyCode: code });
}
async function labelsAndLayout(name) {
    check(name + ': shared responsive stylesheet loaded', await evaluate(`(()=>{const link=document.querySelector('link[href*="/css/workspace.css"]');return !!link&&!!link.sheet})()`));
    const result = await evaluate(`(()=>{
        const visible=e=>e.getClientRects().length>0;
        const fields=[...document.querySelectorAll('main input:not([type=hidden]),main textarea,main select')].filter(visible);
        return {missing:fields.filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.id||e.name),
            unassociated:fields.filter(e=>e.getAttribute('aria-invalid')==='true'&&!(e.getAttribute('aria-describedby')||'').trim().split(' ').some(id=>document.getElementById(id)?.textContent.trim())).map(e=>e.id||e.name),
            overflow:document.documentElement.scrollWidth>innerWidth+1};
    })()`);
    check(name + ': visible inputs have labels (NFR-A11Y-001)', result.missing.length === 0);
    check(name + ': invalid fields reference error text (NFR-A11Y-001)', result.unassociated.length === 0);
    check(name + ': no page-level horizontal overflow (NFR-A11Y-002)', !result.overflow);
}
async function fieldErrors(name, invalid, valid = []) {
    const result = await evaluate(`(()=>{
        const invalid=${JSON.stringify(invalid)},valid=${JSON.stringify(valid)};
        const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);
        return {unique:ids.length===new Set(ids).size,
            missing:invalid.filter(selector=>{const control=document.querySelector(selector),error=document.getElementById(control?.id+'-error');
                return !control||control.getAttribute('aria-invalid')!=='true'||control.getAttribute('aria-describedby')!==error?.id||
                    !error?.textContent.trim()||!error.getClientRects().length||!control.form?.contains(error);}),
            stale:valid.filter(selector=>{const control=document.querySelector(selector);return !control||control.getAttribute('aria-invalid')==='true'||control.hasAttribute('aria-describedby');})};
    })()`);
    check(name + ': unique field/error IDs', result.unique);
    check(name + ': invalid controls reference visible errors in their own form (UIX-006–008/NFR-A11Y-001)', result.missing.length === 0);
    check(name + ': valid and unrelated controls have no error metadata', result.stale.length === 0);
    await labelsAndLayout(name);
}
async function paletteChecks() {
    const colors = await evaluate(`(()=>{
        const sample=document.createElement('div');sample.style.cssText='position:fixed;left:-10000px;top:0';
        const error=document.createElement('span');error.className='error';error.textContent='error';
        const button=document.createElement('button');button.className='button-primary';button.textContent='action';
        sample.append(error,button);document.body.append(sample);
        const style=e=>getComputedStyle(e), root=style(document.documentElement), body=style(document.body), focus=style(document.activeElement);
        const value={ink:body.color,page:body.backgroundColor,muted:root.getPropertyValue('--muted').trim(),surface:root.getPropertyValue('--surface').trim(),
            link:style(document.querySelector('a')).color,error:error?style(error).color:'',primaryText:style(button).color,primaryBackground:style(button).backgroundColor,
            focusColor:focus.outlineColor,focusWidth:focus.outlineWidth,focusStyle:focus.outlineStyle,
            focusOffset:focus.outlineOffset,focusTag:document.activeElement.tagName,brand:root.getPropertyValue('--brand').trim()};
        sample.remove();return value;
    })()`);
    const rgb = color => {
        if (color.startsWith('#')) {
            const hex = color.slice(1);
            return (hex.length === 3 ? [...hex].map(value => value + value).join('') : hex).match(/../g).map(value => parseInt(value, 16));
        }
        return color.match(/[\d.]+/g).slice(0, 3).map(Number);
    };
    check('contrast parser handles rgb, #rgb and #rrggbb', rgb('rgb(32, 90, 197)').join(',') === '32,90,197' &&
        rgb('#fff').join(',') === '255,255,255' && rgb('#205ac5').join(',') === '32,90,197');
    const luminance = color => rgb(color).map(value => value / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4)
        .reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0);
    const ratio = (foreground, background) => {
        const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
        return (values[0] + .05) / (values[1] + .05);
    };
    report.contrast = { ink: ratio(colors.ink, colors.page), muted: ratio(colors.muted, colors.surface), link: ratio(colors.link, colors.page),
        error: ratio(colors.error, colors.page), primary: ratio(colors.primaryText, colors.primaryBackground) };
    check(`rendered shared palette meets 4.5:1 text contrast (NFR-A11Y-001); measured ${JSON.stringify(report.contrast)}`,
        Object.values(report.contrast).every(value => value >= 4.5));
    const brand = await evaluate(`(()=>{const e=document.createElement('span');e.style.color='var(--brand)';document.body.append(e);const color=getComputedStyle(e).color;e.remove();return color})()`);
    const compactFocus = ['INPUT', 'SELECT', 'TEXTAREA'].includes(colors.focusTag);
    check('keyboard focus uses the approved shared brand outline (NFR-A11Y-001)',
        parseFloat(colors.focusWidth) >= (compactFocus ? 2 : 3) &&
        parseFloat(colors.focusOffset) >= 1 && colors.focusStyle !== 'none' && colors.focusColor === brand);
}
async function screenshot(name) {
    const result = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.writeFileSync(path.join(output, name + '.png'), Buffer.from(result.data, 'base64'));
}
async function viewportChecks(name) {
    for (const [width, height] of [[360, 800], [760, 600], [1024, 700], [1440, 900]]) {
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
        await labelsAndLayout(`${name} ${width}x${height}`);
    }
}
async function loginAs(username, workspace) {
    await navigate('/login'); await fill('#username', username); await fill('#password', password); await key('Tab', 9); await key('Enter', 13);
    await waitFor(`location.pathname===${JSON.stringify(workspace)}`);
}
async function browserChecks() {
    const executable = process.env.CHROME_PATH || ['C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'].find(fs.existsSync);
    assert.ok(executable && fs.existsSync(executable), 'Set CHROME_PATH to an installed desktop Chrome executable');
    const profile = fs.mkdtempSync(path.join(output, 'chrome-profile-'));
    chrome = spawn(executable, ['--headless=new', '--remote-debugging-port=0', '--remote-debugging-address=127.0.0.1',
        `--user-data-dir=${profile}`, '--no-first-run', '--disable-background-networking', '--disable-component-update',
        '--disable-sync', '--disable-default-apps', 'about:blank'], { windowsHide: true, stdio: 'ignore' });
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; i < 100 && !fs.existsSync(portFile); i++) await pause(100);
    assert.ok(fs.existsSync(portFile), 'Chrome debugging port available');
    const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
    const debugging = `http://127.0.0.1:${port}`;
    const version = await (await fetch(debugging + '/json/version')).json();
    report.browser = version.Browser;
    const page = await (await fetch(debugging + '/json/new?about:blank', { method: 'PUT' })).json();
    socket = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
    socket.addEventListener('message', event => {
        const message = JSON.parse(event.data); const callback = pending.get(message.id);
        if (callback) { pending.delete(message.id); message.error ? callback.reject(new Error(message.error.message)) : callback.resolve(message.result); }
    });
    await command('Page.enable'); await command('Runtime.enable');
    await command('Emulation.setDeviceMetricsOverride', { width: 1024, height: 700, deviceScaleFactor: 1, mobile: false });
    await navigate('/login'); await viewportChecks('login');
    await fill('#username', 'requester1'); await key('Tab', 9);
    check('login keyboard Tab reaches password', await evaluate(`document.activeElement.id==='password'`));
    await fill('#password', 'wrong-password'); await key('Tab', 9); await key('Enter', 13);
    await waitFor(`!!document.querySelector('[role=alert]')`);
    check('invalid login gives generic alert', (await evaluate(`document.querySelector('[role=alert]').textContent`)).includes('Invalid username or password'));
    await fill('#username', 'requester1'); await fill('#password', password); await key('Tab', 9); await key('Enter', 13);
    await waitFor(`location.pathname==='/requester'&&document.querySelector('.status-counts')`);
    check('role redirect and six dashboard states', await evaluate(`document.querySelectorAll('.status-counts > div').length===6`));
    await viewportChecks('dashboard');
    await navigate('/requester/requests/new');
    for (const [width, height] of [[360, 800], [760, 600], [1024, 700], [1440, 900]]) {
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
        await labelsAndLayout(`create ${width}x${height}`); await screenshot(`create-${width}x${height}`);
        report.viewports.push({ width, height });
    }
    check('assistant review stays hidden before use', await evaluate(`document.querySelector('[data-assistant-review]').hidden&&document.querySelector('[data-assistant-review]').getClientRects().length===0`));
    await fill('#title', 'Tiny'); await fill('#description', 'Retain these manual details'); await fill('#location', 'Block A');
    await evaluate(`document.querySelector('#categoryId').selectedIndex=1`);
    await click('[data-request-form] button[type=submit]');
    await waitFor(`!!document.querySelector('#title-error')`);
    check('server field error retains values and is associated', await evaluate(`document.querySelector('#title').value==='Tiny'&&document.querySelector('#title').getAttribute('aria-describedby')==='title-error'`));
    await labelsAndLayout('invalid create');
    await fill('#title', 'Manual light report'); await fill('#roughText', 'Light flickers in room A');
    await waitFor(`!document.querySelector('[data-ask-assistant]').hidden`);
    for (const [width, height] of [[360, 800], [760, 600], [1024, 700], [1440, 900]]) {
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
        await labelsAndLayout(`unsaved requester input ${width}x${height}`);
        check(`resize preserves requester input ${width}x${height}`, await evaluate(`document.querySelector('#title').value==='Manual light report'&&document.querySelector('#description').value==='Retain these manual details'&&document.querySelector('#location').value==='Block A'&&document.querySelector('#roughText').value==='Light flickers in room A'`));
        check(`resize preserves hidden assistant review ${width}x${height}`, await evaluate(`document.querySelector('[data-assistant-review]').hidden&&document.querySelector('[data-assistant-review]').getClientRects().length===0`));
    }
    await click('[data-ask-assistant]');
    await waitFor(`!document.querySelector('[data-assistant-review]').hidden`);
    check('suggestion is separate and focuses review heading', await evaluate(`document.querySelector('#title').value==='Manual light report'&&document.activeElement.id==='suggestions-heading'`));
    await evaluate(`document.querySelector('[data-suggested-fields] button').focus()`); await key('Enter', 13);
    check('keyboard accepts only title into editable form', await evaluate(`document.querySelector('#title').value==='Flickering light'&&document.querySelector('#description').value==='Retain these manual details'&&document.activeElement.id==='title'`));
    const focus = await evaluate(`(()=>{let s=getComputedStyle(document.activeElement);return {width:s.outlineWidth,style:s.outlineStyle,color:s.outlineColor,offset:s.outlineOffset,tag:document.activeElement.tagName}})()`);
    const compactFocus = ['INPUT', 'SELECT', 'TEXTAREA'].includes(focus.tag);
    check('visible keyboard focus outline', parseFloat(focus.width) >= (compactFocus ? 2 : 3) &&
        parseFloat(focus.offset) >= 1 && focus.style !== 'none');
    await paletteChecks();
    await screenshot('ai-review');
    await click('[data-discard-suggestions]');
    check('discard keeps accepted manual text', await evaluate(`document.querySelector('[data-assistant-review]').hidden&&document.querySelector('#title').value==='Flickering light'`));
    await fill('#title', 'Browser verified light report'); await click('[data-request-form] button[type=submit]');
    await waitFor(`/^\\/requester\\/requests\\/FF-\\d{6}$/.test(location.pathname)`);
    const detail = await evaluate('location.pathname'); report.createdRequest = detail.split('/').pop();
    await labelsAndLayout('detail');
    await viewportChecks('detail');
    await navigate(detail + '/edit'); await fill('#location', 'Updated Block A');
    await click('[data-request-form] button[type=submit]'); await waitFor(`location.pathname===${JSON.stringify(detail)}`);
    check('ordinary edit persisted', (await evaluate('document.querySelector("main").textContent')).includes('Updated Block A'));
    await navigate(detail + '/updates/new'); await viewportChecks('update'); await fill('#text', 'Browser follow-up note');
    await click('[data-request-form] button[type=submit]'); await waitFor(`location.pathname===${JSON.stringify(detail)}`);
    check('requester update persisted', (await evaluate('document.querySelector("main").textContent')).includes('Browser follow-up note'));
    await navigate(detail + '/cancel');
    await viewportChecks('cancel');
    check('cancellation defaults to Keep', await evaluate(`document.querySelector('[data-request-form] button[type=submit]').value==='keep'`));
    await click('button[value=keep]'); await waitFor(`location.pathname===${JSON.stringify(detail)}`);
    await navigate(detail + '/cancel'); await fill('#text', 'No longer required after review');
    await click('input[name=confirmed]'); await click('button[value=cancel]');
    await waitFor(`location.pathname===${JSON.stringify(detail)}`);
    check('explicit cancellation persisted', (await evaluate('document.querySelector("main").textContent')).includes('CANCELLED'));
    await screenshot('cancelled-detail');
    await navigate('/requester/requests?search=does-not-match');
    await viewportChecks('list');
    check('zero-match guidance', (await evaluate('document.querySelector("main").textContent')).includes('No requests match'));
    await navigate('/requester/requests/' + foreignId);
    check('foreign data absent from error page', !(await evaluate('document.body.textContent')).includes('FOREIGN_BROWSER_CANARY'));
    await navigate('/account/password'); await labelsAndLayout('password');
    await viewportChecks('password');
    await fill('#currentPassword', password); await fill('#newPassword', 'ChangedSynthetic123'); await fill('#confirmPassword', 'ChangedSynthetic123');
    await click('main button[type=submit]'); await waitFor(`!!document.querySelector('main [role=status]')`);
    check('own password change retains session', (await evaluate('document.querySelector("main").textContent')).includes('Your password was changed.'));
    await navigate('/requester'); await click('header button[type=submit]');
    await waitFor(`location.pathname==='/login'`);
    check('logout returns to login', true);
    await loginAs('manager1', '/manager/overview');
    await command('Page.navigate', { url: base + '/manager' });
    await waitFor(`location.pathname==='/manager/overview'&&document.readyState==='complete'`);
    check('Manager landing opens complete navigation (REQ-SH-001/012)', await evaluate(`
        document.querySelector('nav[aria-label="Manager navigation"] a[href="/manager/accounts"]') &&
        document.querySelector('nav[aria-label="Manager navigation"] a[href="/manager/categories"]') &&
        document.querySelector('nav[aria-label="Manager navigation"] a[href="/manager/skills"]')`));
    await viewportChecks('Manager overview');
    check('Manager overview shows status, priority and workload sections (REQ-MGR-010)', await evaluate(`document.querySelector('#status-heading')&&document.querySelector('#priority-heading')&&document.querySelector('#workload-heading')`));
    await navigate('/manager/requests?status=INVALID'); await labelsAndLayout('Manager invalid status filter');
    check('Manager invalid status shows its specific validation message (REQ-MGR-001)', await evaluate(`document.querySelector('main').textContent.includes('Choose a listed status.')`));
    await fieldErrors('Manager status filter error', ['#status'], ['#categoryId']);
    await navigate('/manager/requests?search=' + 'x'.repeat(121) + '&createdFrom=2026-10-20&createdTo=2026-10-19');
    await fieldErrors('Manager search and date filter errors', ['#search', '#createdTo'], ['#status', '#createdFrom']);
    await navigate('/manager/requests');
    await fieldErrors('Manager reset filters', [], ['#status', '#search', '#createdTo']);
    for (const [width, height] of [[360, 800], [760, 600], [1024, 700], [1440, 900]]) {
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
        await labelsAndLayout(`Manager request table ${width}x${height}`);
        if (width <= 760) check(`Manager table stays inside scroll container ${width}x${height}`, await evaluate(`(()=>{const table=document.querySelector('table');const wrapper=table?.parentElement;return !!table&&!!wrapper&&getComputedStyle(wrapper).overflowX==='auto'&&document.documentElement.scrollWidth<=innerWidth+1})()`));
    }
    check('Manager request queue renders rows (REQ-MGR-001)', await evaluate(`document.querySelectorAll('table tbody tr').length>0`));
    const managerDetail = await evaluate(`[...document.querySelectorAll('table tbody tr')].find(row=>[...row.cells].some(cell=>cell.textContent.trim()==='OPEN'))?.querySelector('a[href^="/manager/requests/"]')?.getAttribute('href')`);
    assert.ok(managerDetail, 'Manager request detail link exists');
    await navigate(managerDetail); await viewportChecks('Manager request detail');
    check('Manager request detail renders (REQ-MGR-001)', await evaluate(`document.querySelector('main h1')&&document.querySelector('#request-details')`));
    // Bypass client constraints only to exercise server error responses on synthetic data.
    await evaluate(`document.querySelector('#assign-technician').form.submit()`);
    await waitFor(`!!document.querySelector('#assign-technician-error')`);
    await fieldErrors('Manager assignment errors', ['#assign-technician', '#assign-priority'], ['#correct-priority', '#cancel-reason']);
    await fill('#cancel-reason', 'tiny');
    await evaluate(`document.querySelector('#cancel-reason').form.submit()`);
    await waitFor(`!!document.querySelector('#cancel-confirmed-error')`);
    await fieldErrors('Manager cancellation errors', ['#cancel-reason', '#cancel-confirmed'], ['#correct-reason', '#assign-technician']);
    await evaluate(`document.querySelector('.edit-panel').open=true`);
    await fill('#correct-title', 'Tiny'); await fill('#correct-description', 'short'); await fill('#correct-reason', 'tiny');
    await evaluate(`document.querySelector('#correct-title').form.submit()`);
    await waitFor(`!!document.querySelector('#correct-title-error')`);
    await fieldErrors('Manager correction errors', ['#correct-title', '#correct-description', '#correct-reason'], ['#cancel-reason', '#assign-technician']);
    check('invalid correction preserves entered title', await evaluate(`document.querySelector('#correct-title').value==='Tiny'`));
    await navigate('/manager/accounts'); await viewportChecks('Manager account administration');
    check('Manager account administration page is readable (REQ-MGR-013)', await evaluate(`document.querySelector('h1')?.textContent.includes('Account administration')`));
    await navigate('/manager/audit'); await viewportChecks('Manager audit viewer');
    check('Manager audit viewer renders read-only filters (REQ-MGR-011)', await evaluate(`document.querySelector('h1')?.textContent.includes('Audit viewer')&&document.querySelector('form[method=get]')&&!document.querySelector('main form[method=post]')`));
    await navigate('/manager/audit?actor=' + 'x'.repeat(81) + '&occurredFrom=2026-10-20&occurredTo=2026-10-19');
    await fieldErrors('Manager audit filter errors', ['#actor', '#occurredTo'], ['#action', '#occurredFrom']);
    await navigate('/manager/requests/new'); await viewportChecks('Manager record request form');
    check('Manager record request form renders (REQ-MGR-003)', await evaluate(`document.querySelector('h1')?.textContent.includes('Record a request on behalf')&&document.querySelector('#requesterId')`));
    await fieldErrors('Manager valid create form', [], ['#requesterId', '#title', '#description', '#categoryId']);
    await fill('#title', 'Tiny'); await fill('#description', 'short'); await fill('#location', 'Retained location');
    await evaluate(`document.querySelector('#title').form.submit()`);
    await waitFor(`!!document.querySelector('#title-error')`);
    await fieldErrors('Manager create errors', ['#requesterId', '#title', '#description', '#categoryId'], ['#location', '#reportedUrgency']);
    check('Manager invalid create retains valid location', await evaluate(`document.querySelector('#location').value==='Retained location'`));
    await navigate('/manager/categories'); await viewportChecks('Manager category catalogue');
    check('Manager category catalogue renders (REQ-MGR-016/017)', await evaluate(`document.querySelector('h1')?.textContent.includes('Category catalogue')`));
    await navigate('/manager/skills'); await viewportChecks('Manager skill catalogue');
    check('Manager skill catalogue renders (REQ-MGR-018/019)', await evaluate(`document.querySelector('h1')?.textContent.includes('Skill catalogue')`));
    await navigate('/manager'); await click('header button[type=submit]'); await waitFor(`location.pathname==='/login'`);
    await loginAs('technician1', '/technician');
    await navigate('/technician/requests'); await viewportChecks('Technician queue');
    check('Technician queue is visible and read-only (REQ-TECH-001/002/011)', await evaluate(`document.querySelector('#queue-heading')&&document.querySelector('#q')&&document.querySelectorAll('main form[method=post]').length===0`));
    console.log('Chrome critical-flow checks passed');
}

function cookie(response, previous = '') {
    const session = response.headers.getSetCookie().find(value => value.startsWith('SESSION='));
    return session ? session.split(';')[0] : previous;
}
async function login(username) {
    const page = await fetch(base + '/login'); const html = await page.text();
    const token = html.match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
    const before = cookie(page);
    const response = await fetch(base + '/login', { method: 'POST', redirect: 'manual', headers: { Cookie: before },
        body: new URLSearchParams({ username, password, _csrf: token }) });
    assert.equal(response.status, 302); assert.equal(new URL(response.headers.get('location'), base).pathname, '/requester');
    return cookie(response, before);
}
async function loadChecks() {
    const sessions = [];
    // Authenticate before timing; passwords/Argon2 and AI are outside non-AI page timings.
    for (let i = 0; i < 50; i++) sessions.push(await login('load' + i));
    console.log('50 distinct authenticated load accounts ready');
    const get = async (route, session) => {
        const start = performance.now(); const response = await fetch(base + route, { headers: { Cookie: session }, redirect: 'manual' });
        const text = await response.text(); assert.equal(response.status, 200, route);
        return { duration: performance.now() - start, text };
    };
    const ids = await Promise.all(sessions.map(async session => {
        const result = await get('/requester/requests', session);
        return result.text.match(/\/requester\/requests\/(FF-\d{6})/)[1];
    }));
    await Promise.all(sessions.map(session => get('/requester', session)));
    const timings = [];
    await Promise.all(sessions.map(async (session, i) => {
        for (const route of ['/requester', '/requester/requests', '/requester/requests?status=OPEN', '/requester/requests/' + ids[i],
            '/requester/requests?search=Synthetic', '/about', '/account/password', '/requester']) {
            const result = await get(route, session); timings.push(result.duration);
        }
    }));
    timings.sort((a, b) => a - b);
    report.load = { users: 50, requests: timings.length, p50Ms: timings[Math.ceil(timings.length * .5) - 1],
        p95Ms: timings[Math.ceil(timings.length * .95) - 1], maxMs: timings.at(-1), errors: 0,
        maximumPoolSize: Number(poolSize),
        measurement: 'Loopback end-to-end including body/network; warmup/login/AI excluded; 50 distinct concurrent users' };
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(report, null, 2));
    check('local 50-user non-AI p95 at most one second', report.load.p95Ms <= 1000);
    console.log('Local 50-user measurements:', JSON.stringify(report.load));
}
(async () => {
    try { await browserChecks(); await loadChecks(); fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(report, null, 2)); }
    catch (error) {
        const failure = { error: error.message, completed: report.checks };
        if (socket?.readyState === WebSocket.OPEN) {
            const state = await evaluate(`({route:location.pathname,ready:document.readyState,active:document.activeElement.tagName+':'+(document.activeElement.id||document.activeElement.type),alert:document.querySelector('[role=alert]')?.textContent,assistantStatus:document.querySelector('[data-assistant-status]')?.textContent,askHidden:document.querySelector('[data-ask-assistant]')?.hidden,valid:[...document.forms].map(f=>f.checkValidity())})`).catch(() => ({}));
            failure.state = state;
            await screenshot('failure').catch(() => {});
        }
        report.failure = failure;
        fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(report, null, 2));
        fs.writeFileSync(path.join(output, 'failure.json'), JSON.stringify(failure, null, 2));
        throw error;
    }
    finally { if (socket?.readyState === WebSocket.OPEN) { try { await command('Browser.close'); } catch {} socket.close(); } if (chrome && chrome.exitCode === null) chrome.kill(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
