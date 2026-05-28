document.getElementById("joinForm").addEventListener("submit", function (event) {
    let isValid = true;

    // 1. 필수 입력 검사 (체크박스 & 텍스트 구분)
    const requiredInputs = this.querySelectorAll("input[required]");
    requiredInputs.forEach(function (input) {
        const errorMsg = document.getElementById("error-" + input.name);

        // 🟢 입력칸이 체크박스인지 확인
        if (input.type === "checkbox") {
            if (!input.checked) {
                if (errorMsg) {
                    errorMsg.textContent = "필수입력";
                    errorMsg.classList.add("show");
                }
                isValid = false;
            } else {
                if (errorMsg) errorMsg.classList.remove("show");
            }
        }
        // 🟢 체크박스가 아닌 일반 텍스트(text, email, tel 등) 입력칸일 때
        else {
            // 1. 아예 입력하지 않았을 때 (빈칸)
            if (input.value.trim() === "") {
                if (errorMsg) {
                    errorMsg.textContent = "필수입력"; // 기본 에러 메시지
                    errorMsg.classList.add("show");
                }
                input.classList.add("input-error");
                isValid = false;
            }
            // 2. 값이 입력되었을 때
            else {
                // 값이 있는데 만약 그게 '이메일' 타입이라면 양식 검사 진행
                if (input.type === "email") {
                    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

                    // 이메일 양식이 틀렸을 때
                    if (!emailRegex.test(input.value)) {
                        if (errorMsg) {
                            errorMsg.textContent = "양식에 맞게 입력하세요"; // 에러 메시지 문구 변경!
                            errorMsg.classList.add("show");
                        }
                        input.classList.add("input-error");
                        isValid = false;
                    }
                    // 이메일 양식이 맞았을 때
                    else {
                        if (errorMsg) errorMsg.classList.remove("show");
                        input.classList.remove("input-error");
                    }
                }
                // 이메일이 아닌 다른 일반 입력칸이 정상 입력되었을 때
                else {
                    if (errorMsg) errorMsg.classList.remove("show");
                    input.classList.remove("input-error");
                }
            }
        }
    });

    // 2. 전화번호 형식 검사 로직 (기존과 동일)
    const phoneInput = document.querySelector("input[name='phone']");
    const phoneError = document.getElementById("error-phone");

    if (phoneInput.value.trim() !== "") {
        const phoneRegex = /^\d{2,3}-\d{3,4}-\d{4}$/;

        if (!phoneRegex.test(phoneInput.value)) {
            if (phoneError) phoneError.classList.add("show");
            phoneInput.classList.add("input-error");
            isValid = false;
        } else {
            if (phoneError) phoneError.classList.remove("show");
            phoneInput.classList.remove("input-error");
        }
    } else {
        if (phoneError) phoneError.classList.remove("show");
        phoneInput.classList.remove("input-error");
    }

    // 빈칸이 있거나 형식이 틀리거나 체크를 안 했으면 전송 막기
    if (!isValid) {
        event.preventDefault();
    }
});

const agreeTermsCheckbox = document.getElementById("agreeTerms");
if (agreeTermsCheckbox) {
    agreeTermsCheckbox.addEventListener("change", function () {
        if (this.checked) {
            document.getElementById("error-agreeTerms").classList.remove("show");
        }
    });
}

document.getElementById("agreeTerms").addEventListener("change", function () {
    if (this.checked) {
        document.getElementById("error-agreeTerms").classList.remove("show");
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