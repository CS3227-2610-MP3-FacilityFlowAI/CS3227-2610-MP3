"use strict";

(() => {
    const forms = document.querySelectorAll("form[data-command-form]");

    for (const form of forms) {
        form.addEventListener("submit", (event) => {
            if (form.dataset.pending === "true") {
                event.preventDefault();
                return;
            }
            form.dataset.pending = "true";
            for (const button of form.querySelectorAll('button[type="submit"]')) {
                button.disabled = true;
                button.dataset.originalText = button.textContent;
                button.textContent = "Submitting…";
            }
        });
    }

    window.addEventListener("pageshow", () => {
        for (const form of forms) {
            delete form.dataset.pending;
            for (const button of form.querySelectorAll('button[type="submit"]')) {
                button.disabled = false;
                if (button.dataset.originalText) {
                    button.textContent = button.dataset.originalText;
                    delete button.dataset.originalText;
                }
            }
        }
    });
})();
