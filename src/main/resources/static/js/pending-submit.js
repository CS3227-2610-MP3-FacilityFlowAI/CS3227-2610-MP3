"use strict";

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-prevent-double-submit]").forEach((form) => {
        form.addEventListener("submit", () => {
            const submit = form.querySelector('button[type="submit"]');
            if (submit) {
                submit.disabled = true;
            }
        });
    });
});
