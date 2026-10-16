document.querySelectorAll('[data-request-form]').forEach(form => {
    form.addEventListener('submit', event => {
        if (form.dataset.pending) {
            event.preventDefault();
            return;
        }
        form.dataset.pending = 'true';
        form.querySelector('button[type="submit"]').disabled = true;
        form.querySelector('[data-submitting]').hidden = false;
    });
    window.addEventListener('pageshow', () => {
        delete form.dataset.pending;
        form.querySelector('button[type="submit"]').disabled = false;
        form.querySelector('[data-submitting]').hidden = true;
    });
});
