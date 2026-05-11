(function () {
    const frame = document.getElementById("cardPreviewFrame");
    const extraList = document.querySelector("[data-extra-list]");

    const typeLabels = {
        SKILL: "기술",
        CERTIFICATE: "자격증",
        PORTFOLIO: "포트폴리오",
        LINK: "링크",
        PHOTO: "사진"
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
            <label>
                설명
                <textarea data-extra-field="description" rows="2" placeholder="간단한 설명"></textarea>
            </label>
            <label>
                사진 URL
                <input data-extra-field="imageUrl" placeholder="https://example.com/photo.jpg">
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
        }).filter((item) => item.title || item.url || item.description || item.imageUrl);
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
        const profileImage = frame.contentDocument.getElementById("profileImage");

        if (portfolioArea) {
            const portfolioItems = items.filter((item) => item.itemType !== "LINK");
            if (portfolioItems.length > 0) {
                portfolioArea.innerHTML = `
                    <div class="card-extra-title">Portfolio / Skills</div>
                    <div class="card-extra-list">
                        ${portfolioItems.map(renderExtraItem).join("")}
                    </div>
                `;
            }
        }

        if (linkArea) {
            const linkItems = items.filter((item) => item.itemType === "LINK");
            if (linkItems.length > 0) {
                linkArea.innerHTML = `
                    <div class="card-extra-title">Links</div>
                    <div class="card-extra-list">
                        ${linkItems.map(renderExtraItem).join("")}
                    </div>
                `;
            }
        }

        if (profileImage) {
            const photo = items.find((item) => item.itemType === "PHOTO" && item.imageUrl);
            if (photo) {
                profileImage.innerHTML = `<img class="card-profile-img" src="${escapeText(photo.imageUrl)}" alt="profile image">`;
            }
        }
    }

    function renderExtraItem(item) {
        const title = escapeText(item.title || item.url || "");
        const description = escapeText(item.description || "");
        const url = escapeText(item.url || "");
        const imageUrl = escapeText(item.imageUrl || "");
        const kind = escapeText(typeLabels[item.itemType] || "기타");

        return `
            <div class="card-extra-item">
                ${imageUrl ? `<img class="card-extra-image" src="${imageUrl}" alt="${title || "portfolio image"}">` : ""}
                <span class="card-extra-kind">${kind}</span>
                ${title ? `<strong>${title}</strong>` : ""}
                ${description ? `<small>${description}</small>` : ""}
                ${url ? `<small>${url}</small>` : ""}
            </div>
        `;
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
