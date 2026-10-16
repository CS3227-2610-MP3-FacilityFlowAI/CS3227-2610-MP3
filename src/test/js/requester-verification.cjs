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
    await waitFor(`location.pathname === ${JSON.stringify(route.split('?')[0])} && document.readyState === 'complete'`);
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
    const result = await evaluate(`(()=>{
        const visible=e=>e.getClientRects().length>0;
        const fields=[...document.querySelectorAll('main input:not([type=hidden]),main textarea,main select')].filter(visible);
        return {missing:fields.filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.id||e.name),
            overflow:document.documentElement.scrollWidth>innerWidth+1};
    })()`);
    check(name + ': visible inputs have labels', result.missing.length === 0);
    check(name + ': no page-level horizontal overflow', !result.overflow);
}
async function screenshot(name) {
    const result = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.writeFileSync(path.join(output, name + '.png'), Buffer.from(result.data, 'base64'));
}
async function viewportChecks(name) {
    for (const [width, height] of [[760, 600], [1024, 700], [1440, 900]]) {
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
        await labelsAndLayout(`${name} ${width}x${height}`);
    }
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
    await navigate('/login'); await labelsAndLayout('login');
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
    for (const [width, height] of [[760, 600], [1024, 700], [1440, 900]]) {
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
        await labelsAndLayout(`create ${width}x${height}`); await screenshot(`create-${width}x${height}`);
        report.viewports.push({ width, height });
    }
    await fill('#title', 'Tiny'); await fill('#description', 'Retain these manual details'); await fill('#location', 'Block A');
    await evaluate(`document.querySelector('#categoryId').selectedIndex=1`);
    await click('[data-request-form] button[type=submit]');
    await waitFor(`!!document.querySelector('#title-error')`);
    check('server field error retains values and is associated', await evaluate(`document.querySelector('#title').value==='Tiny'&&document.querySelector('#title').getAttribute('aria-describedby')==='title-error'`));
    await labelsAndLayout('invalid create');
    await fill('#title', 'Manual light report'); await fill('#roughText', 'Light flickers in room A');
    await waitFor(`!document.querySelector('[data-ask-assistant]').hidden`);
    await click('[data-ask-assistant]');
    await waitFor(`!document.querySelector('[data-assistant-review]').hidden`);
    check('suggestion is separate and focuses review heading', await evaluate(`document.querySelector('#title').value==='Manual light report'&&document.activeElement.id==='suggestions-heading'`));
    await evaluate(`document.querySelector('[data-suggested-fields] button').focus()`); await key('Enter', 13);
    check('keyboard accepts only title into editable form', await evaluate(`document.querySelector('#title').value==='Flickering light'&&document.querySelector('#description').value==='Retain these manual details'&&document.activeElement.id==='title'`));
    const focus = await evaluate(`(()=>{let s=getComputedStyle(document.activeElement);return {width:s.outlineWidth,style:s.outlineStyle,color:s.outlineColor}})()`);
    check('visible keyboard focus outline', parseFloat(focus.width) >= 3 && focus.style !== 'none');
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
    await fill('#currentPassword', password); await fill('#newPassword', 'ChangedSynthetic123');
    await click('main button[type=submit]'); await waitFor(`!!document.querySelector('main [role=status]')`);
    check('own password change retains session', (await evaluate('document.querySelector("main").textContent')).includes('remain signed in'));
    await navigate('/requester'); await click('header button[type=submit]');
    await waitFor(`location.pathname==='/login'`);
    check('logout returns to login', true);
    // Verify the shipped palette rather than claiming an automated WCAG certification.
    const luminance = hex => {
        const rgb = hex.match(/../g).map(x => parseInt(x, 16) / 255).map(x => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    };
    report.contrast = Object.fromEntries(['17212b', '174f91', '9d1818'].map(color => [color, 1.05 / (luminance(color) + .05)]));
    check('body/link/error palette contrast at least 4.5 to white', Object.values(report.contrast).every(value => value >= 4.5));
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
        if (socket?.readyState === WebSocket.OPEN) {
            const state = await evaluate(`({route:location.pathname,ready:document.readyState,active:document.activeElement.tagName+':'+(document.activeElement.id||document.activeElement.type),alert:document.querySelector('[role=alert]')?.textContent,assistantStatus:document.querySelector('[data-assistant-status]')?.textContent,askHidden:document.querySelector('[data-ask-assistant]')?.hidden,valid:[...document.forms].map(f=>f.checkValidity())})`).catch(() => ({}));
            fs.writeFileSync(path.join(output, 'failure.json'), JSON.stringify({ error: error.message, state, completed: report.checks }, null, 2));
            await screenshot('failure').catch(() => {});
        }
        throw error;
    }
    finally { if (socket?.readyState === WebSocket.OPEN) { try { await command('Browser.close'); } catch {} socket.close(); } if (chrome && chrome.exitCode === null) chrome.kill(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
