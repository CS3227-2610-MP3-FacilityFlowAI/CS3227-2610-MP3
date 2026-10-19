"use strict";
// REQ-SH-010: execute the actual external script with a small DOM event model.
// This verifies pending-submit behavior, not browser rendering/accessibility.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
function form(label) {
    const button = {disabled: false, dataset: {}, textContent: label};
    return {dataset: {}, button, fields: {note: "Unsaved note", minutes: "25"}, handlers: {},
        addEventListener(type, callback) {this.handlers[type] = callback;},
        querySelectorAll() {return [button];}};
}
const first = form("Add Work Log");
const second = form("Start work");
const windowHandlers = {};
const code = fs.readFileSync("src/main/resources/static/js/technician-commands.js", "utf8");
vm.runInNewContext(code, {
    document: {querySelectorAll: () => [first, second]},
    window: {addEventListener: (type, callback) => {windowHandlers[type] = callback;}}
});
let prevented = 0;
const event = {preventDefault() {prevented++;}};
first.handlers.submit(event);
assert.equal(prevented, 0);
assert.equal(first.button.disabled, true);
assert.equal(second.button.disabled, false);
assert.deepEqual(first.fields, {note: "Unsaved note", minutes: "25"});
first.handlers.submit(event);
assert.equal(prevented, 1, "second pending submission is blocked");
second.handlers.submit(event);
assert.equal(second.button.disabled, true, "independent form may submit");
windowHandlers.pageshow();
assert.equal(first.button.disabled, false);
assert.equal(second.button.disabled, false);
assert.equal(first.button.textContent, "Add Work Log");
assert.equal(second.button.textContent, "Start work");
assert.equal(first.dataset.pending, undefined);
first.handlers.submit(event);
assert.equal(prevented, 1, "restored page can submit again");
vm.runInNewContext(code, {
    document: {querySelectorAll: () => []},
    window: {addEventListener() {}}
});
console.log("PASS: pending duplicate blocked, independent forms, retained inputs, pageshow reset, no-form page");
