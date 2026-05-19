document.getElementById("profileBtn").addEventListener("click", function (event) {
    document.getElementById("profileDropdown").classList.toggle("show");
    // 이벤트가 위로 전파되는 것 방지
    event.stopPropagation();
});

// 드롭다운 박스 바깥 영역을 클릭하면 창 닫기 (구글처럼)
window.addEventListener("click", function (event) {
    var dropdown = document.getElementById("profileDropdown");
    var button = document.getElementById("profileBtn");

    // 클릭한 곳이 버튼도 아니고 드롭다운 창 내부도 아니라면 닫기
    if (dropdown.classList.contains('show') && !button.contains(event.target) && !dropdown.contains(event.target)) {
        dropdown.classList.remove('show');
    }
});