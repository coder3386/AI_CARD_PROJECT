(function () {
    const canvasWidth = 860;
    const canvasHeight = 480;
    const minShapeWidth = 60;
    const minShapeHeight = 36;
    const roleLabels = {
        profileImage: "프로필",
        nameText: "이름",
        jobText: "직무",
        introText: "소개",
        emailText: "이메일",
        phoneText: "전화",
        portfolioArea: "포트폴리오",
        linkArea: "링크",
        decoration: "장식"
    };
    const personalRoleFields = [
        { role: "nameText", label: "이름", selector: "[name='displayName']" },
        { role: "jobText", label: "직무 / 직책", selector: "[name='jobTitle']" },
        { role: "introText", label: "자기소개", selector: "[name='intro']" },
        { role: "emailText", label: "이메일", selector: "[name='email']" },
        { role: "phoneText", label: "전화번호", selector: "[name='phone']" }
    ];
    const extraRoles = ["portfolioArea", "linkArea"];
    const palette = [
        { border: "#0f766e", background: "rgba(20, 184, 166, 0.16)" },
        { border: "#2563eb", background: "rgba(96, 165, 250, 0.16)" },
        { border: "#7c3aed", background: "rgba(167, 139, 250, 0.17)" },
        { border: "#db2777", background: "rgba(244, 114, 182, 0.17)" },
        { border: "#ea580c", background: "rgba(251, 146, 60, 0.18)" },
        { border: "#4d7c0f", background: "rgba(132, 204, 22, 0.18)" }
    ];

    const canvas = document.getElementById("drawingCanvas");
    const addShapeButton = document.getElementById("addDrawingShapeButton");
    const layoutInput = document.getElementById("drawingLayoutJson");
    const roleSelect = document.getElementById("shapeRoleSelect");
    const clearButton = document.getElementById("clearDrawingCanvasButton");
    const chipsContainer = document.getElementById("drawingShapeChips");
    const validationMessage = document.getElementById("drawingValidationMessage");
    const extraList = document.getElementById("drawingExtraList");
    const form = layoutInput ? layoutInput.closest("form") : null;
    let selectedShape = null;
    let shapeSequence = 0;

    function initDrawingEditor() {
        if (!canvas) {
            return;
        }

        if (addShapeButton) {
            addShapeButton.addEventListener("click", addShape);
        }

        if (roleSelect) {
            roleSelect.addEventListener("change", function () {
                if (selectedShape) {
                    selectedShape.dataset.role = roleSelect.value;
                    updateShapeRoleBadge(selectedShape);
                    syncExtraFields();
                    updateShapeSummary();
                    setDrawingLayoutToHiddenInput();
                    validatePlacedPersonalFields(false);
                }
            });
        }

        if (clearButton) {
            clearButton.addEventListener("click", clearDrawingCanvas);
        }

        if (form) {
            form.addEventListener("input", function () {
                validatePlacedPersonalFields(false);
            });
            form.addEventListener("submit", function (event) {
                setDrawingLayoutToHiddenInput();
                if (!validatePlacedPersonalFields(true)) {
                    event.preventDefault();
                }
            });
        }

        canvas.addEventListener("click", function () {
            selectShape(null);
        });

        updateShapeSummary();
        syncExtraFields();
        setDrawingLayoutToHiddenInput();
    }

    function addShape() {
        const shape = document.createElement("div");
        const label = document.createElement("span");
        const roleBadge = document.createElement("span");
        const deleteButton = document.createElement("button");
        const color = palette[shapeSequence % palette.length];

        shapeSequence += 1;
        shape.className = "drawing-shape rectangle";
        shape.dataset.shapeId = `shape_${shapeSequence}`;
        shape.dataset.type = "rectangle";
        shape.dataset.role = "";
        shape.style.left = `${80 + shapeSequence * 18}px`;
        shape.style.top = `${70 + shapeSequence * 14}px`;
        shape.style.width = "160px";
        shape.style.height = "96px";
        shape.style.borderColor = color.border;
        shape.style.background = color.background;
        shape.style.setProperty("--shape-accent", color.border);

        label.className = "drawing-shape-label";
        label.contentEditable = "true";
        label.spellcheck = false;
        label.textContent = "영역";

        roleBadge.className = "drawing-shape-role";

        deleteButton.className = "drawing-shape-delete";
        deleteButton.type = "button";
        deleteButton.textContent = "x";
        deleteButton.setAttribute("aria-label", "도형 삭제");

        shape.appendChild(roleBadge);
        shape.appendChild(label);
        shape.appendChild(deleteButton);
        appendResizeHandles(shape);

        shape.addEventListener("pointerdown", startDrag);
        shape.addEventListener("click", function (event) {
            event.stopPropagation();
            selectShape(shape);
        });
        label.addEventListener("input", function () {
            updateShapeRoleBadge(shape);
            syncExtraFields();
            updateShapeSummary();
            setDrawingLayoutToHiddenInput();
        });
        label.addEventListener("pointerdown", function (event) {
            event.stopPropagation();
        });
        deleteButton.addEventListener("click", function (event) {
            event.stopPropagation();
            deleteShape(shape);
        });
        deleteButton.addEventListener("pointerdown", function (event) {
            event.stopPropagation();
        });

        canvas.appendChild(shape);
        updateShapeRoleBadge(shape);
        selectShape(shape);
        syncExtraFields();
        updateShapeSummary();
        setDrawingLayoutToHiddenInput();
    }

    function appendResizeHandles(shape) {
        ["n", "e", "s", "w", "ne", "nw", "se", "sw"].forEach((direction) => {
            const handle = document.createElement("span");
            handle.className = `drawing-resize-handle ${direction}`;
            handle.dataset.resizeDirection = direction;
            handle.addEventListener("pointerdown", startResize);
            shape.appendChild(handle);
        });
    }

    function startDrag(event) {
        if (
            event.target.closest(".drawing-shape-delete") ||
            event.target.closest(".drawing-shape-label") ||
            event.target.closest(".drawing-resize-handle")
        ) {
            return;
        }

        const shape = event.currentTarget;
        selectShape(shape);

        const startX = event.clientX;
        const startY = event.clientY;
        const startLeft = parseFloat(shape.style.left || "0");
        const startTop = parseFloat(shape.style.top || "0");

        function move(moveEvent) {
            const nextLeft = clamp(startLeft + moveEvent.clientX - startX, 0, canvasWidth - shape.offsetWidth);
            const nextTop = clamp(startTop + moveEvent.clientY - startY, 0, canvasHeight - shape.offsetHeight);
            shape.style.left = `${nextLeft}px`;
            shape.style.top = `${nextTop}px`;
            setDrawingLayoutToHiddenInput();
        }

        function stop() {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", stop);
            updateShapeSummary();
        }

        window.addEventListener("pointermove", move);
        window.addEventListener("pointerup", stop);
    }

    function startResize(event) {
        event.preventDefault();
        event.stopPropagation();

        const handle = event.currentTarget;
        const shape = handle.closest(".drawing-shape");
        const direction = handle.dataset.resizeDirection || "";
        selectShape(shape);

        const startX = event.clientX;
        const startY = event.clientY;
        const startLeft = parseFloat(shape.style.left || "0");
        const startTop = parseFloat(shape.style.top || "0");
        const startWidth = shape.offsetWidth;
        const startHeight = shape.offsetHeight;

        function move(moveEvent) {
            const deltaX = moveEvent.clientX - startX;
            const deltaY = moveEvent.clientY - startY;
            let nextLeft = startLeft;
            let nextTop = startTop;
            let nextWidth = startWidth;
            let nextHeight = startHeight;

            if (direction.includes("e")) {
                nextWidth = startWidth + deltaX;
            }
            if (direction.includes("s")) {
                nextHeight = startHeight + deltaY;
            }
            if (direction.includes("w")) {
                nextWidth = startWidth - deltaX;
                nextLeft = startLeft + deltaX;
            }
            if (direction.includes("n")) {
                nextHeight = startHeight - deltaY;
                nextTop = startTop + deltaY;
            }

            if (nextWidth < minShapeWidth) {
                if (direction.includes("w")) {
                    nextLeft -= minShapeWidth - nextWidth;
                }
                nextWidth = minShapeWidth;
            }
            if (nextHeight < minShapeHeight) {
                if (direction.includes("n")) {
                    nextTop -= minShapeHeight - nextHeight;
                }
                nextHeight = minShapeHeight;
            }

            if (nextLeft < 0) {
                nextWidth += nextLeft;
                nextLeft = 0;
            }
            if (nextTop < 0) {
                nextHeight += nextTop;
                nextTop = 0;
            }
            if (nextLeft + nextWidth > canvasWidth) {
                nextWidth = canvasWidth - nextLeft;
            }
            if (nextTop + nextHeight > canvasHeight) {
                nextHeight = canvasHeight - nextTop;
            }

            shape.style.left = `${nextLeft}px`;
            shape.style.top = `${nextTop}px`;
            shape.style.width = `${nextWidth}px`;
            shape.style.height = `${nextHeight}px`;
            setDrawingLayoutToHiddenInput();
        }

        function stop() {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", stop);
            updateShapeSummary();
        }

        window.addEventListener("pointermove", move);
        window.addEventListener("pointerup", stop);
    }

    function selectShape(shape) {
        if (selectedShape) {
            selectedShape.classList.remove("selected");
        }

        selectedShape = shape;

        if (selectedShape) {
            selectedShape.classList.add("selected");
        }

        if (roleSelect) {
            roleSelect.value = selectedShape ? selectedShape.dataset.role || "" : "";
        }
    }

    function deleteShape(shape) {
        if (selectedShape === shape) {
            selectedShape = null;
            if (roleSelect) {
                roleSelect.value = "";
            }
        }
        shape.remove();
        syncExtraFields();
        updateShapeSummary();
        setDrawingLayoutToHiddenInput();
        validatePlacedPersonalFields(false);
    }

    function updateShapeRoleBadge(shape) {
        const badge = shape.querySelector(".drawing-shape-role");
        const role = getShapeRole(shape);
        if (badge) {
            badge.textContent = roleLabels[role] || role;
        }
    }

    function updateShapeSummary() {
        if (!chipsContainer || !canvas) {
            return;
        }

        const shapes = Array.from(canvas.querySelectorAll(".drawing-shape"));
        chipsContainer.innerHTML = "";

        if (shapes.length === 0) {
            const empty = document.createElement("span");
            empty.className = "drawing-empty-chip";
            empty.textContent = "아직 추가한 도형이 없습니다.";
            chipsContainer.appendChild(empty);
            return;
        }

        shapes.forEach((shape, index) => {
            const chip = document.createElement("button");
            const role = getShapeRole(shape);
            const label = getShapeText(shape) || `${index + 1}번 도형`;
            chip.type = "button";
            chip.className = "drawing-shape-chip";
            chip.textContent = `${index + 1}. ${roleLabels[role] || role} - ${label}`;
            chip.addEventListener("click", function () {
                selectShape(shape);
                shape.scrollIntoView({ block: "nearest", inline: "nearest" });
            });
            chipsContainer.appendChild(chip);
        });
    }

    function syncExtraFields() {
        if (!extraList || !canvas) {
            return;
        }

        const existingValues = collectExtraValues();
        const extraShapes = Array.from(canvas.querySelectorAll(".drawing-shape"))
            .filter((shape) => extraRoles.includes(getShapeRole(shape)));

        extraList.innerHTML = "";

        if (extraShapes.length === 0) {
            const empty = document.createElement("p");
            empty.className = "drawing-extra-empty";
            empty.textContent = "아직 추가 정보 영역이 없습니다.";
            extraList.appendChild(empty);
            return;
        }

        extraShapes.forEach((shape, index) => {
            const role = getShapeRole(shape);
            const shapeId = shape.dataset.shapeId;
            const previous = existingValues[shapeId] || {};
            const type = role === "linkArea" ? "LINK" : "PORTFOLIO";
            const title = previous.title || "";

            extraList.appendChild(buildExtraItemFieldset(index, shapeId, type, title, previous));
        });
    }

    function buildExtraItemFieldset(index, shapeId, itemType, title, previous) {
        const fieldset = document.createElement("div");
        fieldset.className = "drawing-extra-item";
        fieldset.dataset.shapeId = shapeId;

        fieldset.innerHTML = `
            <input type="hidden" name="extraItems[${index}].itemType" value="${escapeAttr(itemType)}">
            <div class="drawing-extra-item-title">
                <strong>${itemType === "LINK" ? "링크 정보" : "포트폴리오 정보"}</strong>
                <span>${shapeId}</span>
            </div>
            <label>
                제목
                <input type="text" name="extraItems[${index}].title" value="${escapeAttr(title)}" placeholder="예: GitHub, 자격증, 프로젝트">
            </label>
            <label>
                링크 URL
                <input type="url" name="extraItems[${index}].url" value="${escapeAttr(previous.url || "")}" placeholder="https://example.com">
            </label>
        `;

        return fieldset;
    }

    function collectExtraValues() {
        const values = {};
        if (!extraList) {
            return values;
        }

        extraList.querySelectorAll(".drawing-extra-item").forEach((item) => {
            const shapeId = item.dataset.shapeId;
            values[shapeId] = {
                title: getFieldValue(item, "[name$='.title']"),
                url: getFieldValue(item, "[name$='.url']")
            };
        });

        return values;
    }

    function validatePlacedPersonalFields(showMessage) {
        if (!canvas) {
            return true;
        }

        const placedRoles = new Set(Array.from(canvas.querySelectorAll(".drawing-shape")).map(getShapeRole));
        const missing = personalRoleFields
            .filter((field) => {
                const input = form ? form.querySelector(field.selector) : null;
                return input && input.value.trim() && !placedRoles.has(field.role);
            })
            .map((field) => field.label);

        if (validationMessage) {
            if (missing.length > 0 && showMessage) {
                validationMessage.textContent = `아래에 입력한 정보 중 배치판에 없는 칸이 있습니다: ${missing.join(", ")}. 위 배치판에 해당 역할 도형을 추가해주세요.`;
                validationMessage.classList.add("active");
            } else {
                validationMessage.textContent = "";
                validationMessage.classList.remove("active");
            }
        }

        return missing.length === 0;
    }

    function extractDrawingLayout() {
        if (!canvas) {
            return {
                canvasWidth,
                canvasHeight,
                elements: []
            };
        }

        const elements = Array.from(canvas.querySelectorAll(".drawing-shape")).map((shape) => {
            const rect = shape.getBoundingClientRect();
            const canvasRect = canvas.getBoundingClientRect();
            const text = getShapeText(shape);

            return {
                role: getShapeRole(shape),
                type: shape.dataset.type || "rectangle",
                x: Math.round(rect.left - canvasRect.left),
                y: Math.round(rect.top - canvasRect.top),
                width: Math.round(rect.width),
                height: Math.round(rect.height),
                text
            };
        });

        return {
            canvasWidth,
            canvasHeight,
            elements
        };
    }

    function inferRoleFromShape(shape) {
        const text = (shape.text || "").toLowerCase();
        if (text.includes("프로필") || text.includes("사진") || text.includes("profile")) return "profileImage";
        if (text.includes("이름") || text.includes("name")) return "nameText";
        if (text.includes("직무") || text.includes("직책") || text.includes("job")) return "jobText";
        if (text.includes("소개") || text.includes("intro")) return "introText";
        if (text.includes("메일") || text.includes("email")) return "emailText";
        if (text.includes("전화") || text.includes("phone")) return "phoneText";
        if (text.includes("포트폴리오") || text.includes("기술") || text.includes("portfolio") || text.includes("skill")) return "portfolioArea";
        if (text.includes("링크") || text.includes("github") || text.includes("blog") || text.includes("link")) return "linkArea";
        return "decoration";
    }

    function setDrawingLayoutToHiddenInput() {
        if (!layoutInput) {
            return;
        }
        layoutInput.value = JSON.stringify(extractDrawingLayout());
    }

    function clearDrawingCanvas() {
        if (!canvas) {
            return;
        }
        canvas.querySelectorAll(".drawing-shape").forEach((shape) => shape.remove());
        selectShape(null);
        syncExtraFields();
        updateShapeSummary();
        setDrawingLayoutToHiddenInput();
        validatePlacedPersonalFields(false);
    }

    function getShapeText(shape) {
        const label = shape.querySelector(".drawing-shape-label");
        return label ? label.textContent.trim() : "";
    }

    function getShapeRole(shape) {
        return shape.dataset.role || inferRoleFromShape({ text: getShapeText(shape) });
    }

    function getFieldValue(root, selector) {
        const field = root.querySelector(selector);
        return field ? field.value : "";
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");
    }

    function escapeAttr(value) {
        return escapeHtml(value)
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function clamp(value, min, max) {
        return Math.max(min, Math.min(max, value));
    }

    window.initDrawingEditor = initDrawingEditor;
    window.extractDrawingLayout = extractDrawingLayout;
    window.inferRoleFromShape = inferRoleFromShape;
    window.setDrawingLayoutToHiddenInput = setDrawingLayoutToHiddenInput;
    window.clearDrawingCanvas = clearDrawingCanvas;

    initDrawingEditor();
})();
