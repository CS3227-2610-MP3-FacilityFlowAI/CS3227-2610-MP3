"use strict";
// REQ-SH-010: execute the delivered pending-form script against an isolated DOM fixture.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

class Element {
    constructor(tagName = "form") {
        this.tagName = tagName.toUpperCase();
        this.handlers = {};
        this.attributes = {};
        this.dataset = {};
        this.buttons = [];
        this.passwordReads = 0;
        this.passwordValue = "synthetic secret";
    }
    addEventListener(type, callback) { this.handlers[type] = callback; }
    querySelectorAll(selector) {
        assert.equal(selector, 'button[type="submit"], button:not([type])');
        return this.buttons;
    }
    setAttribute(name, value) { this.attributes[name] = value; }
    removeAttribute(name) { delete this.attributes[name]; }
    get password() { this.passwordReads++; return this.passwordValue; }
}

const script = fs.readFileSync("src/main/resources/static/js/form-submission.js", "utf8");
const first = new Element();
first.dataset.pendingForm = "";
const second = new Element();
second.dataset.pendingForm = "";
const unrelated = new Element();
unrelated.dataset = {};
const firstSubmit = new Element("button");
const secondSubmit = new Element("button");
const firstOther = new Element("button");
// querySelectorAll returns only submit buttons and default-type buttons; this
// explicit type=button control is deliberately outside the selected results.
firstOther.type = "button";
first.buttons = [firstSubmit];
second.buttons = [secondSubmit];
const windowHandlers = {};
const document = {querySelectorAll: (selector) => {
    assert.equal(selector, "form[data-pending-form]");
    return [first, second];
}};
const window = {addEventListener: (type, handler) => {
    (windowHandlers[type] ||= []).push(handler);
}};
vm.runInNewContext(script, {document, window});

let prevented = 0;
first.handlers.submit({preventDefault() { prevented++; }});
assert.equal(first.dataset.pending, "true");
assert.equal(first.attributes["aria-busy"], "true");
assert.equal(firstSubmit.disabled, true);
assert.equal(firstOther.disabled, undefined, "non-submit buttons remain usable");
assert.equal(second.dataset.pending, undefined, "pending state belongs to each form");
assert.equal(secondSubmit.disabled, undefined);
first.handlers.submit({preventDefault() { prevented++; }});
assert.equal(prevented, 1, "duplicate submission is prevented once the first is pending");

second.handlers.submit({preventDefault() { prevented++; }});
assert.equal(second.dataset.pending, "true", "a distinct form can submit independently");
assert.equal(secondSubmit.disabled, true);
for (const handler of windowHandlers.pageshow) handler();
assert.equal(first.dataset.pending, undefined);
assert.equal(second.dataset.pending, undefined);
assert.equal(first.attributes["aria-busy"], undefined);
assert.equal(firstSubmit.disabled, false);
assert.equal(secondSubmit.disabled, false);

assert.equal(first.passwordReads, 0, "script never reads password fields");
assert.equal(second.passwordReads, 0, "script never reads password fields");
assert.equal(first.dataset.pendingForm, "", "the opt-in marker remains while transient pending state is cleared");
assert.equal(second.dataset.pendingForm, "");
console.log("PASS: actual pending-form script suppresses duplicates, restores pageshow, and does not inspect field values");
