// REQ-SH-010: pending submission prevention and back-navigation recovery.
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const button = { disabled: false };
const status = { hidden: true };
const listeners = {};
const form = {
    dataset: {},
    addEventListener: (event, callback) => { listeners[event] = callback; },
    querySelector: selector => selector === '[data-submitting]' ? status : button,
};
runInNewContext(readFileSync('src/main/resources/static/js/request-form.js', 'utf8'), {
    document: { querySelectorAll: () => [form] },
    window: { addEventListener: (event, callback) => { listeners[event] = callback; } },
});
let prevented = false;
const event = { preventDefault: () => { prevented = true; } };
listeners.submit(event);
assert.equal(prevented, false);
assert.equal(button.disabled, true);
assert.equal(status.hidden, false);
listeners.submit(event);
assert.equal(prevented, true);
listeners.pageshow();
assert.equal(button.disabled, false);
assert.equal(status.hidden, true);
assert.equal(form.dataset.pending, undefined);
console.log('REQ-SH-010: pending and back-navigation checks passed');
