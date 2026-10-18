"use strict";

(() => {
    const pendingForms = document.querySelectorAll("form[data-pending-form]");
    for (const form of pendingForms) {
        const buttons = Array.from(form.querySelectorAll('button[type="submit"], button:not([type])'));
        form.addEventListener("submit", (event) => {
            if (form.dataset.pending === "true") {
                event.preventDefault();
                return;
            }
            form.dataset.pending = "true";
            form.setAttribute("aria-busy", "true");
            for (const button of buttons) button.disabled = true;
        });
        window.addEventListener("pageshow", () => {
            delete form.dataset.pending;
            form.removeAttribute("aria-busy");
            for (const button of buttons) button.disabled = false;
        });
    }
})();
