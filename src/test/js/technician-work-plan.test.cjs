"use strict";
// REQ-TECH-014/016/017: execute the delivered external script against a deterministic DOM/fetch fixture.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

class Element {
    constructor(id = "") {
        this.id = id;
        this.children = [];
        this.handlers = {};
        this.hidden = false;
        this.disabled = false;
        this.value = "";
        this.name = "";
        this.action = "";
        this.dataset = {};
        this.text = "";
    }
    addEventListener(type, handler) { this.handlers[type] = handler; }
    querySelector() { return this.csrf || null; }
    appendChild(node) { this.children.push(node); return node; }
    removeChild(node) { this.children.splice(this.children.indexOf(node), 1); return node; }
    get firstChild() { return this.children[0] || null; }
    set textContent(value) { this.children = []; this.text = value; }
    get textContent() { return this.children.length ? this.children.map((node) => node.textContent).join("") : this.text; }
}

const script = fs.readFileSync("src/main/resources/static/js/technician-work-plan.js", "utf8");
const ids = ["work-plan-form", "work-plan-request", "work-plan-discard", "work-plan-status", "work-plan-result",
    "work-plan-diagnosticSteps", "work-plan-safetyPrecautions", "work-plan-suggestedTools",
    "work-plan-requesterQuestions", "work-plan-evidenceChecklist", "work-plan-stopOrEscalateWhen",
    "work-plan-rationale"];
function fixture(fetchImpl) {
    const elements = new Map(ids.map((id) => [id, new Element(id)]));
    const form = elements.get("work-plan-form");
    form.action = "/technician/requests/FF-000001/work-plan";
    form.csrf = new Element("csrf");
    form.csrf.name = "_csrf";
    form.csrf.value = "csrf-token";
    const documentHandlers = {};
    const windowHandlers = {};
    const timers = [];
    const document = {
        visibilityState: "visible",
        getElementById: (id) => elements.get(id) || null,
        createElement: (name) => Object.assign(new Element(), {tagName: name.toUpperCase()}),
        addEventListener: (type, handler) => { documentHandlers[type] = handler; }
    };
    const window = {
        addEventListener: (type, handler) => { windowHandlers[type] = handler; },
        setTimeout: (callback) => { const timer = {callback}; timers.push(timer); return timer; },
        clearTimeout() {}
    };
    vm.runInNewContext(script, {document, window, fetch: fetchImpl, URLSearchParams, AbortController});
    return {elements, form, document, documentHandlers, windowHandlers, timers};
}
function plan() {
    return {
        diagnosticSteps: ["Check the isolated equipment."],
        safetyPrecautions: ["Wear required protective equipment."],
        suggestedTools: ["Voltage tester"],
        requesterQuestions: [],
        evidenceChecklist: ["Record observations."],
        stopOrEscalateWhen: ["Stop if the equipment remains energized."],
        rationale: "These are suggestions for human review."
    };
}
function response(payload, ok = true) { return {ok, async json() { return payload; }}; }
async function submit(form) { return form.handlers.submit({preventDefault() {}}); }

(async () => {
    let request;
    const success = fixture(async (url, options) => {
        request = {url, options};
        return response({plan: plan()});
    });
    await submit(success.form);
    assert.equal(request.url, "/technician/requests/FF-000001/work-plan");
    assert.equal(request.options.method, "POST");
    assert.equal(request.options.credentials, "same-origin");
    assert.equal(request.options.cache, "no-store");
    assert.equal(request.options.headers.Accept, "application/json");
    assert.equal(new URLSearchParams(request.options.body).get("_csrf"), "csrf-token");
    assert.equal([...new URLSearchParams(request.options.body).keys()].length, 1, "only CSRF is sent as form data");
    assert.equal(success.elements.get("work-plan-result").hidden, false);
    assert.equal(success.elements.get("work-plan-safetyPrecautions").children[0].textContent,
        "Wear required protective equipment.");
    assert.equal(success.elements.get("work-plan-rationale").textContent, plan().rationale);
    assert.equal(success.elements.get("work-plan-discard").hidden, false);

    const malformed = fixture(async () => response({plan: {...plan(), unexpected: "partial data"}}));
    await submit(malformed.form);
    assert.equal(malformed.elements.get("work-plan-result").hidden, true, "malformed plans are rejected atomically");
    assert.equal(malformed.elements.get("work-plan-safetyPrecautions").children.length, 0);
    assert.ok(malformed.elements.get("work-plan-status").textContent.includes("manual Technician workflow"));

    const unsafe = fixture(async () => response({plan: {...plan(), diagnosticSteps: ["<img src=x onerror=alert(1)>"]}}));
    await submit(unsafe.form);
    assert.equal(unsafe.elements.get("work-plan-result").hidden, true, "unsafe text never produces a partial render");

    const safeError = fixture(async () => response({code: "AI_UNAVAILABLE", message: "private arbitrary server detail"}, false));
    await submit(safeError.form);
    assert.ok(safeError.elements.get("work-plan-status").textContent.includes("manual Technician workflow"));
    assert.ok(!safeError.elements.get("work-plan-status").textContent.includes("private arbitrary"));

    for (const code of ["AI_LIMITED", "AI_TIMEOUT", "AI_INVALID_OUTPUT", "AI_CONFIGURATION_ERROR", "AI_INVALID_REQUEST"]) {
        const failure = fixture(async () => response({code, message: "untrusted server detail"}, false));
        await submit(failure.form);
        assert.ok(failure.elements.get("work-plan-status").textContent.includes("manual Technician workflow"), code);
        assert.ok(!failure.elements.get("work-plan-status").textContent.includes("untrusted server detail"), code);
    }

    const nonJson = fixture(async () => ({ok: false, async json() { throw new SyntaxError("not JSON"); }}));
    await submit(nonJson.form);
    assert.ok(nonJson.elements.get("work-plan-status").textContent.includes("manual Technician workflow"));
    const httpPlan = fixture(async () => response({plan: plan()}, false));
    await submit(httpPlan.form);
    assert.equal(httpPlan.elements.get("work-plan-result").hidden, true, "HTTP errors cannot render an otherwise valid plan");
    const network = fixture(async () => { throw new Error("synthetic network detail"); });
    await submit(network.form);
    assert.ok(!network.elements.get("work-plan-status").textContent.includes("synthetic network detail"));

    const timeout = fixture(async (_url, options) => new Promise((_resolve, reject) => {
        options.signal.addEventListener("abort", () => reject(new Error("aborted")), {once: true});
    }));
    const timeoutPending = submit(timeout.form);
    await Promise.resolve();
    timeout.timers[0].callback();
    await timeoutPending;
    assert.ok(timeout.elements.get("work-plan-status").textContent.includes("manual Technician workflow"));

    const changed = fixture(async () => response({code: "DISCARDED_ACCESS_CHANGED", message: ""}, false));
    await submit(changed.form);
    assert.equal(changed.elements.get("work-plan-request").disabled, true);
    assert.ok(changed.elements.get("work-plan-status").textContent.includes("Refresh this page"));

    let release;
    let signal;
    const pending = fixture(async (_url, options) => {
        signal = options.signal;
        return new Promise((resolve) => { release = resolve; });
    });
    const first = submit(pending.form);
    await Promise.resolve();
    let prevented = 0;
    await pending.form.handlers.submit({preventDefault() { prevented++; }});
    assert.equal(prevented, 1, "duplicate pending submit is blocked");
    const manual = new Element("manual-log");
    manual.fields = {note: "Unsubmitted technician draft", minutes: "25"};
    pending.documentHandlers.submit({target: manual});
    assert.equal(signal.aborted, true, "manual submission invalidates pending advice");
    release(response({plan: plan()}));
    await first;
    assert.equal(pending.elements.get("work-plan-result").hidden, true, "late response after manual submit is discarded");
    assert.deepEqual(manual.fields, {note: "Unsubmitted technician draft", minutes: "25"});

    for (const lifecycleEvent of ["pagehide", "visibilitychange"]) {
        let finish;
        const lifecycle = fixture(async () => new Promise((resolve) => { finish = resolve; }));
        const inFlight = submit(lifecycle.form);
        await Promise.resolve();
        if (lifecycleEvent === "pagehide") lifecycle.windowHandlers.pagehide();
        else {
            lifecycle.document.visibilityState = "hidden";
            lifecycle.documentHandlers.visibilitychange();
        }
        finish(response({plan: plan()}));
        await inFlight;
        assert.equal(lifecycle.elements.get("work-plan-result").hidden, true, `${lifecycleEvent} suppresses late advice`);
    }
    console.log("PASS: actual Work Plan script validates atomic output, posts CSRF only, fails safely, and suppresses stale advice");
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
