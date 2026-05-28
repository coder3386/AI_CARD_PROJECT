document.addEventListener("DOMContentLoaded", function () {
    const menuItems = document.querySelectorAll(".menu-item");
    const topbarTitleSpan = document.querySelector(".manager-topbar .topbar-title span");

    menuItems.forEach(function (item) {
        item.addEventListener("click", function (e) {
            // 기존 active 타겟 제거
            menuItems.forEach(i => i.classList.remove("active"));

            // 현재 누른 메뉴 활성화
            this.classList.add("active");

            // 상단 타이틀 동적 이름 변경 프로세스
            const menuText = this.querySelector("span").textContent;
            if (topbarTitleSpan) {
                topbarTitleSpan.textContent = `> ${menuText}`;
            }
        });
    });
});