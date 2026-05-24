// 1. 휴대폰 자동 하이픈 (이전 코드 재사용)
const phoneInput = document.querySelector("input[name='phone']");
phoneInput.addEventListener("input", function () {
    let val = this.value.replace(/[^0-9]/g, '');
    if (val.length > 3 && val.length <= 7) val = val.replace(/(\d{3})(\d+)/, '$1-$2');
    else if (val.length > 7) val = val.replace(/(\d{3})(\d{4})(\d+)/, '$1-$2-$3');
    this.value = val.substring(0, 13);
});

// 2. 폼 제출 시 간단한 검증
document.getElementById("editForm").addEventListener("submit", function (e) {
    const phone = phoneInput.value;
    const phoneRegex = /^\d{2,3}-\d{3,4}-\d{4}$/;

    if (phone !== "" && !phoneRegex.test(phone)) {
        document.getElementById("error-phone").classList.add("show");
        e.preventDefault();
    }
});

const currentPasswordInput = document.querySelector("input[name='currentPassword']");
currentPasswordInput.addEventListener("input", function () {
    const errorMsg = document.getElementById("error-currentPassword");
    if (errorMsg) errorMsg.classList.remove("show");
    this.classList.remove("input-error");
});