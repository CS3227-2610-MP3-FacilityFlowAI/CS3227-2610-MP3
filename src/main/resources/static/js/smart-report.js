// REQ-RQ-015–017: temporary review only; explicit field acceptance never submits the business form.
document.querySelectorAll('[data-smart-report]').forEach(panel => {
    const form = document.querySelector('[data-request-form]');
    const rough = panel.querySelector('[data-rough-text]');
    const ask = panel.querySelector('[data-ask-assistant]');
    const discard = panel.querySelector('[data-discard-suggestions]');
    const status = panel.querySelector('[data-assistant-status]');
    const review = panel.querySelector('[data-assistant-review]');
    const rows = panel.querySelector('[data-suggested-fields]');
    const questions = panel.querySelector('[data-assistant-questions]');
    const rationale = panel.querySelector('[data-assistant-rationale]');
    const names = ['title', 'description', 'location', 'categoryId', 'reportedUrgency'];
    const field = name => form.elements.namedItem(name);
    const snapshot = () => Object.fromEntries([...names.map(name => [name, field(name).value]), ['roughText', rough.value]]);
    let controller;
    let generation = 0;

    const clear = () => {
        generation++;
        controller?.abort();
        controller = undefined;
        ask.disabled = false;
        panel.removeAttribute('aria-busy');
        review.hidden = true;
        discard.hidden = true;
        rows.replaceChildren();
        questions.replaceChildren();
        rationale.textContent = '';
    };
    discard.addEventListener('click', () => {
        clear();
        status.textContent = 'AI suggestions discarded. Your report fields are unchanged.';
        ask.focus();
    });

    ask.hidden = false;
    ask.addEventListener('click', async () => {
        if (form.dataset.pending) return;
        clear();
        const attempt = generation;
        const original = snapshot();
        const body = new URLSearchParams(original);
        const csrf = form.elements.namedItem('_csrf');
        if (!csrf || [...rough.value].length > 2000) {
            status.textContent = 'Check the rough-text limit or reload the page. Your manual form remains usable.';
            return;
        }
        body.set('_csrf', csrf.value);
        const version = form.elements.namedItem('version');
        if (version) body.set('version', version.value);
        controller = new AbortController();
        const requestController = controller;
        const timeout = setTimeout(() => requestController.abort(), 35000);
        ask.disabled = true;
        discard.hidden = false;
        panel.setAttribute('aria-busy', 'true');
        status.textContent = 'Preparing AI suggestions. You can continue editing your manual form.';
        try {
            const response = await fetch(panel.dataset.endpoint, {
                method: 'POST', credentials: 'same-origin', cache: 'no-store', redirect: 'error',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'Accept': 'application/json' },
                body, signal: requestController.signal,
            });
            const reply = await response.json();
            if (attempt !== generation || requestController.signal.aborted) return;
            if (!response.ok || reply.status !== 'SUCCEEDED' || !reply.suggestion) {
                status.textContent = 'AI suggestions are unavailable for this attempt. Keep editing or submit your manual form; you can try AI again later.';
                return;
            }
            if (JSON.stringify(snapshot()) !== JSON.stringify(original)) {
                status.textContent = 'Your input changed while AI was working. The result was discarded; ask again using your current input.';
                return;
            }
            const suggestion = reply.suggestion;
            const choices = [
                ['draftTitle', 'title', 'title'], ['draftDescription', 'description', 'description'],
                ['draftLocation', 'location', 'location'], ['suggestedCategory', 'categoryId', 'category'],
                ['suggestedUrgency', 'reportedUrgency', 'reported urgency'],
            ];
            for (const [key, name, label] of choices) {
                if (typeof suggestion[key] !== 'string') continue;
                const row = document.createElement('div');
                const heading = document.createElement('h4');
                heading.textContent = `Suggested ${label}`;
                const text = document.createElement('p');
                text.className = 'free-text';
                text.textContent = suggestion[key];
                const use = document.createElement('button');
                use.type = 'button';
                use.textContent = `Use suggested ${label}`;
                use.addEventListener('click', () => {
                    if (form.dataset.pending || field(name).value !== original[name]) {
                        status.textContent = 'This field changed since the suggestion. Keep your edit or ask for new suggestions.';
                        return;
                    }
                    let value = suggestion[key];
                    if (name === 'categoryId') {
                        value = [...field(name).options].find(option => option.value && !option.disabled && option.textContent === value)?.value;
                    } else if (name === 'reportedUrgency') value = value.toUpperCase();
                    if (value === undefined || (field(name).options && ![...field(name).options].some(option => option.value === value && !option.disabled))) {
                        status.textContent = 'That suggested choice is no longer available. Choose a current option in the manual form.';
                        return;
                    }
                    field(name).value = value;
                    field(name).dispatchEvent(new Event('input', { bubbles: true }));
                    field(name).dispatchEvent(new Event('change', { bubbles: true }));
                    use.disabled = true;
                    status.textContent = `Suggested ${label} copied. You can edit it before submitting. Nothing has been saved.`;
                    field(name).focus();
                });
                row.append(heading, text, use);
                rows.append(row);
            }
            for (const question of suggestion.missingInformationQuestions) {
                const item = document.createElement('li');
                item.textContent = question;
                questions.append(item);
            }
            rationale.textContent = suggestion.rationale;
            review.hidden = false;
            status.textContent = 'AI suggestions ready. Use fields individually or discard them; your report has not been saved.';
            panel.querySelector('#suggestions-heading').focus();
        } catch (error) {
            if (attempt === generation) status.textContent = 'AI suggestions could not be loaded. Your manual input is unchanged; keep editing or submit normally.';
        } finally {
            clearTimeout(timeout);
            if (attempt === generation) {
                ask.disabled = false;
                panel.removeAttribute('aria-busy');
                if (review.hidden) discard.hidden = true;
                controller = undefined;
            }
        }
    });
    form.addEventListener('submit', () => { clear(); ask.disabled = true; status.textContent = ''; });
    window.addEventListener('pagehide', clear);
    window.addEventListener('pageshow', () => { clear(); status.textContent = ''; });
});
