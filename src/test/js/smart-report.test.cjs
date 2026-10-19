// REQ-RQ-015–017 / AI-SEC-013/014/016/028: deterministic DOM checks, no provider or browser dependency.
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const source = readFileSync('src/main/resources/static/js/smart-report.js', 'utf8');
class Node {
    constructor() { this.children = []; this.listeners = {}; this.dataset = {}; this.value = ''; this.textContent = ''; this.hidden = false; }
    addEventListener(name, callback) { (this.listeners[name] ||= []).push(callback); }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(name, value) { this[name] = value; }
    removeAttribute(name) { delete this[name]; }
    dispatchEvent(event) { for (const listener of this.listeners[event.type] || []) listener(event); }
    focus() { this.focused = true; }
    async click() { for (const listener of this.listeners.click || []) await listener(); }
}
function fixture(provider) {
    const form = new Node();
    const fields = Object.fromEntries(['title', 'description', 'location', 'categoryId', 'reportedUrgency', '_csrf', 'version'].map(name => [name, new Node()]));
    fields._csrf.value = 'synthetic-csrf'; fields.version.value = '4'; fields.reportedUrgency.value = 'NORMAL';
    fields.categoryId.options = [{ value: '', textContent: 'Choose a category' }, { value: 'electrical-id', textContent: 'Electrical' }];
    fields.reportedUrgency.options = ['LOW', 'NORMAL', 'HIGH', 'EMERGENCY'].map(value => ({ value, textContent: value }));
    form.elements = { namedItem: name => fields[name] };
    const panel = new Node(); panel.dataset.endpoint = '/requester/requests/FF-000001/assistant';
    const nodes = {};
    for (const name of ['rough-text', 'ask-assistant', 'discard-suggestions', 'assistant-status', 'assistant-review', 'suggested-fields', 'assistant-questions', 'assistant-rationale']) {
        nodes[name] = new Node();
    }
    nodes['assistant-review'].hidden = true;
    const heading = new Node();
    panel.querySelector = selector => selector === '#suggestions-heading' ? heading : nodes[selector.slice(6, -1)];
    const window = new Node();
    const calls = [];
    runInNewContext(source, {
        document: { querySelectorAll: () => [panel], querySelector: () => form, createElement: () => new Node() },
        window, URLSearchParams, AbortController, setTimeout, clearTimeout,
        Event: class { constructor(type) { this.type = type; } },
        fetch: async (...args) => { calls.push(args); return provider(...args); },
    });
    return { form, fields, panel, nodes, window, calls, heading, ask: () => nodes['ask-assistant'].click(), discard: () => nodes['discard-suggestions'].click() };
}
const success = (suggestion = {}) => ({ ok: true, json: async () => ({ status: 'SUCCEEDED', suggestion: {
    draftTitle: 'Flickering light', draftDescription: 'The light keeps flickering.', draftLocation: 'Block A',
    suggestedCategory: 'Electrical', suggestedUrgency: 'High', missingInformationQuestions: ['Which room?'], rationale: 'Clarify location.', ...suggestion,
} }) });

(async () => {
    const ui = fixture(async () => success());
    ui.nodes['rough-text'].value = 'Rough current text';
    ui.fields.title.value = 'My original title';
    await ui.ask();
    assert.equal(ui.fields.title.value, 'My original title');
    assert.equal(ui.nodes['assistant-review'].hidden, false);
    assert.equal(ui.heading.focused, true);
    const [url, options] = ui.calls[0];
    assert.equal(url, ui.panel.dataset.endpoint);
    assert.equal(options.method, 'POST'); assert.equal(options.credentials, 'same-origin');
    assert.equal(options.body.get('_csrf'), 'synthetic-csrf'); assert.equal(options.body.get('version'), '4');
    assert.equal(options.body.get('roughText'), 'Rough current text'); assert.equal(options.body.has('accepted'), false);
    const buttons = ui.nodes['suggested-fields'].children.map(row => row.children[2]);
    await buttons[0].click();
    assert.equal(ui.fields.title.value, 'Flickering light');
    assert.equal(ui.fields.description.value, '');
    await buttons[3].click(); await buttons[4].click();
    assert.equal(ui.fields.categoryId.value, 'electrical-id'); assert.equal(ui.fields.reportedUrgency.value, 'HIGH');
    assert.equal(ui.form.dataset.pending, undefined);
    assert.equal(ui.form.listeners.submit.length, 1); // Script listens for ordinary submission; it never invokes it.
    await ui.discard();
    assert.equal(ui.nodes['assistant-review'].hidden, true); assert.equal(ui.nodes['suggested-fields'].children.length, 0);
    assert.equal(ui.fields.title.value, 'Flickering light'); // Accepted values are now the user's editable manual fields.

    for (const provider of [async () => ({ ok: false, json: async () => ({ status: 'AI_LIMITED' }) }),
        async () => { throw new Error('network'); }, async () => ({ ok: true, json: async () => { throw new Error('malformed'); } })]) {
        const failed = fixture(provider); failed.fields.description.value = 'Keep my manual input';
        await failed.ask();
        assert.equal(failed.fields.description.value, 'Keep my manual input');
        assert.equal(failed.nodes['assistant-review'].hidden, true); assert.equal(failed.nodes['ask-assistant'].disabled, false);
        assert.match(failed.nodes['assistant-status'].textContent, /manual/);
    }

    let resolve;
    const late = fixture(() => new Promise(done => { resolve = done; }));
    const pending = late.ask();
    assert.equal(late.nodes['ask-assistant'].disabled, true);
    late.fields.title.value = 'Typed while waiting'; resolve(success()); await pending;
    assert.equal(late.nodes['assistant-review'].hidden, true); assert.equal(late.fields.title.value, 'Typed while waiting');
    assert.match(late.nodes['assistant-status'].textContent, /input changed/);

    const cancelled = fixture(() => new Promise(done => { resolve = done; }));
    const abandoned = cancelled.ask(); const signal = cancelled.calls[0][1].signal;
    await cancelled.discard(); resolve(success()); await abandoned;
    assert.equal(signal.aborted, true); assert.equal(cancelled.nodes['assistant-review'].hidden, true);
    assert.equal(cancelled.fields.title.value, '');

    const changed = fixture(async () => success()); await changed.ask();
    changed.fields.title.value = 'My newer edit';
    await changed.nodes['suggested-fields'].children[0].children[2].click();
    assert.equal(changed.fields.title.value, 'My newer edit');
    changed.fields.categoryId.options = [];
    await changed.nodes['suggested-fields'].children[3].children[2].click();
    assert.equal(changed.fields.categoryId.value, '');

    const encoded = fixture(async () => success({ draftTitle: '<img src=x onerror=alert(1)>', rationale: '<script>bad</script>', missingInformationQuestions: ['<b>Question</b>'] }));
    await encoded.ask();
    assert.equal(encoded.nodes['suggested-fields'].children[0].children[1].textContent, '<img src=x onerror=alert(1)>');
    assert.equal(encoded.nodes['assistant-rationale'].textContent, '<script>bad</script>');
    assert.equal(encoded.nodes['assistant-questions'].children[0].textContent, '<b>Question</b>');
    assert.equal(source.includes('innerHTML'), false);

    const saving = fixture(() => new Promise(done => { resolve = done; }));
    const inFlight = saving.ask(); saving.form.dataset.pending = 'true';
    saving.form.dispatchEvent({ type: 'submit' }); resolve(success()); await inFlight;
    assert.equal(saving.nodes['assistant-review'].hidden, true); assert.equal(saving.calls[0][1].signal.aborted, true);
    await saving.ask(); assert.equal(saving.calls.length, 1);
    delete saving.form.dataset.pending; saving.window.dispatchEvent({ type: 'pageshow' });
    assert.equal(saving.nodes['ask-assistant'].disabled, false);
    const oversized = fixture(async () => success()); oversized.nodes['rough-text'].value = 'x'.repeat(2001);
    await oversized.ask(); assert.equal(oversized.calls.length, 0);
    console.log('REQ-RQ-015–017: review, explicit acceptance, discard, stale/late/cancel/failure/escaping checks passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
