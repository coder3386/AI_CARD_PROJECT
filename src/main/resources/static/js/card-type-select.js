(function () {
    const storageKey = "AICARD_OPENAI_API_KEY";
    const geminiStorageKey = "AICARD_GEMINI_API_KEY";
    const apiKeyInput = document.getElementById("personalApiKeyInput");
    const geminiApiKeyInput = document.getElementById("personalGeminiApiKeyInput");
    const apiKeyField = document.getElementById("apiKeyField");
    const geminiApiKeyField = document.getElementById("geminiApiKeyField");
    const apiKeySelectError = document.getElementById("apiKeySelectError");
    const textCardButton = document.getElementById("textCardButton");
    const drawingCardButton = document.getElementById("drawingCardButton");
    const loadingMessageByAction = [
        { keyword: "/cards/drawing/generate", message: "그림 기반 명함 제작중입니다..." },
        { keyword: "/cards/generate", message: "텍스트 기반 명함 제작중입니다..." },
        { keyword: "/update-text", message: "명함을 다시 생성중입니다..." },
        { keyword: "/delete", message: "명함을 삭제중입니다..." }
    ];

    function saveOneKey(input, key) {
        const value = input ? input.value || "" : "";
        if (value.trim()) {
            sessionStorage.setItem(key, value);
        } else {
            sessionStorage.removeItem(key);
        }
    }

    function saveApiKeysFromInput() {
        saveOneKey(apiKeyInput, storageKey);
        saveOneKey(geminiApiKeyInput, geminiStorageKey);
    }

    function hasRequiredApiKeys() {
        const openAiKey = apiKeyInput ? apiKeyInput.value : sessionStorage.getItem(storageKey);
        const geminiKey = geminiApiKeyInput ? geminiApiKeyInput.value : sessionStorage.getItem(geminiStorageKey);
        return Boolean((openAiKey && openAiKey.trim()) || (geminiKey && geminiKey.trim()));
    }

    function showApiKeyError() {
        if (apiKeySelectError) {
            apiKeySelectError.hidden = false;
        }
        window.alert("GPT API Key 또는 Gemini API Key 중 하나는 입력해야 합니다.");
    }

    function hideApiKeyErrorIfReady() {
        if (apiKeySelectError && hasRequiredApiKeys()) {
            apiKeySelectError.hidden = true;
        }
    }

    function moveTo(button) {
        saveApiKeysFromInput();
        if (!hasRequiredApiKeys()) {
            showApiKeyError();
            return;
        }
        const targetUrl = button.dataset.targetUrl;
        if (targetUrl) {
            location.href = targetUrl;
        }
    }

    function fillHiddenApiKey() {
        const savedKey = sessionStorage.getItem(storageKey);
        const savedGeminiKey = sessionStorage.getItem(geminiStorageKey);
        if (apiKeyField && savedKey) {
            apiKeyField.value = savedKey;
        }
        if (geminiApiKeyField && savedGeminiKey) {
            geminiApiKeyField.value = savedGeminiKey;
        }
    }

    function clearApiKeyAndGoHome(button) {
        sessionStorage.removeItem(storageKey);
        sessionStorage.removeItem(geminiStorageKey);
        location.href = button.dataset.homeUrl || "/";
    }

    function initSubmitGuard() {
        document.querySelectorAll("form").forEach((form) => {
            form.addEventListener("submit", function (event) {
                fillHiddenApiKey();
                if (isGenerateForm(form) && !hasHiddenRequiredApiKeys()) {
                    event.preventDefault();
                    form.dataset.submitting = "false";
                    showApiKeyError();
                    return;
                }

                if (form.dataset.submitting === "true") {
                    event.preventDefault();
                    return;
                }

                form.dataset.submitting = "true";
                setTimeout(function () {
                    if (event.defaultPrevented) {
                        form.dataset.submitting = "false";
                        hideLoadingOverlay();
                        return;
                    }

                    showLoadingOverlay(getLoadingMessage(form));
                    form.querySelectorAll("button[type='submit']").forEach((button) => {
                        button.disabled = true;
                        button.dataset.originalText = button.textContent;
                        button.textContent = button.classList.contains("ai-card-danger-button") ? "처리 중..." : "생성 중...";
                    });
                }, 0);
            });
        });
    }

    function isGenerateForm(form) {
        const action = form.getAttribute("action") || "";
        return action.includes("/cards/generate") || action.includes("/cards/drawing/generate");
    }

    function hasHiddenRequiredApiKeys() {
        return Boolean(
            (apiKeyField && apiKeyField.value && apiKeyField.value.trim())
            || (geminiApiKeyField && geminiApiKeyField.value && geminiApiKeyField.value.trim())
        );
    }

    function getLoadingMessage(form) {
        const action = form.getAttribute("action") || "";
        const matched = loadingMessageByAction.find((item) => action.includes(item.keyword));
        return matched ? matched.message : "처리중입니다...";
    }

    function showLoadingOverlay(message) {
        let overlay = document.getElementById("aiLoadingOverlay");
        if (!overlay) {
            overlay = document.createElement("div");
            overlay.id = "aiLoadingOverlay";
            overlay.className = "ai-loading-overlay";
            overlay.innerHTML = `
                <div class="ai-loading-box">
                    <div class="ai-loading-spinner" aria-hidden="true"></div>
                    <strong id="aiLoadingMessage"></strong>
                    <span>잠시만 기다려주세요.</span>
                </div>
            `;
            document.body.appendChild(overlay);
        }

        const messageElement = overlay.querySelector("#aiLoadingMessage");
        if (messageElement) {
            messageElement.textContent = message;
        }
        overlay.classList.add("active");
    }

    function hideLoadingOverlay() {
        const overlay = document.getElementById("aiLoadingOverlay");
        if (overlay) {
            overlay.classList.remove("active");
        }
    }

    if (apiKeyInput) {
        const savedKey = sessionStorage.getItem(storageKey);
        if (savedKey) {
            apiKeyInput.value = savedKey;
        }
        apiKeyInput.addEventListener("input", hideApiKeyErrorIfReady);
    }

    if (geminiApiKeyInput) {
        const savedGeminiKey = sessionStorage.getItem(geminiStorageKey);
        if (savedGeminiKey) {
            geminiApiKeyInput.value = savedGeminiKey;
        }
        geminiApiKeyInput.addEventListener("input", hideApiKeyErrorIfReady);
    }

    if (textCardButton) {
        textCardButton.addEventListener("click", function () {
            moveTo(textCardButton);
        });
    }

    if (drawingCardButton) {
        drawingCardButton.addEventListener("click", function () {
            moveTo(drawingCardButton);
        });
    }

    document.querySelectorAll("[data-main-home]").forEach((button) => {
        button.addEventListener("click", function () {
            clearApiKeyAndGoHome(button);
        });
    });

    fillHiddenApiKey();
    initSubmitGuard();
})();
