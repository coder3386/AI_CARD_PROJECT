(function () {
    const frame = document.getElementById("cardPreviewFrame");
    const extraList = document.querySelector("[data-extra-list]");

    const typeLabels = {
        SKILL: "기술",
        CERTIFICATE: "자격증",
        PORTFOLIO: "포트폴리오",
        LINK: "링크"
    };

    function updatePreview(input) {
        const targetId = input.dataset.previewTarget;
        if (!targetId || !frame || !frame.contentDocument) {
            return;
        }

        const target = frame.contentDocument.getElementById(targetId);
        if (target) {
            target.textContent = input.value;
        }
    }

    function createExtraRow(type) {
        const row = document.createElement("div");
        row.className = "ai-extra-row";
        row.dataset.extraRow = "";
        row.innerHTML = `
            <input type="hidden" data-extra-field="itemType" value="${type}">
            <div class="ai-extra-row-head">
                <strong>${typeLabels[type] || "기타"}</strong>
                <button type="button" data-extra-remove>삭제</button>
            </div>
            <label>
                제목
                <input data-extra-field="title" placeholder="Java, 정보처리기사, GitHub">
            </label>
            <label>
                링크
                <input data-extra-field="url" placeholder="https://example.com">
            </label>
        `;
        return row;
    }

    function reindexExtraRows() {
        if (!extraList) {
            return;
        }

        extraList.querySelectorAll("[data-extra-row]").forEach((row, index) => {
            row.querySelectorAll("[data-extra-field]").forEach((field) => {
                field.name = `extraItems[${index}].${field.dataset.extraField}`;
            });
        });
    }

    function readExtraRows() {
        if (!extraList) {
            return [];
        }

        return Array.from(extraList.querySelectorAll("[data-extra-row]")).map((row) => {
            const item = {};
            row.querySelectorAll("[data-extra-field]").forEach((field) => {
                item[field.dataset.extraField] = field.value || "";
            });
            return item;
        }).filter((item) => item.title || item.url);
    }

    function escapeText(value) {
        return (value || "").replace(/[&<>"']/g, function (match) {
            return {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                "\"": "&quot;",
                "'": "&#039;"
            }[match];
        });
    }

    function renderExtraPreview() {
        if (!frame || !frame.contentDocument) {
            return;
        }

        const items = readExtraRows();
        const portfolioArea = frame.contentDocument.getElementById("portfolioArea");
        const linkArea = frame.contentDocument.getElementById("linkArea");
        ensureExtraModal(frame.contentDocument);

        if (portfolioArea) {
            normalizeExtraArea(portfolioArea, "portfolio");
            portfolioArea.innerHTML = renderExtraArea("Portfolio / Skills / Links", items);
            toggleExtraArea(portfolioArea, true);
        }

        if (linkArea) {
            linkArea.innerHTML = "";
            toggleExtraArea(linkArea, false);
        }
    }

    function normalizeExtraArea(area, kind) {
        area.style.boxSizing = "border-box";
        area.style.position = "absolute";
        area.style.right = "34px";
        area.style.left = "auto";
        area.style.top = "auto";
        area.style.transform = "none";
        area.style.width = "min(360px, calc(100% - 68px))";
        area.style.maxHeight = kind === "portfolio" ? "112px" : "96px";
        area.style.bottom = kind === "portfolio" ? "132px" : "24px";
        area.style.zIndex = "5";
        area.style.padding = "18px";
        area.style.border = "1px solid rgba(255,255,255,.18)";
        area.style.borderRadius = "18px";
        area.style.color = "inherit";
        area.style.background = "rgba(255,255,255,.08)";
        area.style.overflow = "hidden";
    }

    function toggleExtraArea(area, visible) {
        area.style.display = visible ? "" : "none";
    }

    function renderExtraArea(title, items) {
        if (items.length === 0) {
            return `
                <div class="card-extra-title">${escapeText(title)}</div>
                <div class="card-extra-empty">추가 정보 영역</div>
            `;
        }
        return `
            <div class="card-extra-title">${escapeText(title)}</div>
            <div class="card-extra-list">
                ${items.map(renderExtraItem).join("")}
            </div>
        `;
    }

    function renderExtraItem(item) {
        const title = escapeText(item.title || item.url || "");
        const url = escapeText(item.url || "");
        const kind = escapeText(typeLabels[item.itemType] || "기타");

        return `
            <button type="button"
                    class="card-extra-item card-extra-button"
                    data-card-extra-button
                    data-extra-type="${kind}"
                    data-extra-title="${title}"
                    data-extra-url="${url}">
                <span class="card-extra-kind">${kind}</span>
                ${title ? `<strong>${title}</strong>` : ""}
            </button>
        `;
    }

    function ensureExtraModal(doc) {
        if (!doc || doc.getElementById("cardExtraModal")) {
            return;
        }

        const style = doc.createElement("style");
        style.textContent = `
            .card-extra-button { cursor: pointer; font: inherit; text-align: left; appearance: none; transition: transform .16s ease, background .16s ease, border-color .16s ease; }
            .card-extra-button:hover { transform: translateY(-1px); background: rgba(255,255,255,.2); border-color: rgba(255,255,255,.34); }
            .card-extra-empty { min-height: 48px; display: grid; place-items: center; border: 1px dashed rgba(255,255,255,.22); border-radius: 12px; color: inherit; opacity: .42; font-size: 12px; font-weight: 700; }
            .card-extra-modal-backdrop { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(2, 6, 23, .42); z-index: 9999; }
            .card-extra-modal-backdrop[hidden] { display: none !important; }
            .card-extra-modal { width: min(360px, calc(100vw - 48px)); box-sizing: border-box; position: relative; padding: 22px; border-radius: 18px; border: 1px solid rgba(255,255,255,.22); color: #f8fafc; background: rgba(15,23,42,.96); box-shadow: 0 24px 70px rgba(0,0,0,.35); font-family: Arial, sans-serif; }
            .card-extra-modal-close { position: absolute; top: 10px; right: 10px; width: 32px; height: 32px; border-radius: 999px; border: 1px solid rgba(255,255,255,.2); color: #f8fafc; background: rgba(255,255,255,.08); cursor: pointer; }
            .card-extra-modal-type { margin: 0 0 8px; font-size: 12px; font-weight: 800; color: #38bdf8; letter-spacing: .08em; }
            .card-extra-modal-title { margin: 0; padding-right: 34px; font-size: 22px; line-height: 1.3; }
            .card-extra-modal-url { margin: 14px 0 0; font-size: 13px; line-height: 1.45; word-break: break-all; opacity: .86; }
            .card-extra-modal-open { width: 100%; margin-top: 18px; padding: 12px 14px; border: 0; border-radius: 12px; color: #06251f; background: #5eead4; font-weight: 800; cursor: pointer; }
        `;
        doc.head.appendChild(style);

        const modal = doc.createElement("div");
        modal.id = "cardExtraModal";
        modal.className = "card-extra-modal-backdrop";
        modal.hidden = true;
        modal.innerHTML = `
            <div class="card-extra-modal" role="dialog" aria-modal="true" aria-labelledby="cardExtraModalTitle">
                <button type="button" class="card-extra-modal-close" data-card-extra-close aria-label="close">X</button>
                <p class="card-extra-modal-type" data-card-extra-modal-type></p>
                <h2 id="cardExtraModalTitle" class="card-extra-modal-title" data-card-extra-modal-title></h2>
                <p class="card-extra-modal-url" data-card-extra-modal-url></p>
                <button type="button" class="card-extra-modal-open" data-card-extra-open hidden>링크로 이동</button>
            </div>
        `;
        doc.body.appendChild(modal);
        bindExtraModal(doc, modal);
    }

    function bindExtraModal(doc, modal) {
        const typeEl = modal.querySelector("[data-card-extra-modal-type]");
        const titleEl = modal.querySelector("[data-card-extra-modal-title]");
        const urlEl = modal.querySelector("[data-card-extra-modal-url]");
        const openButton = modal.querySelector("[data-card-extra-open]");
        let currentUrl = "";

        doc.addEventListener("click", function (event) {
            const itemButton = event.target.closest("[data-card-extra-button]");
            if (itemButton) {
                currentUrl = itemButton.dataset.extraUrl || "";
                typeEl.textContent = itemButton.dataset.extraType || "ITEM";
                titleEl.textContent = itemButton.dataset.extraTitle || "상세 정보";
                urlEl.textContent = currentUrl;
                urlEl.hidden = !currentUrl;
                openButton.hidden = !currentUrl;
                modal.hidden = false;
                return;
            }

            if (event.target.closest("[data-card-extra-close]") || event.target === modal) {
                modal.hidden = true;
                currentUrl = "";
                return;
            }

            if (event.target.closest("[data-card-extra-open]") && currentUrl) {
                if (doc.defaultView.confirm(`${currentUrl} 로 이동하시겠습니까?`)) {
                    doc.defaultView.open(currentUrl, "_blank", "noopener");
                }
            }
        });

        doc.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                modal.hidden = true;
                currentUrl = "";
            }
        });
    }

    document.querySelectorAll("[data-preview-target]").forEach((input) => {
        input.addEventListener("input", function () {
            updatePreview(input);
        });
    });

    document.querySelectorAll("[data-extra-add]").forEach((button) => {
        button.addEventListener("click", function () {
            if (!extraList) {
                return;
            }
            extraList.appendChild(createExtraRow(button.dataset.extraAdd));
            reindexExtraRows();
            renderExtraPreview();
        });
    });

    if (extraList) {
        extraList.addEventListener("click", function (event) {
            const button = event.target.closest("[data-extra-remove]");
            if (!button) {
                return;
            }
            button.closest("[data-extra-row]").remove();
            reindexExtraRows();
            renderExtraPreview();
        });

        extraList.addEventListener("input", function () {
            reindexExtraRows();
            renderExtraPreview();
        });

        reindexExtraRows();
    }

    if (frame) {
        frame.addEventListener("load", renderExtraPreview);
    }
})();
