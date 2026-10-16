document.querySelectorAll('[data-request-form]').forEach(form => {
    form.addEventListener('submit', event => {
        if (form.dataset.pending) {
            event.preventDefault();
            return;
        }
        form.dataset.pending = 'true';
        // Named submitters must stay enabled so the browser includes the explicit decision.
        const button = form.querySelector('button[type="submit"]');
        if (!button.name) button.disabled = true;
        form.querySelector('[data-submitting]').hidden = false;
    });
    window.addEventListener('pageshow', () => {
        delete form.dataset.pending;
        form.querySelector('button[type="submit"]').disabled = false;
        form.querySelector('[data-submitting]').hidden = true;
    });
});
