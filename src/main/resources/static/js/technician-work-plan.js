"use strict";

(() => {
    const form = document.getElementById("work-plan-form");
    if (!form) return;

    const requestButton = document.getElementById("work-plan-request");
    const discardButton = document.getElementById("work-plan-discard");
    const status = document.getElementById("work-plan-status");
    const result = document.getElementById("work-plan-result");
    const csrf = form.querySelector('input[type="hidden"]');
    const fields = {
        diagnosticSteps: { id: "work-plan-diagnosticSteps", min: 1, max: 8, length: 300, kind: "list" },
        safetyPrecautions: { id: "work-plan-safetyPrecautions", min: 1, max: 8, length: 300, kind: "list" },
        suggestedTools: { id: "work-plan-suggestedTools", min: 0, max: 10, length: 120, kind: "list" },
        requesterQuestions: { id: "work-plan-requesterQuestions", min: 0, max: 5, length: 200, kind: "list" },
        evidenceChecklist: { id: "work-plan-evidenceChecklist", min: 1, max: 8, length: 200, kind: "list" },
        stopOrEscalateWhen: { id: "work-plan-stopOrEscalateWhen", min: 1, max: 5, length: 300, kind: "list" },
        rationale: { id: "work-plan-rationale", min: 1, max: 500, kind: "text" }
    };
    const keys = Object.keys(fields);
    const failures = {
        AI_UNAVAILABLE: "Work Plan suggestions are unavailable right now. You can continue with the manual Technician workflow.",
        AI_LIMITED: "Work Plan suggestions are busy. Try again later, or continue with the manual Technician workflow.",
        AI_TIMEOUT: "The Work Plan request took too long. You can retry, or continue with the manual Technician workflow.",
        AI_INVALID_OUTPUT: "The suggestions could not be safely displayed. Continue with the manual Technician workflow.",
        AI_CONFIGURATION_ERROR: "Work Plan suggestions are unavailable right now. You can continue with the manual Technician workflow.",
        DISCARDED_ACCESS_CHANGED: "The request or your access changed while suggestions were generated. Refresh this page before requesting another plan. The manual workflow remains available."
    };
    const genericFailure = "The Work Plan could not be loaded. You can retry, or continue with the manual Technician workflow.";
    let generation = 0;
    let controller = null;
    let pending = false;
    let refreshRequired = false;

    function clearAdvice() {
        for (const definition of Object.values(fields)) {
            const target = document.getElementById(definition.id);
            while (target.firstChild) target.removeChild(target.firstChild);
        }
        result.hidden = true;
        discardButton.hidden = true;
    }

    function invalidate() {
        generation += 1;
        if (controller) controller.abort();
        controller = null;
        pending = false;
        requestButton.disabled = refreshRequired;
        status.textContent = "Work Plan request stopped. Continue with the manual Technician workflow.";
    }

    function validText(value, maxLength) {
        if (typeof value !== "string") return false;
        const points = Array.from(value);
        if (points.length < 1 || points.length > maxLength || value.trim().length === 0) return false;
        return !/[<>\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]/u.test(value)
            && !/!\[[^\]]*\]\([^)]*\)|\[[^\]]+\]\([^)]*\)/u.test(value);
    }

    function validatePlan(plan) {
        if (!plan || typeof plan !== "object" || Array.isArray(plan)) return false;
        const actual = Object.keys(plan);
        if (actual.length !== keys.length || keys.some((key) => !Object.prototype.hasOwnProperty.call(plan, key))) return false;
        for (const [key, definition] of Object.entries(fields)) {
            const value = plan[key];
            if (definition.kind === "text") {
                if (!validText(value, definition.max)) return false;
            } else if (!Array.isArray(value) || value.length < definition.min || value.length > definition.max
                || value.some((item) => !validText(item, definition.length))) {
                return false;
            }
        }
        return true;
    }

    function renderPlan(plan) {
        for (const [key, definition] of Object.entries(fields)) {
            const target = document.getElementById(definition.id);
            if (definition.kind === "text") {
                target.textContent = plan[key];
            } else {
                for (const value of plan[key]) {
                    const item = document.createElement("li");
                    item.textContent = value;
                    target.appendChild(item);
                }
            }
        }
        result.hidden = false;
        discardButton.hidden = false;
        status.textContent = "Suggestions loaded. Review safety guidance and use your judgment.";
    }

    function safeFailure(payload) {
        const code = payload && typeof payload.code === "string" ? payload.code : "";
        status.textContent = failures[code] || genericFailure;
        if (code === "DISCARDED_ACCESS_CHANGED") {
            refreshRequired = true;
            requestButton.disabled = true;
        }
    }

    function validFailureEnvelope(payload) {
        return payload && typeof payload === "object" && !Array.isArray(payload)
            && Object.keys(payload).length === 2
            && Object.prototype.hasOwnProperty.call(payload, "code")
            && Object.prototype.hasOwnProperty.call(payload, "message")
            && typeof payload.code === "string" && typeof payload.message === "string";
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (pending || refreshRequired) return;
        clearAdvice();
        status.textContent = "Requesting Work Plan suggestions…";
        pending = true;
        requestButton.disabled = true;
        controller = new AbortController();
        const ownController = controller;
        const ownGeneration = ++generation;
        const timeout = window.setTimeout(() => ownController.abort(), 35000);
        try {
            const token = csrf && csrf.value;
            if (!csrf || csrf.name !== "_csrf" || !token) throw new Error("missing CSRF");
            const response = await fetch(form.action, {
                method: "POST",
                credentials: "same-origin",
                cache: "no-store",
                headers: { "Accept": "application/json", "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
                body: new URLSearchParams([[csrf.name, token]]),
                signal: ownController.signal
            });
            let payload;
            try {
                payload = await response.json();
            } catch (_) {
                payload = null;
            }
            if (ownGeneration !== generation) return;
            if (response.ok && payload && typeof payload === "object" && !Array.isArray(payload)
                && Object.keys(payload).length === 1 && Object.prototype.hasOwnProperty.call(payload, "plan")
                && validatePlan(payload.plan)) {
                renderPlan(payload.plan);
            } else if (validFailureEnvelope(payload)) {
                safeFailure(payload);
            } else {
                safeFailure(null);
            }
        } catch (_) {
            if (ownGeneration === generation) {
                status.textContent = genericFailure;
            }
        } finally {
            window.clearTimeout(timeout);
            if (ownGeneration === generation) {
                pending = false;
                controller = null;
                requestButton.disabled = refreshRequired;
            }
        }
    });

    discardButton.addEventListener("click", () => {
        invalidate();
        clearAdvice();
        status.textContent = "Suggestions discarded. You can request another plan or continue manually.";
        requestButton.disabled = refreshRequired;
    });

    document.addEventListener("submit", (event) => {
        if (event.target === form) return;
        invalidate();
        refreshRequired = true;
        requestButton.disabled = true;
        clearAdvice();
        status.textContent = "Refreshing request state. Work Plan suggestions were cleared.";
    }, true);

    window.addEventListener("pagehide", () => {
        invalidate();
        clearAdvice();
    });
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "hidden") {
            invalidate();
            clearAdvice();
        }
    });
    window.addEventListener("pageshow", () => {
        if (pending) invalidate();
        requestButton.disabled = refreshRequired;
    });
})();
