document.getElementById("joinForm").addEventListener("submit", function (event) {
    let isValid = true;

    // 1. 필수 입력 검사 (기존 로직)
    const requiredInputs = this.querySelectorAll("input[required]");
    requiredInputs.forEach(function (input) {
        const errorMsg = document.getElementById("error-" + input.name);
        if (input.value.trim() === "") {
            if (errorMsg) errorMsg.classList.add("show");
            input.classList.add("input-error");
            isValid = false;
        } else {
            if (errorMsg) errorMsg.classList.remove("show");
            input.classList.remove("input-error");
        }
    });

    // 2. 🟢 새로 추가: 전화번호 형식 검사 로직
    const phoneInput = document.querySelector("input[name='phone']");
    const phoneError = document.getElementById("error-phone");

    // 휴대폰 번호를 입력했을 때만 검사합니다 (선택 사항이므로)
    if (phoneInput.value.trim() !== "") {
        // 정규표현식: 숫자 2~3자리 - 숫자 3~4자리 - 숫자 4자리
        const phoneRegex = /^\d{2,3}-\d{3,4}-\d{4}$/;

        // 입력값이 정규표현식 패턴과 맞지 않는다면?
        if (!phoneRegex.test(phoneInput.value)) {
            if (phoneError) phoneError.classList.add("show"); // 에러 메시지 표시
            phoneInput.classList.add("input-error"); // 테두리 빨갛게
            isValid = false; // 폼 전송 보류!
        } else {
            if (phoneError) phoneError.classList.remove("show");
            phoneInput.classList.remove("input-error");
        }
    } else {
        // 비워뒀을 때는 에러를 없애줍니다 (선택 사항이니까 정상 통과)
        if (phoneError) phoneError.classList.remove("show");
        phoneInput.classList.remove("input-error");
    }

    // 빈칸이 있거나 형식이 틀리면 서버로 넘어가지 못하게 막기
    if (!isValid) {
        event.preventDefault();
    }
});

// 사용자가 다시 입력하기 시작하면 즉시 빨간줄 지워주기 (필수입력 & 전화번호 모두 적용)
const allInputs = document.querySelectorAll("#joinForm input");
allInputs.forEach(function (input) {
    input.addEventListener("input", function () {
        const errorMsg = document.getElementById("error-" + this.name);
        if (errorMsg) errorMsg.classList.remove("show");
        this.classList.remove("input-error");
    });
});

// 🌟 휴대폰 번호 입력 시 자동으로 하이픈(-) 추가해주는 마법의 코드
const phoneInput = document.querySelector("input[name='phone']");
phoneInput.addEventListener("input", function (e) {
    // 숫자만 남기고 다 지운 뒤
    let val = this.value.replace(/[^0-9]/g, '');

    // 길이에 맞춰서 하이픈(-)을 다시 끼워 넣습니다.
    if (val.length > 3 && val.length <= 7) {
        val = val.replace(/(\d{3})(\d+)/, '$1-$2');
    } else if (val.length > 7) {
        // 010-1234-5678 형식
        val = val.replace(/(\d{3})(\d{4})(\d+)/, '$1-$2-$3');
    }

    // 최대 13자리(000-0000-0000)까지만 입력 가능하도록 자르기
    this.value = val.substring(0, 13);
});