'use strict';

document.querySelectorAll('form[data-confirm]').forEach((form) => {
    form.addEventListener('submit', (event) => {
        if (!window.confirm(form.dataset.confirm)) {
            event.preventDefault();
        }
    });
});

const attachmentInput = document.getElementById('file');
if (attachmentInput) {
    attachmentInput.addEventListener('change', () => {
        const file = attachmentInput.files[0];
        const message = file && file.size > 5 * 1024 * 1024 ? '附件不可超過 5 MB，請重新選擇。' : '';
        attachmentInput.setCustomValidity(message);
        attachmentInput.classList.toggle('is-invalid', Boolean(message));
        document.getElementById('file-client-error').textContent = message;
        if (message) attachmentInput.reportValidity();
    });
}

(() => {
    const source = document.getElementById('content');
    const htmlSource = document.getElementById('contentHtml');
    const shell = document.getElementById('content-editor-shell');
    const host = document.getElementById('content-editor');
    const toolbar = document.getElementById('content-toolbar');
    if (!source || !htmlSource || !shell || !host || !toolbar || !window.Quill) return;

    const form = source.closest('form');
    const feedback = document.getElementById('content-client-error');
    const counter = document.getElementById('content-count');
    const maxText = 10000;
    const maxHtml = 100000;
    const alignmentClasses = ['ql-align-center', 'ql-align-right', 'ql-align-justify'];
    // Keep existing saved layouts readable while exposing only the simplified toolbar.
    const formats = ['bold', 'italic', 'underline', 'strike', 'size', 'color', 'background', 'header', 'list', 'align', 'indent'];

    try {
        const size = window.Quill.import('attributors/class/size');
        size.whitelist = ['14', '16', '18', '20', '24', '28', '32'];
        const color = window.Quill.import('attributors/class/color');
        color.whitelist = ['black', 'firebrick', 'darkorange', 'forestgreen', 'royalblue', 'indigo', 'dimgray'];
        const background = window.Quill.import('attributors/class/background');
        background.whitelist = ['lightyellow', 'lightcyan', 'lavender', 'honeydew'];
        window.Quill.register(size, true);
        window.Quill.register(color, true);
        window.Quill.register(background, true);

        // Native, labelled toolbar controls call Quill's API. No inline event handlers or styles are needed.
        const editor = new window.Quill(host, {
            theme: 'snow',
            formats,
            placeholder: '請輸入完整公告內容',
            modules: { toolbar: false, history: { delay: 500, maxStack: 100, userOnly: true } }
        });
        if (htmlSource.value.trim()) editor.clipboard.dangerouslyPasteHTML(htmlSource.value, 'silent');
        else editor.setText(source.value, 'silent');
        editor.history.clear();
        editor.root.setAttribute('role', 'textbox');
        editor.root.setAttribute('aria-multiline', 'true');
        editor.root.setAttribute('aria-required', 'true');
        editor.root.setAttribute('aria-labelledby', 'content-label');
        editor.root.setAttribute('aria-describedby', 'content-help content-client-error content-error contentHtml-error');
        editor.root.spellcheck = true;

        let selection = { index: 0, length: 0 };
        let submitted = false;
        let serverInvalid = source.classList.contains('is-invalid') || Boolean(document.getElementById('contentHtml-error'));

        function selectedRange() {
            const lastIndex = Math.max(0, editor.getLength() - 1);
            const index = Math.min(selection.index, lastIndex);
            return { index, length: Math.min(selection.length, lastIndex - index) };
        }

        function semanticHtml() {
            const template = document.createElement('template');
            template.innerHTML = editor.getSemanticHTML();
            // Quill 2's semantic list serializer omits list-item alignment attributes.
            // Keep alignment while retaining the semantic nesting that represents indentation.
            const sourceItems = [...editor.root.querySelectorAll('li')];
            const targetItems = [...template.content.querySelectorAll('li')].filter((item) =>
                [...item.childNodes].some((node) => node.nodeType === Node.TEXT_NODE
                    ? Boolean(node.textContent.length)
                    : node.nodeType === Node.ELEMENT_NODE && !['OL', 'UL'].includes(node.nodeName)));
            if (sourceItems.length === targetItems.length) {
                sourceItems.forEach((item, index) => {
                    alignmentClasses.forEach((name) => {
                        if (item.classList.contains(name)) targetItems[index].classList.add(name);
                    });
                });
            }
            return template.innerHTML;
        }

        function syncContent(showRequired) {
            const text = editor.getText().replace(/\n$/, '');
            const html = semanticHtml();
            source.value = text;
            htmlSource.value = html;
            counter.textContent = text.length.toLocaleString('en-US') + ' / 10,000 字';
            let message = '';
            if ((showRequired || submitted) && !text.trim()) message = '請輸入公布內容。';
            else if (text.length > maxText) message = '公布內容最多 10,000 字，請減少文字。';
            else if (html.length > maxHtml) message = '內文格式資料過大，請減少格式或分段。';
            feedback.textContent = message;
            feedback.hidden = !message;
            counter.classList.toggle('editor-limit-exceeded', text.length > maxText);
            shell.classList.toggle('is-invalid', Boolean(message) || serverInvalid);
            editor.root.setAttribute('aria-invalid', String(Boolean(message) || serverInvalid));
            return !message;
        }

        function updateToolbar() {
            const range = selectedRange();
            const current = editor.getFormat(range.index, range.length);
            toolbar.querySelectorAll('button[data-editor-format]').forEach((button) => {
                const name = button.dataset.editorFormat;
                button.setAttribute('aria-pressed', String(current[name] === true));
            });
            toolbar.querySelectorAll('select[data-editor-format]').forEach((select) => {
                const name = select.dataset.editorFormat;
                const value = current[name];
                const defaultSize = current.header === 2 ? '24' : current.header === 3 ? '20' : '16';
                const chosen = Array.isArray(value) ? '' : String(value || (name === 'size' ? defaultSize : ''));
                select.value = [...select.options].some((option) => option.value === chosen) ? chosen : '';
            });
        }

        function restoreSelection() {
            const range = selectedRange();
            editor.focus();
            editor.setSelection(range.index, range.length, 'silent');
            return range;
        }

        toolbar.addEventListener('mousedown', (event) => {
            if (event.target.closest('button')) event.preventDefault();
        });
        toolbar.addEventListener('click', (event) => {
            const button = event.target.closest('button[data-editor-format]');
            if (!button || !toolbar.contains(button)) return;
            const range = restoreSelection();
            const name = button.dataset.editorFormat;
            const current = editor.getFormat(range.index, range.length);
            editor.format(name, !current[name], 'user');
            updateToolbar();
            syncContent(false);
        });
        toolbar.addEventListener('change', (event) => {
            const select = event.target.closest('select[data-editor-format]');
            if (!select) return;
            const value = select.value;
            const name = select.dataset.editorFormat;
            const range = restoreSelection();
            const current = editor.getFormat(range.index, range.length);
            const defaultSize = name === 'size' && value === '16' && !current.header;
            editor.format(name, defaultSize ? false : value || false, 'user');
            updateToolbar();
            syncContent(false);
        });
        editor.on('selection-change', (range) => {
            if (range) { selection = { index: range.index, length: range.length }; updateToolbar(); }
        });
        editor.on('text-change', () => {
            serverInvalid = false;
            ['content-error', 'contentHtml-error'].forEach((id) => {
                const message = document.getElementById(id);
                if (message) message.hidden = true;
            });
            syncContent(false);
            updateToolbar();
        });
        form.addEventListener('submit', (event) => {
            submitted = true;
            if (!syncContent(true)) {
                event.preventDefault();
                editor.focus();
                shell.scrollIntoView({ block: 'center' });
            }
        });
        document.getElementById('content-label').addEventListener('click', (event) => {
            event.preventDefault();
            editor.focus();
        });
        source.hidden = true;
        source.required = false;
        htmlSource.disabled = false;
        shell.hidden = false;
        syncContent(false);
        updateToolbar();
    } catch (error) {
        // Keep the original, usable plain-text form if the editor cannot initialize.
        shell.hidden = true;
        source.hidden = false;
        source.required = true;
        htmlSource.disabled = true;
        console.error('無法啟用公告文字編輯器。', error);
    }
})();
